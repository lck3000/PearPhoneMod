package com.pearphone.mod.audio;

import com.pearphone.mod.config.AudioPlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages a playlist of {@link TrackInfo} objects and owns the active {@link EnhancedAudioPlayer}.
 *
 * Thread-safety:
 *  - {@code tracks} is a CopyOnWriteArrayList — safe for concurrent reads from the GUI thread.
 *  - {@code currentIndex}, {@code player}, and {@code volume} are volatile.
 *  - All mutating methods may be called from any thread.
 */
public class PlaylistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");

    private final CopyOnWriteArrayList<TrackInfo> tracks = new CopyOnWriteArrayList<>();
    private volatile int currentIndex = -1;
    private volatile EnhancedAudioPlayer player;
    private volatile int volume = 100;

    /**
     * Pre-started players keyed by playlist index.  Each entry has already launched
     * yt-dlp + ffmpeg so their pipe buffers fill while the current track plays,
     * eliminating the URL-resolution delay on the next track transition.
     */
    private final ConcurrentHashMap<Integer, EnhancedAudioPlayer> preloadedPlayers =
            new ConcurrentHashMap<>();

    /** Fetches metadata for newly added tracks without blocking the caller. */
    private final ExecutorService metadataPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "MetadataFetch");
        t.setDaemon(true);
        return t;
    });

    // ── Playlist management ───────────────────────────────────────────────────

    /**
     * Add a URL to the end of the playlist and immediately start fetching its
     * title and duration in the background.
     */
    public void addTrack(String url) {
        if (url == null || url.isBlank()) return;
        TrackInfo info = new TrackInfo(url);
        tracks.add(info);
        metadataPool.submit(() -> fetchMetadata(info));
        // The new track might fall within the preload window of the current track.
        if (currentIndex >= 0) schedulePreloads(currentIndex);
    }

    public void removeTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        // Cancel all preloads; they will be rescheduled with correct indices after removal.
        cancelAllPreloads();
        if (index == currentIndex) {
            stopPlayer();
            currentIndex = -1;
        } else if (index < currentIndex) {
            currentIndex--;
        }
        tracks.remove(index);
        if (currentIndex >= 0) schedulePreloads(currentIndex);
    }

    public void clearAll() {
        cancelAllPreloads();
        stopPlayer();
        currentIndex = -1;
        tracks.clear();
    }

    // ── Playback control ──────────────────────────────────────────────────────

    /** Play track at {@code index}. Stops and replaces any currently playing track. */
    public void play(int index) {
        if (index < 0 || index >= tracks.size()) return;
        stopPlayer();
        currentIndex = index;

        // Reuse a preloaded player if one exists for this index — its yt-dlp + ffmpeg
        // processes are already running so the audio line opens with no URL-resolution delay.
        EnhancedAudioPlayer p = preloadedPlayers.remove(index);
        if (p != null) {
            p.setVolume(volume); // volume may have changed since preload was started
            LOGGER.info("Playlist: playing index {} from preload — {}", index, tracks.get(index).getTitle());
        } else {
            p = new EnhancedAudioPlayer(tracks.get(index).getUrl(), volume);
            LOGGER.info("Playlist: playing index {} (cold start) — {}", index, tracks.get(index).getTitle());
        }
        p.setOnComplete(this::onTrackComplete);
        player = p;
        p.play();

        schedulePreloads(index);
    }

    /** Start or resume. If paused → resume. If stopped → play current (or first) track. */
    public void playOrResume() {
        EnhancedAudioPlayer p = player;
        if (p != null && p.isPaused()) {
            p.resume();
        } else if (p == null || (!p.isPlaying() && !p.isPaused())) {
            int idx = currentIndex >= 0 ? currentIndex : (tracks.isEmpty() ? -1 : 0);
            if (idx >= 0) play(idx);
        }
    }

    public void pause() {
        EnhancedAudioPlayer p = player;
        if (p != null) p.pause();
    }

    public void stop() {
        stopPlayer();
    }

    public void next() {
        if (currentIndex < tracks.size() - 1) play(currentIndex + 1);
    }

    public void prev() {
        if (currentIndex > 0) play(currentIndex - 1);
    }

    public void setVolume(int v) {
        volume = Math.max(0, Math.min(100, v));
        EnhancedAudioPlayer p = player;
        if (p != null) p.setVolume(volume);
    }

    // ── State queries (read from GUI thread) ──────────────────────────────────

    public List<TrackInfo> getTracks()   { return new ArrayList<>(tracks); }
    public int getCurrentIndex()         { return currentIndex; }
    public int getVolume()               { return volume; }

    public TrackInfo getCurrentTrack() {
        int idx = currentIndex;
        return (idx >= 0 && idx < tracks.size()) ? tracks.get(idx) : null;
    }

    public boolean isPlaying() {
        EnhancedAudioPlayer p = player;
        return p != null && p.isPlaying();
    }

    public boolean isPaused() {
        EnhancedAudioPlayer p = player;
        return p != null && p.isPaused();
    }

    public String getStatusMessage() {
        EnhancedAudioPlayer p = player;
        return p != null ? p.getStatusMessage() : "Idle";
    }

    /** Position in the current track, in seconds. 0 if unavailable. */
    public double getPositionSeconds() {
        EnhancedAudioPlayer p = player;
        return p != null ? p.getPositionSeconds() : 0;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void shutdown() {
        cancelAllPreloads();
        stopPlayer();
        metadataPool.shutdownNow();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void stopPlayer() {
        EnhancedAudioPlayer p = player;
        if (p != null) {
            p.stop();
            player = null;
        }
    }

    /** Called on the audio background thread when a track finishes naturally. */
    private void onTrackComplete() {
        int next = currentIndex + 1;
        if (next < tracks.size()) {
            play(next);
        } else {
            currentIndex = -1;
            player = null;
            cancelAllPreloads();
        }
    }

    // ── Preload management ────────────────────────────────────────────────────

    /**
     * Ensure the next {@code preloadCount} tracks after {@code fromIndex} have
     * a pre-started {@link EnhancedAudioPlayer}.  Cancels any preloads that are
     * now outside the window.
     */
    private void schedulePreloads(int fromIndex) {
        int count = preloadCount();
        if (count == 0) return;

        // Cancel preloads that fell outside the window.
        preloadedPlayers.entrySet().removeIf(entry -> {
            int idx = entry.getKey();
            boolean outOfWindow = idx <= fromIndex || idx > fromIndex + count;
            if (outOfWindow) entry.getValue().stop();
            return outOfWindow;
        });

        // Start preloads for tracks in the window that aren't already preloading.
        for (int i = 1; i <= count; i++) {
            int idx = fromIndex + i;
            if (idx < tracks.size() && !preloadedPlayers.containsKey(idx)) {
                EnhancedAudioPlayer p = new EnhancedAudioPlayer(tracks.get(idx).getUrl(), volume);
                preloadedPlayers.put(idx, p);
                p.preload();
                LOGGER.info("[Preload] Scheduled index {} — {}", idx, tracks.get(idx).getTitle());
            }
        }
    }

    private void cancelAllPreloads() {
        preloadedPlayers.values().forEach(EnhancedAudioPlayer::stop);
        preloadedPlayers.clear();
    }

    private static int preloadCount() {
        try { return AudioPlayerConfig.COMMON.preloadCount.get(); }
        catch (Exception e) { return 2; }
    }

    private void fetchMetadata(TrackInfo info) {
        try {
            YtDlpExtractor ext = YtDlpExtractor.getInstance();
            if (!ext.isAvailable()) {
                info.setStatus(TrackInfo.Status.ERROR);
                return;
            }
            TrackMetadata meta = ext.fetchMetadata(info.getUrl());
            if (meta != null) {
                info.setTitle(meta.title());
                info.setDurationSeconds(meta.durationSeconds());
                info.setStatus(TrackInfo.Status.READY);
            } else {
                info.setStatus(TrackInfo.Status.ERROR);
            }
        } catch (Exception e) {
            LOGGER.error("Metadata fetch failed: {}", e.getMessage());
            info.setStatus(TrackInfo.Status.ERROR);
        }
    }
}
