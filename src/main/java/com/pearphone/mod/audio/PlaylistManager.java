package com.pearphone.mod.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
    }

    public void removeTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        if (index == currentIndex) {
            stopPlayer();
            currentIndex = -1;
        } else if (index < currentIndex) {
            currentIndex--;
        }
        tracks.remove(index);
    }

    public void clearAll() {
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
        EnhancedAudioPlayer p = new EnhancedAudioPlayer(tracks.get(index).getUrl(), volume);
        p.setOnComplete(this::onTrackComplete);
        player = p;
        p.play();
        LOGGER.info("Playlist: playing index {} — {}", index, tracks.get(index).getTitle());
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
        }
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
