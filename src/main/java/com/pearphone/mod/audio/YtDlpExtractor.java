package com.pearphone.mod.audio;

import com.pearphone.mod.config.AudioPlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Locates and wraps yt-dlp.
 *
 * Detection order:
 *   1. yt-dlp          (standalone binary on PATH)
 *   2. python -m yt_dlp  (pip user-install, Python on PATH)
 *   3. py -m yt_dlp    (Windows py launcher)
 *   4. python3 -m yt_dlp
 */
public class YtDlpExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");

    /** Candidate command prefixes to try, in preference order. */
    private static final String[][] CANDIDATES = {
        {"yt-dlp"},
        {"python",  "-m", "yt_dlp"},
        {"py",      "-m", "yt_dlp"},
        {"python3", "-m", "yt_dlp"},
    };

    private static YtDlpExtractor INSTANCE = null;

    /**
     * LRU cache of resolved CDN audio URLs keyed by YouTube URL.
     * Avoids re-running yt-dlp for recently played or preloaded tracks.
     */
    private static final Map<String, String> URL_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > urlCacheMaxSize();
            }
        }
    );

    private static int urlCacheMaxSize() {
        try { return AudioPlayerConfig.COMMON.streamUrlCacheSize.get(); }
        catch (Exception e) { return 10; }
    }

    private boolean isAvailable = false;
    /** The working command prefix, e.g. ["yt-dlp"] or ["python", "-m", "yt_dlp"]. */
    private List<String> cmdPrefix = null;

    private YtDlpExtractor() {
        detect();
    }

    public static YtDlpExtractor getInstance() {
        if (INSTANCE == null) INSTANCE = new YtDlpExtractor();
        return INSTANCE;
    }

    /** Re-run detection (e.g. after the background download has completed). */
    public void refresh() {
        this.isAvailable = false;
        this.cmdPrefix = null;
        detect();
    }

    public boolean isAvailable() {
        return this.isAvailable;
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    private void detect() {
        // ── 1. Managed binary (auto-downloaded by YtDlpManager) ──────────────
        Path managed = YtDlpManager.getManagedBinary();
        if (managed != null) {
            String[] prefix = {managed.toAbsolutePath().toString()};
            if (probe(prefix)) {
                this.cmdPrefix = new ArrayList<>(Arrays.asList(prefix));
                this.isAvailable = true;
                LOGGER.info("yt-dlp ready via managed binary: {}", managed);
                return;
            }
        }

        // ── 2. Fall back to system PATH ───────────────────────────────────────
        for (String[] prefix : CANDIDATES) {
            if (probe(prefix)) {
                this.cmdPrefix = new ArrayList<>(Arrays.asList(prefix));
                this.isAvailable = true;
                LOGGER.info("yt-dlp found on system PATH via: {}", String.join(" ", prefix));
                return;
            }
        }
        LOGGER.warn("yt-dlp unavailable (managed download failed and not found on PATH). " +
                    "Check network connectivity or install manually: pip install yt-dlp");
    }

    private boolean probe(String[] prefix) {
        try {
            List<String> cmd = new ArrayList<>(Arrays.asList(prefix));
            cmd.add("--version");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            boolean ok = p.waitFor(10, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Stream URL extraction (two-step: get CDN URL, then play separately) ──

    /**
     * Run yt-dlp -g to get a direct CDN URL.
     * Prefer the piped-stream approach in EnhancedAudioPlayer; this is a fallback.
     */
    public String extractAudioStreamUrl(String youtubeUrl, String format) {
        if (!this.isAvailable) {
            LOGGER.error("yt-dlp unavailable — cannot extract URL");
            return null;
        }
        try {
            List<String> cmd = new ArrayList<>(this.cmdPrefix);
            cmd.addAll(Arrays.asList("-f", format, "-g", "--no-playlist", youtubeUrl));

            LOGGER.info("Extracting URL: {}", String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Drain stderr in background
            Thread errDrain = new Thread(() -> {
                try { process.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            });
            errDrain.setDaemon(true);
            errDrain.start();

            // Read first http URL from stdout
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String url = null;
            String line;
            while ((line = stdout.readLine()) != null) {
                if (line.trim().startsWith("http")) { url = line.trim(); break; }
            }
            stdout.lines().forEach(__ -> {}); // drain rest

            boolean done = process.waitFor(getTimeout(), TimeUnit.SECONDS);
            int exit = done ? process.exitValue() : -1;
            if (exit == 0 && url != null) {
                LOGGER.info("CDN URL extracted (format={})", format);
                return url;
            }
            LOGGER.warn("yt-dlp exited {} with no URL for format={}", exit, format);
            return null;

        } catch (Exception e) {
            LOGGER.error("extractAudioStreamUrl error: {}", e.getMessage(), e);
            return null;
        }
    }

    public String extractAudioStreamUrl(String youtubeUrl) {
        // Try opus (best quality), then m4a (broader compat)
        String url = extractAudioStreamUrl(youtubeUrl, "bestaudio[ext=webm]/251/250");
        if (url == null) url = extractAudioStreamUrl(youtubeUrl, "140/bestaudio[ext=m4a]");
        return url;
    }

    public String extractM4aStreamUrl(String youtubeUrl) {
        return extractAudioStreamUrl(youtubeUrl, "140/139/bestaudio[ext=m4a]");
    }

    /**
     * Extract the best audio CDN URL, using the LRU cache when possible.
     * Thread-safe. Cache size is controlled by {@code streamUrlCacheSize} in config.
     */
    public String extractStreamUrlCached(String youtubeUrl) {
        String cached = URL_CACHE.get(youtubeUrl);
        if (cached != null) {
            LOGGER.debug("[URLCache] Hit for: {}", youtubeUrl);
            return cached;
        }
        String url = extractAudioStreamUrl(youtubeUrl);
        if (url != null) {
            URL_CACHE.put(youtubeUrl, url);
            LOGGER.info("[URLCache] Cached URL ({} entries)", URL_CACHE.size());
        }
        return url;
    }

    /** Remove a cached URL (call when playback fails so the next attempt re-extracts). */
    public void invalidateCachedUrl(String youtubeUrl) {
        URL_CACHE.remove(youtubeUrl);
    }

    // ── Streaming process (yt-dlp pipes audio bytes directly) ────────────────

    /**
     * Start a yt-dlp process that writes raw audio bytes to its stdout.
     * Connect its stdout to an ffplay stdin for instant streaming playback.
     *
     * @return the running Process, or null on failure
     */
    public Process startStreamProcess(String youtubeUrl) {
        return startStreamProcess(youtubeUrl, 0);
    }

    /**
     * Start a yt-dlp process beginning at {@code seekSeconds} into the video.
     * When {@code seekSeconds > 0}, adds {@code --download-sections *HH:MM:SS-inf}
     * so yt-dlp skips to that timestamp before piping data.
     *
     * @return the running Process, or null on failure
     */
    public Process startStreamProcess(String youtubeUrl, double seekSeconds) {
        if (!this.isAvailable) {
            LOGGER.error("yt-dlp unavailable — cannot start stream");
            return null;
        }
        try {
            List<String> cmd = new ArrayList<>(this.cmdPrefix);
            cmd.addAll(Arrays.asList(
                // 251 = WebM/Opus, 140 = M4A/AAC — both stream progressively without
                // remuxing, which eliminates the buffer-before-output delay.
                "-f",  "251/140/bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio",
                "-o",  "-",           // pipe raw audio bytes to stdout
                "-q",                 // suppress progress/warnings
                "--no-playlist",
                "--no-part"           // no temp .part files (belt-and-suspenders)
            ));
            if (seekSeconds > 0.5) {
                int total = (int) seekSeconds;
                String ts = String.format("*%02d:%02d:%02d-inf", total / 3600, (total % 3600) / 60, total % 60);
                cmd.addAll(Arrays.asList("--download-sections", ts, "--force-keyframes-at-cuts"));
                LOGGER.info("Starting yt-dlp stream (seek={}s): {}", total, String.join(" ", cmd));
            } else {
                LOGGER.info("Starting yt-dlp stream: {}", String.join(" ", cmd));
            }
            cmd.add(youtubeUrl);
            return new ProcessBuilder(cmd).start();
        } catch (Exception e) {
            LOGGER.error("Failed to start yt-dlp stream: {}", e.getMessage(), e);
            return null;
        }
    }

    // ── Metadata fetching ────────────────────────────────────────────────────

    /**
     * Fetch title and duration for a URL synchronously.
     * Call from a background thread only — blocks until yt-dlp responds.
     *
     * @return {@link TrackMetadata} on success, or {@code null} on failure.
     */
    public TrackMetadata fetchMetadata(String youtubeUrl) {
        if (!this.isAvailable) return null;
        try {
            List<String> cmd = new ArrayList<>(this.cmdPrefix);
            cmd.addAll(Arrays.asList(
                "--dump-json", "--no-playlist",
                "--no-warnings", "--quiet",
                youtubeUrl
            ));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            Thread errDrain = new Thread(() -> {
                try { proc.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            });
            errDrain.setDaemon(true);
            errDrain.start();

            String json = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            proc.waitFor(getTimeout(), TimeUnit.SECONDS);

            if (json.isEmpty()) return null;

            // yt-dlp may return multiple JSON lines for playlists; use the first.
            int newline = json.indexOf('\n');
            if (newline > 0) json = json.substring(0, newline).trim();

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String title = obj.has("title") && !obj.get("title").isJsonNull()
                    ? obj.get("title").getAsString() : youtubeUrl;
            int duration = obj.has("duration") && !obj.get("duration").isJsonNull()
                    ? (int) obj.get("duration").getAsDouble() : -1;
            return new TrackMetadata(title, duration);

        } catch (Exception e) {
            LOGGER.error("fetchMetadata error for {}: {}", youtubeUrl, e.getMessage());
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int getTimeout() {
        try { return AudioPlayerConfig.SERVER.audioStreamTimeout.get(); }
        catch (Exception e) { return 60; }
    }
}
