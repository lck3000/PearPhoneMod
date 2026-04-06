package com.pearphone.mod.audio;

import com.pearphone.mod.config.AudioPlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streams YouTube audio through three paths (tried in order):
 *
 *  1. yt-dlp → ffmpeg (raw PCM) → Java SourceDataLine  [real-time volume, pause/resume, position]
 *  2. yt-dlp | ffplay                                   [volume set at launch only]
 *  3. CDN URL → PowerShell WMF  (Windows, no ffmpeg)   [volume set at launch only]
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
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS    = 2;
    private static final int BYTES_PER_FRAME = 2 * CHANNELS; // 16-bit stereo

    private final String youtubeUrl;
    private volatile int baseVolume;
    private volatile Runnable onComplete;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean paused    = new AtomicBoolean(false);

    /** Primary path: Java audio line. Non-null only while the Java PCM path is active. */
    private volatile SourceDataLine audioLine;
    /** Bytes delivered to the audio line — used to calculate playback position. */
    private final AtomicLong bytesWritten = new AtomicLong(0);

    /** Fallback paths: the player/feeder process. */
    private volatile Process mainProcess;
    private volatile Process ytdlpProcess;

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
        bytesWritten.set(0);
        EXECUTOR.submit(this::doPlay);
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
        killProcess(this.ytdlpProcess);
        this.mainProcess   = null;
        this.ytdlpProcess  = null;
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
     * Callback invoked on the audio thread when a track finishes naturally
     * (not when stopped externally). Used by {@link PlaylistManager} for auto-advance.
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
        return paused.get() && !cancelled.get() && this.audioLine != null;
    }

    /** Elapsed playback time in seconds (Java audio path only; 0 for fallback paths). */
    public double getPositionSeconds() {
        return bytesWritten.get() / (double)(SAMPLE_RATE * BYTES_PER_FRAME);
    }

    public int    getVolume()           { return this.baseVolume; }
    public String getStatusMessage()    { return this.statusMessage; }
    public void   shutdown()            { stop(); }

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

            // ── Path 1: yt-dlp → ffmpeg → Java SourceDataLine ────────────────
            setStatus("Connecting to stream...");
            if (tryJavaPcmStream()) return;
            if (cancelled.get()) return;

            int ffVol = Math.max(0, Math.min(128, (int)(baseVolume * 1.28)));

            // ── Path 2: yt-dlp | ffplay ───────────────────────────────────────
            if (tryPipedFfplay(ffVol)) return;
            if (cancelled.get()) return;

            // ── Path 3: CDN URL → ffplay ──────────────────────────────────────
            setStatus("Extracting stream URL...");
            String cdnUrl = extractor.extractAudioStreamUrl(youtubeUrl);
            if (cdnUrl != null) {
                if (cancelled.get()) return;
                if (tryFfplayUrl(cdnUrl, ffVol)) return;
            }
            if (cancelled.get()) return;

            // ── Path 4: PowerShell WMF (Windows last resort) ─────────────────
            if (IS_WINDOWS) {
                setStatus("Trying PowerShell/WMF...");
                String m4aUrl = extractor.extractM4aStreamUrl(youtubeUrl);
                if (m4aUrl == null && cdnUrl != null) m4aUrl = cdnUrl;
                if (m4aUrl != null) {
                    if (cancelled.get()) return;
                    if (tryPowerShell(m4aUrl)) return;
                }
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
     * yt-dlp → ffmpeg (s16le PCM) → Java SourceDataLine.
     * Supports real-time volume (FloatControl), pause/resume, and position tracking.
     */
    private boolean tryJavaPcmStream() {
        Process ytdlp = null;
        Process ffmpeg = null;
        SourceDataLine line = null;
        try {
            ytdlp = extractor.startStreamProcess(youtubeUrl);
            if (ytdlp == null) return false;
            this.ytdlpProcess = ytdlp;
            if (cancelled.get()) { killProcess(ytdlp); return false; }

            ProcessBuilder ffpb = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-i", "pipe:0",
                    "-f", "s16le", "-ar", String.valueOf(SAMPLE_RATE), "-ac", String.valueOf(CHANNELS),
                    "pipe:1");
            ffmpeg = ffpb.start();
            this.mainProcess = ffmpeg;
            if (cancelled.get()) { killProcess(ffmpeg); killProcess(ytdlp); return false; }

            final Process ytRef = ytdlp;
            final Process ffRef = ffmpeg;

            Thread feeder = new Thread(() -> {
                try (OutputStream ffIn = ffRef.getOutputStream()) {
                    ytRef.getInputStream().transferTo(ffIn);
                } catch (Exception ignored) {}
            }, "AudioFeeder");
            feeder.setDaemon(true);
            feeder.start();

            Thread errDrain = new Thread(() -> {
                try { ffRef.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            }, "AudioErrDrain");
            errDrain.setDaemon(true);
            errDrain.start();

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.warn("Java SourceDataLine not supported; trying ffplay.");
                killProcess(ffmpeg); killProcess(ytdlp);
                return false;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 65536);
            applyGain(line, baseVolume);
            this.audioLine = line;
            line.start();

            setStatus("Playing");
            LOGGER.info("Streaming via yt-dlp → ffmpeg → Java audio");

            InputStream pcm = ffmpeg.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while (!cancelled.get()) {
                n = pcm.read(buf);
                if (n == -1) break;
                try {
                    line.write(buf, 0, n);  // blocks when paused (line stopped + buffer full)
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
            closeLine(line); this.audioLine = null;
            killProcess(ffmpeg); killProcess(ytdlp);
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("Java PCM stream error: {}", e.getMessage(), e);
            closeLine(line); this.audioLine = null;
            killProcess(ffmpeg); killProcess(ytdlp);
            return false;
        }
    }

    private boolean tryPipedFfplay(int ffVol) {
        Process ytdlp = null;
        Process ffplay = null;
        try {
            ytdlp = extractor.startStreamProcess(youtubeUrl);
            if (ytdlp == null) return false;
            this.ytdlpProcess = ytdlp;
            if (cancelled.get()) { killProcess(ytdlp); return false; }

            ProcessBuilder ffpb = new ProcessBuilder(
                    "ffplay", "-nodisp", "-autoexit",
                    "-loglevel", "error", "-volume", String.valueOf(ffVol), "-");
            ffpb.redirectErrorStream(true);
            ffplay = ffpb.start();
            this.mainProcess = ffplay;
            if (cancelled.get()) { killProcess(ffplay); killProcess(ytdlp); return false; }

            final Process ytRef = ytdlp, fpRef = ffplay;
            Thread pipe = new Thread(() -> {
                try (OutputStream out = fpRef.getOutputStream()) {
                    ytRef.getInputStream().transferTo(out);
                } catch (Exception ignored) {
                } finally {
                    try { fpRef.getOutputStream().close(); } catch (Exception ignored) {}
                }
            }, "FfplayPipe");
            pipe.setDaemon(true); pipe.start();
            new Thread(() -> {
                try { fpRef.getInputStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            }, "FfplayDrain").start();

            setStatus("Playing");
            LOGGER.info("Streaming via yt-dlp | ffplay");
            ffplay.waitFor();
            if (!cancelled.get()) { setStatus("Idle"); fireOnComplete(); }
            return true;

        } catch (IOException e) {
            LOGGER.warn("ffplay piped stream failed ({}); trying URL fallback.", e.getMessage());
            killProcess(ffplay); killProcess(ytdlp);
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("ffplay piped error: {}", e.getMessage(), e);
            killProcess(ffplay); killProcess(ytdlp);
            return false;
        }
    }

    private boolean tryFfplayUrl(String url, int ffVol) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffplay", "-nodisp", "-autoexit",
                    "-loglevel", "quiet", "-volume", String.valueOf(ffVol), url);
            pb.redirectErrorStream(true);
            Process ffplay = pb.start();
            this.mainProcess = ffplay;
            if (cancelled.get()) { killProcess(ffplay); return false; }

            setStatus("Playing (URL mode)");
            ffplay.getInputStream().transferTo(OutputStream.nullOutputStream());
            ffplay.waitFor();
            if (!cancelled.get()) { setStatus("Idle"); fireOnComplete(); }
            return true;
        } catch (IOException e) {
            LOGGER.warn("ffplay URL fallback failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("ffplay URL error: {}", e.getMessage(), e);
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
