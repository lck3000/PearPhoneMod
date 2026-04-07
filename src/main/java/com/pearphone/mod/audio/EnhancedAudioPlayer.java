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
    /** Seek offset applied at the next play() call (seconds). */
    private volatile double seekOffsetSeconds = 0;

    /** Fallback paths: the player/feeder process. */
    private volatile Process mainProcess;
    private volatile Process ytdlpProcess;

    /**
     * Preload state: yt-dlp and ffmpeg started early so their pipe buffers fill
     * while the previous track plays.  When play() is called, tryJavaPcmStream()
     * consumes these instead of spawning new processes, eliminating the URL-
     * resolution delay.
     */
    private volatile Process preloadedYtdlp;
    private volatile Process preloadedFfmpeg;
    private final AtomicBoolean preloaded     = new AtomicBoolean(false);
    private final AtomicBoolean preloadFailed = new AtomicBoolean(false);

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
     * Start the yt-dlp → ffmpeg pipeline in the background without opening an audio
     * line.  Their stdout/stdin pipe buffers fill while another track plays.  When
     * {@link #play()} is subsequently called, {@link #tryJavaPcmStream()} detects the
     * ready processes and skips URL resolution entirely, eliminating startup latency.
     *
     * <p>Safe to call from any thread.  Idempotent — a second call is a no-op.
     * If yt-dlp or ffmpeg are unavailable the flag {@code preloadFailed} is set so
     * {@link #tryJavaPcmStream()} falls back to a cold start.
     */
    public void preload() {
        if (preloaded.get() || preloadFailed.get()) return;
        EXECUTOR.submit(() -> {
            try {
                if (cancelled.get()) return;
                if (!AudioPlayerConfig.COMMON.enableYoutubeSupport.get()) return;
                if (!extractor.isAvailable()) return;

                Process ytdlp = extractor.startStreamProcess(youtubeUrl);
                if (ytdlp == null) { preloadFailed.set(true); return; }
                if (cancelled.get()) { killProcess(ytdlp); return; }

                Process ffmpeg;
                try {
                    ffmpeg = new ProcessBuilder(
                            "ffmpeg", "-hide_banner", "-loglevel", "error",
                            "-i", "pipe:0",
                            "-f", "s16le", "-ar", String.valueOf(SAMPLE_RATE), "-ac", String.valueOf(CHANNELS),
                            "pipe:1").start();
                } catch (IOException e) {
                    // ffmpeg unavailable — preload only works on the Java PCM path
                    killProcess(ytdlp);
                    preloadFailed.set(true);
                    LOGGER.debug("[Preload] ffmpeg not found, skipping preload for {}", youtubeUrl);
                    return;
                }
                if (cancelled.get()) { killProcess(ffmpeg); killProcess(ytdlp); return; }

                // Pipe yt-dlp → ffmpeg and drain stderr; both run as daemon threads.
                final Process ytRef = ytdlp, ffRef = ffmpeg;
                Thread feeder = new Thread(() -> {
                    try (OutputStream ffIn = ffRef.getOutputStream()) {
                        ytRef.getInputStream().transferTo(ffIn);
                    } catch (Exception ignored) {}
                }, "AudioPreloadFeeder");
                feeder.setDaemon(true);
                feeder.start();

                new Thread(() -> {
                    try { ffRef.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
                    catch (Exception ignored) {}
                }, "AudioPreloadErrDrain").start();

                this.preloadedYtdlp = ytdlp;
                this.preloadedFfmpeg = ffmpeg;
                preloaded.set(true);

                // Handle the race where stop() was called while we were setting up.
                if (cancelled.get()) {
                    killProcess(preloadedFfmpeg);
                    killProcess(preloadedYtdlp);
                    preloadedFfmpeg = null;
                    preloadedYtdlp  = null;
                    preloaded.set(false);
                } else {
                    LOGGER.info("[Preload] Ready: {}", youtubeUrl);
                }
            } catch (Exception e) {
                preloadFailed.set(true);
                LOGGER.warn("[Preload] Failed for {}: {}", youtubeUrl, e.getMessage());
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
        killProcess(this.ytdlpProcess);
        this.mainProcess  = null;
        this.ytdlpProcess = null;

        // Also tear down any in-flight preload.
        killProcess(this.preloadedFfmpeg);
        killProcess(this.preloadedYtdlp);
        this.preloadedFfmpeg = null;
        this.preloadedYtdlp  = null;
        preloaded.set(false);

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
     * Used when creating a new player that should start mid-track.
     */
    public void setSeekOffset(double seconds) {
        this.seekOffsetSeconds = Math.max(0, seconds);
    }

    /**
     * Seek to {@code seconds} into the track. Stops and restarts the yt-dlp/ffmpeg
     * pipeline with {@code --download-sections} so playback resumes at the new
     * position. Only accurate for the Java PCM path; other paths restart from the
     * given offset but do not update the position indicator.
     */
    public void seekTo(double seconds) {
        this.seekOffsetSeconds = Math.max(0, seconds);

        // Tear down current playback (same as stop, but we restart immediately)
        cancelled.set(true);
        paused.set(false);
        SourceDataLine line = this.audioLine;
        if (line != null) {
            try { line.close(); } catch (Exception ignored) {}
            this.audioLine = null;
        }
        killProcess(this.mainProcess);
        killProcess(this.ytdlpProcess);
        this.mainProcess  = null;
        this.ytdlpProcess = null;
        // Invalidate preloaded processes — they started from offset 0, not the seek point
        killProcess(this.preloadedFfmpeg);
        killProcess(this.preloadedYtdlp);
        this.preloadedFfmpeg = null;
        this.preloadedYtdlp  = null;
        preloaded.set(false);

        // Restart from the new position
        cancelled.set(false);
        bytesWritten.set((long)(seekOffsetSeconds * SAMPLE_RATE * BYTES_PER_FRAME));
        EXECUTOR.submit(this::doPlay);
        LOGGER.info("Seeking to {}s for: {}", (int) seconds, youtubeUrl);
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
        return paused.get() && !cancelled.get();
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
     *
     * <p>If {@link #preload()} completed successfully the already-running yt-dlp and
     * ffmpeg processes are reused, skipping the YouTube URL-resolution delay entirely.
     */
    private boolean tryJavaPcmStream() {
        Process ytdlp  = null;
        Process ffmpeg = null;
        SourceDataLine line = null;

        // Reuse preloaded processes if they are alive; otherwise cold-start.
        boolean fromPreload = preloaded.get()
                && preloadedFfmpeg != null && preloadedFfmpeg.isAlive()
                && preloadedYtdlp  != null && preloadedYtdlp.isAlive();

        try {
            if (fromPreload) {
                ytdlp  = this.preloadedYtdlp;
                ffmpeg = this.preloadedFfmpeg;
                this.ytdlpProcess = ytdlp;
                this.mainProcess  = ffmpeg;
                LOGGER.info("[Audio] Consuming preloaded stream for: {}", youtubeUrl);
            } else {
                // ── Cold start ────────────────────────────────────────────────
                ytdlp = extractor.startStreamProcess(youtubeUrl, seekOffsetSeconds);
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

                final Process ytRef = ytdlp, ffRef = ffmpeg;
                Thread feeder = new Thread(() -> {
                    try (OutputStream ffIn = ffRef.getOutputStream()) {
                        ytRef.getInputStream().transferTo(ffIn);
                    } catch (Exception ignored) {}
                }, "AudioFeeder");
                feeder.setDaemon(true);
                feeder.start();

                new Thread(() -> {
                    try { ffRef.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
                    catch (Exception ignored) {}
                }, "AudioErrDrain").start();
            }

            // ── Open audio line ───────────────────────────────────────────────
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.warn("Java SourceDataLine not supported; trying ffplay.");
                cleanupProcesses(fromPreload, ffmpeg, ytdlp);
                return false;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 65536);
            applyGain(line, baseVolume);
            this.audioLine = line;
            line.start();

            setStatus("Playing");
            LOGGER.info("Streaming via {}yt-dlp → ffmpeg → Java audio",
                    fromPreload ? "[preloaded] " : "");

            // ── Read loop ─────────────────────────────────────────────────────
            InputStream pcm = ffmpeg.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while (!cancelled.get()) {
                // While paused: hold here without reading more PCM so the line buffer
                // stays at its current fill level and bytesWritten does not advance.
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
            cleanupProcesses(fromPreload, ffmpeg, ytdlp);
            return false;
        } catch (Exception e) {
            if (!cancelled.get()) LOGGER.error("Java PCM stream error: {}", e.getMessage(), e);
            closeLine(line);
            this.audioLine = null;
            cleanupProcesses(fromPreload, ffmpeg, ytdlp);
            return false;
        }
    }

    /**
     * Kill processes only when they were cold-started by this method.
     * Preloaded processes are owned by the preload system and cleaned up via {@link #stop()}.
     */
    private void cleanupProcesses(boolean fromPreload, Process ffmpeg, Process ytdlp) {
        if (!fromPreload) {
            killProcess(ffmpeg);
            killProcess(ytdlp);
        }
    }

    private boolean tryPipedFfplay(int ffVol) {
        Process ytdlp = null;
        Process ffplay = null;
        try {
            ytdlp = extractor.startStreamProcess(youtubeUrl, seekOffsetSeconds);
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
