package com.pearphone.mod.audio;

import com.pearphone.mod.config.AudioPlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streams YouTube audio through three paths (tried in order):
 *
 *  1. ffmpeg (HTTP CDN stream) → Java SourceDataLine  [real-time volume, pause/resume, position]
 *  2. ffplay (HTTP CDN stream)                         [volume set at launch only]
 *  3. PowerShell WMF          (Windows, no ffmpeg)     [volume set at launch only]
 *
 * <p>yt-dlp is used <em>only</em> to resolve the CDN stream URL via {@code -g}.
 * ffmpeg/ffplay then stream directly from that URL, enabling fast seeking with
 * {@code -ss} without re-running yt-dlp. The resolved URL is cached across seeks
 * and reused from {@link YtDlpExtractor}'s LRU cache on subsequent plays.
 */
public class EnhancedAudioPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "AudioPlayer");
        t.setDaemon(true);
        return t;
    });
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    // PCM format used for the Java audio path
    private static final int SAMPLE_RATE      = 44100;
    private static final int CHANNELS         = 2;
    private static final int BYTES_PER_FRAME  = 2 * CHANNELS; // 16-bit stereo

    private final String youtubeUrl;
    private volatile int baseVolume;
    private volatile Runnable onComplete;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean paused    = new AtomicBoolean(false);

    /** Primary path: Java audio line. Non-null only while the Java PCM path is active. */
    private volatile SourceDataLine audioLine;
    /** Bytes delivered to the audio line — used to calculate playback position. */
    private final AtomicLong bytesWritten = new AtomicLong(0);
    /** Seek offset applied at the next play() call (seconds). */
    private volatile double seekOffsetSeconds = 0;

    /** The active ffmpeg or ffplay process. */
    private volatile Process mainProcess;

    /**
     * Resolved CDN audio URL (from yt-dlp -g).
     * Set by {@link #preload()} or at the start of the first {@link #doPlay()} call.
     * Kept across seeks so yt-dlp is never re-run for the same track instance.
     */
    private volatile String resolvedCdnUrl;

    private volatile String statusMessage = "Idle";

    private final YtDlpExtractor extractor;

    public EnhancedAudioPlayer(String youtubeUrl, int volume) {
        this.youtubeUrl = youtubeUrl;
        this.baseVolume = Math.max(0, Math.min(100, volume));
        this.extractor  = YtDlpExtractor.getInstance();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void play() {
        cancelled.set(false);
        paused.set(false);
        bytesWritten.set((long)(seekOffsetSeconds * SAMPLE_RATE * BYTES_PER_FRAME));
        EXECUTOR.submit(this::doPlay);
    }

    /**
     * Resolve the CDN stream URL in the background so {@link #play()} can start
     * immediately without waiting for yt-dlp.  Idempotent — a second call is a no-op.
     * Uses {@link YtDlpExtractor#extractStreamUrlCached} so the URL may come from
     * the global LRU cache without any yt-dlp invocation at all.
     */
    public void preload() {
        if (resolvedCdnUrl != null) return;
        EXECUTOR.submit(() -> {
            if (cancelled.get()) return;
            if (!AudioPlayerConfig.COMMON.enableYoutubeSupport.get()) return;
            if (!extractor.isAvailable()) return;
            String url = extractor.extractStreamUrlCached(youtubeUrl);
            if (url != null && !cancelled.get()) {
                resolvedCdnUrl = url;
                LOGGER.info("[Preload] URL resolved: {}", youtubeUrl);
            }
        });
    }

    public void stop() {
        cancelled.set(true);
        paused.set(false);

        SourceDataLine line = this.audioLine;
        if (line != null) {
            try { line.close(); } catch (Exception ignored) {}
            this.audioLine = null;
        }
        killProcess(this.mainProcess);
        this.mainProcess = null;

        this.statusMessage = "Idle";
        LOGGER.info("Playback stopped");
    }

    /**
     * Pause playback. Only effective on the Java audio path.
     * For ffplay/PowerShell paths this is a no-op.
     */
    public void pause() {
        paused.set(true);
        SourceDataLine line = this.audioLine;
        if (line != null && line.isRunning()) {
            line.stop(); // halts output immediately, retains buffer
        }
    }

    /** Resume after {@link #pause()}. */
    public void resume() {
        paused.set(false);
        SourceDataLine line = this.audioLine;
        if (line != null && line.isOpen() && !line.isRunning()) {
            line.start();
        }
    }

    /**
     * Live volume adjustment. For the Java audio path this takes effect immediately
     * via {@link FloatControl}. For fallback paths it is stored for the next play.
     */
    public void setVolume(int volume) {
        this.baseVolume = Math.max(0, Math.min(100, volume));
        SourceDataLine line = this.audioLine;
        if (line != null && line.isOpen()) {
            applyGain(line, this.baseVolume);
        }
    }

    /**
     * Set the seek offset for the NEXT {@link #play()} call without restarting.
     */
    public void setSeekOffset(double seconds) {
        this.seekOffsetSeconds = Math.max(0, seconds);
    }

    /**
     * Seek to {@code seconds} into the track.
     * Stops and restarts ffmpeg with {@code -ss} — yt-dlp is NOT re-run because
     * {@link #resolvedCdnUrl} is already cached from the initial play or preload.
     */
    public void seekTo(double seconds) {
        this.seekOffsetSeconds = Math.max(0, seconds);

        cancelled.set(true);
        paused.set(false);
        SourceDataLine line = this.audioLine;
        if (line != null) {
            try { line.close(); } catch (Exception ignored) {}
            this.audioLine = null;
        }
        killProcess(this.mainProcess);
        this.mainProcess = null;

        cancelled.set(false);
        bytesWritten.set((long)(seekOffsetSeconds * SAMPLE_RATE * BYTES_PER_FRAME));
        EXECUTOR.submit(this::doPlay);
        LOGGER.info("Seeking to {}s for: {}", (int) seconds, youtubeUrl);
    }

    /**
     * Callback invoked on the audio thread when a track finishes naturally.
     */
    public void setOnComplete(Runnable cb) { this.onComplete = cb; }

    public boolean isPlaying() {
        if (cancelled.get() || paused.get()) return false;
        SourceDataLine line = this.audioLine;
        if (line != null) return line.isOpen() && line.isRunning();
        Process p = this.mainProcess;
        return p != null && p.isAlive();
    }

    public boolean isPaused() {
        return paused.get() && !cancelled.get();
    }

    /** Elapsed playback time in seconds (Java audio path only; 0 for fallback paths). */
    public double getPositionSeconds() {
        return bytesWritten.get() / (double)(SAMPLE_RATE * BYTES_PER_FRAME);
    }

    public int    getVolume()        { return this.baseVolume; }
    public String getStatusMessage() { return this.statusMessage; }
    public void   shutdown()         { stop(); }

    // ── Playback logic ────────────────────────────────────────────────────────

    private void doPlay() {
        try {
            if (cancelled.get()) return;

            if (!AudioPlayerConfig.COMMON.enableYoutubeSupport.get()) {
                setStatus("Error: YouTube support disabled in config");
                return;
            }
            if (!extractor.isAvailable()) {
                setStatus("Retrying yt-dlp detection...");
                extractor.refresh();
            }
            if (!extractor.isAvailable()) {
                setStatus("Error: yt-dlp unavailable");
                return;
            }
            if (cancelled.get()) return;

            // Resolve CDN URL — from preload cache, global URL cache, or fresh yt-dlp call
            String cdnUrl = resolvedCdnUrl;
            if (cdnUrl == null) {
                setStatus("Resolving stream URL...");
                cdnUrl = extractor.extractStreamUrlCached(youtubeUrl);
                if (cdnUrl == null) {
                    setStatus("Error: could not resolve stream URL");
                    return;
                }
                resolvedCdnUrl = cdnUrl;
                LOGGER.info("[Audio] URL resolved (cold start): {}", youtubeUrl);
            } else {
                LOGGER.info("[Audio] Using cached URL for: {}", youtubeUrl);
            }
            if (cancelled.get()) return;

            int ffVol = Math.max(0, Math.min(128, (int)(baseVolume * 1.28)));

            // ── Path 1: ffmpeg → Java SourceDataLine ─────────────────────────
            setStatus("Connecting to stream...");
            if (tryJavaPcmStream(cdnUrl)) return;
            if (cancelled.get()) return;

            // ── Path 2: ffplay ────────────────────────────────────────────────
            if (tryFfplayUrl(cdnUrl, ffVol)) return;
            if (cancelled.get()) return;

            // ── Path 3: PowerShell WMF (Windows last resort) ─────────────────
            if (IS_WINDOWS) {
                setStatus("Resolving M4A URL for PowerShell...");
                String m4aUrl = extractor.extractM4aStreamUrl(youtubeUrl);
                if (m4aUrl == null) m4aUrl = cdnUrl;
                if (!cancelled.get() && tryPowerShell(m4aUrl)) return;
            }

            if (!cancelled.get()) {
                setStatus("Error: no audio backend found. Install ffmpeg and add to PATH.");
                LOGGER.error("No working audio backend.");
            }

        } catch (Exception e) {
            if (!cancelled.get()) {
                LOGGER.error("Playback error: {}", e.getMessage(), e);
                setStatus("Error: " + e.getMessage());
            }
        }
    }

    // ── Path implementations ──────────────────────────────────────────────────

    /**
     * Starts ffmpeg pointing directly at the CDN URL (no yt-dlp subprocess).
     * Passing {@code -ss} before {@code -i} enables fast input seeking.
     */
    private boolean tryJavaPcmStream(String cdnUrl) {
        Process ffmpeg = null;
        SourceDataLine line = null;
        try {
            List<String> cmd = new ArrayList<>(Arrays.asList("ffmpeg", "-hide_banner", "-loglevel", "error"));
            if (seekOffsetSeconds > 0.5) {
                cmd.addAll(Arrays.asList("-ss", String.format("%.3f", seekOffsetSeconds)));
            }
            cmd.addAll(Arrays.asList(
                "-i", cdnUrl,
                "-f", "s16le", "-ar", String.valueOf(SAMPLE_RATE), "-ac", String.valueOf(CHANNELS),
                "pipe:1"
            ));

            ffmpeg = new ProcessBuilder(cmd).start();
            this.mainProcess = ffmpeg;
            if (cancelled.get()) { killProcess(ffmpeg); return false; }

            // Drain ffmpeg stderr without blocking
            final Process ffRef = ffmpeg;
            new Thread(() -> {
                try { ffRef.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            }, "AudioErrDrain").start();

            // Open audio line
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.warn("Java SourceDataLine not supported; trying ffplay.");
                killProcess(ffmpeg);
                return false;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 65536);
            applyGain(line, baseVolume);
            this.audioLine = line;
            line.start();

            setStatus("Playing");
            LOGGER.info("Streaming via ffmpeg → Java audio (seek={}s)", (int) seekOffsetSeconds);

            // Read loop
            InputStream pcm = ffmpeg.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while (!cancelled.get()) {
                while (paused.get() && !cancelled.get()) {
                    try { Thread.sleep(20); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (cancelled.get()) break;
                n = pcm.read(buf);
                if (n == -1) break;
                try {
                    line.write(buf, 0, n);
                    bytesWritten.addAndGet(n);
                } catch (Exception e) {
                    break; // line closed by stop()
                }
            }

            if (!cancelled.get() && line.isOpen()) line.drain();
            closeLine(line);
            this.audioLine = null;

            if (!cancelled.get()) {
                setStatus("Idle");
                fireOnComplete();
            }
            return true;

        } catch (IOException e) {
            LOGGER.warn("ffmpeg not available ({}); trying ffplay.", e.getMessage());
            closeLine(line);
            this.audioLine = null;
            killProcess(ffmpeg);
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("Java PCM stream error: {}", e.getMessage(), e);
            closeLine(line);
            this.audioLine = null;
            killProcess(ffmpeg);
            return false;
        }
    }

    private boolean tryFfplayUrl(String url, int ffVol) {
        try {
            List<String> cmd = new ArrayList<>(Arrays.asList(
                "ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet",
                "-volume", String.valueOf(ffVol)
            ));
            if (seekOffsetSeconds > 0.5) {
                cmd.addAll(Arrays.asList("-ss", String.format("%.3f", seekOffsetSeconds)));
            }
            cmd.add(url);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process ffplay = pb.start();
            this.mainProcess = ffplay;
            if (cancelled.get()) { killProcess(ffplay); return false; }

            setStatus("Playing (ffplay)");
            LOGGER.info("Streaming via ffplay (seek={}s)", (int) seekOffsetSeconds);
            ffplay.getInputStream().transferTo(OutputStream.nullOutputStream());
            ffplay.waitFor();
            if (!cancelled.get()) { setStatus("Idle"); fireOnComplete(); }
            return true;
        } catch (IOException e) {
            LOGGER.warn("ffplay not available: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("ffplay error: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean tryPowerShell(String url) {
        try {
            String safeUrl = url.replace("'", "''");
            double vol = baseVolume / 100.0;
            String script =
                "Add-Type -AssemblyName PresentationCore; " +
                "$mp = New-Object System.Windows.Media.MediaPlayer; " +
                "$mp.Open([Uri]'" + safeUrl + "'); " +
                "$mp.Volume = " + String.format("%.4f", vol) + "; " +
                "$mp.Play(); Start-Sleep 3; " +
                "while ($mp.IsBuffering) { Start-Sleep 1 }; " +
                "$deadline = (Get-Date).AddHours(3); " +
                "while ((Get-Date) -lt $deadline) { " +
                "  try { $d = $mp.NaturalDuration.TimeSpan; " +
                "    if ($mp.Position -ge $d) { break } } catch {}; " +
                "  Start-Sleep 1 }; " +
                "$mp.Stop(); $mp.Close()";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-STA", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            Process ps = pb.start();
            this.mainProcess = ps;
            if (cancelled.get()) { killProcess(ps); return false; }

            setStatus("Playing (PowerShell/WMF)");
            ps.getInputStream().transferTo(OutputStream.nullOutputStream());
            ps.waitFor();
            if (!cancelled.get()) { setStatus("Idle"); fireOnComplete(); }
            return true;
        } catch (IOException e) {
            LOGGER.warn("PowerShell fallback unavailable: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("PowerShell error: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void fireOnComplete() {
        Runnable cb = onComplete;
        if (cb != null) {
            try { cb.run(); } catch (Exception e) { LOGGER.error("onComplete error: {}", e.getMessage()); }
        }
    }

    private static void applyGain(SourceDataLine line, int volumePercent) {
        try {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = volumePercent <= 0
                    ? gain.getMinimum()
                    : 20f * (float) Math.log10(volumePercent / 100.0);
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
        } catch (Exception ignored) {}
    }

    private static void closeLine(SourceDataLine line) {
        if (line != null) try { line.close(); } catch (Exception ignored) {}
    }

    private static void killProcess(Process p) {
        if (p != null && p.isAlive()) p.destroyForcibly();
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        LOGGER.info("[AudioPlayer] {}", msg);
    }
}
