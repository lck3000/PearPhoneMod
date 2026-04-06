package com.pearphone.mod.audio;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages a local yt-dlp standalone binary, downloading it from GitHub releases on first use.
 *
 * Call {@link #ensureAvailableAsync()} at mod load time to start the download in the background.
 * Later calls to {@link #getManagedBinary()} will wait (up to 2 minutes) for the download to
 * complete before returning, so the caller never has to manage the async state manually.
 *
 * Binary location: {@code <gamedir>/pearphone/bin/yt-dlp[.exe|_macos]}
 */
public class YtDlpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");

    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    static final boolean IS_WINDOWS = OS.contains("win");
    static final boolean IS_MAC     = OS.contains("mac");

    private static final String DOWNLOAD_BASE =
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";

    /** Cached path once the binary is confirmed ready. */
    private static volatile Path cachedPath = null;
    /** The single in-flight (or completed) download future. */
    private static volatile CompletableFuture<Path> downloadFuture = null;
    /** Guards {@link #downloadFuture} initialisation so it is only created once. */
    private static final Object START_LOCK = new Object();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Kicks off a background download of yt-dlp if it is not already present.
     * Safe to call multiple times and from any thread — only one download ever runs.
     * Should be called as early as possible (e.g. during {@code FMLCommonSetupEvent}).
     */
    public static void ensureAvailableAsync() {
        synchronized (START_LOCK) {
            if (downloadFuture != null) return; // already started or completed

            Path existing = findOnDisk();
            if (existing != null) {
                LOGGER.info("[PortableAudio] yt-dlp already present at: {}", existing);
                cachedPath = existing;
                downloadFuture = CompletableFuture.completedFuture(existing);
                return;
            }

            LOGGER.info("[PortableAudio] yt-dlp not found — downloading standalone binary in background...");
            downloadFuture = CompletableFuture.supplyAsync(YtDlpManager::doDownload);
        }
    }

    /**
     * Returns the path to the managed yt-dlp binary, waiting up to 120 s for any
     * in-progress download to complete.  Returns {@code null} if the binary cannot be
     * obtained (download failed or timed out).
     */
    public static Path getManagedBinary() {
        // Fast path: already ready
        if (cachedPath != null && Files.exists(cachedPath)) return cachedPath;

        // Ensure a download is in flight (in case ensureAvailableAsync wasn't called yet)
        ensureAvailableAsync();

        // Wait for the future to resolve
        try {
            Path result = downloadFuture.get(120, TimeUnit.SECONDS);
            if (result != null) cachedPath = result;
            return result;
        } catch (TimeoutException e) {
            LOGGER.error("[PortableAudio] yt-dlp download timed out after 120 s");
            return null;
        } catch (Exception e) {
            LOGGER.error("[PortableAudio] yt-dlp download error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Forces a full reset so the next call to {@link #ensureAvailableAsync()} or
     * {@link #getManagedBinary()} triggers a fresh download attempt.
     * Only call this when you know the cached binary is unusable (e.g. corrupted).
     */
    public static synchronized void forceReset() {
        cachedPath = null;
        downloadFuture = null;
    }

    // ── Download logic ────────────────────────────────────────────────────────

    /** Runs on a ForkJoinPool thread — blocks until download succeeds or fails. */
    private static Path doDownload() {
        Path binDir = getBinDir();
        if (binDir == null) return null;

        Path dest = binDir.resolve(binaryName());
        String rawUrl = DOWNLOAD_BASE + binaryName();

        try {
            URL resolved = resolveRedirects(rawUrl, 8);
            if (resolved == null) {
                LOGGER.error("[PortableAudio] Could not resolve download URL (too many redirects): {}", rawUrl);
                return null;
            }

            HttpURLConnection conn = openConn(resolved);
            int status = conn.getResponseCode();
            if (status != 200) {
                conn.disconnect();
                LOGGER.error("[PortableAudio] Download returned HTTP {}: {}", status, resolved);
                return null;
            }

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            conn.disconnect();

            long size = Files.size(dest);
            if (size < 1_000_000L) {
                LOGGER.error("[PortableAudio] Downloaded file suspiciously small ({} bytes) — may be an error page", size);
                Files.deleteIfExists(dest);
                return null;
            }

            ensureExecutable(dest);
            LOGGER.info("[PortableAudio] yt-dlp downloaded successfully ({} MB) to: {}",
                    size / (1024 * 1024), dest);
            return dest;

        } catch (Exception e) {
            LOGGER.error("[PortableAudio] yt-dlp download failed: {}", e.getMessage(), e);
            try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Path findOnDisk() {
        try {
            Path binDir = getBinDir();
            if (binDir == null) return null;
            Path bin = binDir.resolve(binaryName());
            if (Files.exists(bin) && Files.size(bin) > 1_000_000L) {
                ensureExecutable(bin);
                return bin;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Path getBinDir() {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("pearphone").resolve("bin");
            Files.createDirectories(dir);
            return dir;
        } catch (Exception e) {
            LOGGER.error("[PortableAudio] Cannot create pearphone/bin directory: {}", e.getMessage());
            return null;
        }
    }

    static String binaryName() {
        if (IS_WINDOWS) return "yt-dlp.exe";
        if (IS_MAC)     return "yt-dlp_macos";
        return "yt-dlp";
    }

    /** Follows HTTP redirects manually — GitHub releases always redirect to a CDN URL. */
    private static URL resolveRedirects(String startUrl, int maxHops) throws Exception {
        String url = startUrl;
        for (int i = 0; i < maxHops; i++) {
            HttpURLConnection conn = openConn(new URL(url));
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            conn.disconnect();
            if (code == 200) return new URL(url);
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc == null || loc.isEmpty()) return null;
                url = loc;
                continue;
            }
            LOGGER.error("[PortableAudio] Unexpected HTTP {} while resolving: {}", code, url);
            return null;
        }
        return null;
    }

    private static HttpURLConnection openConn(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(300_000);  // yt-dlp binary is ~15 MB; allow slow connections
        conn.setRequestProperty("User-Agent", "PearPhoneMod/1.0 (Minecraft mod)");
        return conn;
    }

    private static void ensureExecutable(Path bin) {
        if (IS_WINDOWS) return;
        if (!bin.toFile().setExecutable(true, false)) {
            LOGGER.warn("[PortableAudio] Could not set executable bit on {}", bin);
        }
    }
}
