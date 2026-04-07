package com.pearphone.mod.audio;

import com.pearphone.mod.config.AudioPlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages a playlist of {@link TrackInfo} objects and owns the active {@link EnhancedAudioPlayer}.
 *
 * <p>Thread-safety:
 * <ul>
 *   <li>{@code tracks} is a CopyOnWriteArrayList — safe for concurrent reads from the GUI thread.</li>
 *   <li>{@code currentIndex}, {@code player}, and {@code volume} are volatile.</li>
 *   <li>Shuffle/repeat state and {@code shuffledIndices} are guarded by {@code this}.</li>
 * </ul>
 */
public class PlaylistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum ShuffleMode { OFF, ON }
    public enum RepeatMode  { NONE, ALL, ONE }
    /** Controls who hears the audio from this item player. */
    public enum ListenMode  { HEADPHONES, SPEAKER, MUTE }

    // ── Core state ────────────────────────────────────────────────────────────

    private final CopyOnWriteArrayList<TrackInfo> tracks = new CopyOnWriteArrayList<>();
    private volatile int currentIndex = -1;
    private volatile EnhancedAudioPlayer player;
    private volatile int volume = 100;

    // ── Shuffle / repeat ──────────────────────────────────────────────────────

    private ShuffleMode shuffleMode = ShuffleMode.OFF;
    private RepeatMode  repeatMode  = RepeatMode.NONE;
    private ListenMode  listenMode  = ListenMode.HEADPHONES;
    /**
     * Play order used when shuffle is ON.  Each element is a track index.
     * Guarded by {@code this}.
     */
    private final List<Integer> shuffledIndices = new ArrayList<>();
    /** Position inside {@code shuffledIndices} for the currently playing (or last-played) track. */
    private int shufflePos = 0;

    /**
     * When a saved position is restored from disk, this holds the offset that
     * {@link #playOrResume()} will use on the first play call.
     * Also returned by {@link #getPositionSeconds()} while no player is active,
     * so the progress bar shows the correct position before playback starts.
     */
    private volatile double restoredPositionSeconds = 0;

    // ── Preload ───────────────────────────────────────────────────────────────

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
        int newIndex;
        synchronized (this) {
            tracks.add(info);
            newIndex = tracks.size() - 1;
            if (shuffleMode == ShuffleMode.ON) shuffledIndices.add(newIndex);
        }
        metadataPool.submit(() -> fetchMetadata(info));
        if (currentIndex >= 0) schedulePreloads(currentIndex);
    }

    /**
     * Add a URL with pre-known metadata (e.g. restored from a save file).
     * When {@code title} is non-null and {@code durationSeconds} ≥ 0 the track is
     * marked {@link TrackInfo.Status#READY} immediately, skipping the yt-dlp
     * metadata round-trip.
     */
    public void addTrackWithMeta(String url, String title, int durationSeconds) {
        if (url == null || url.isBlank()) return;
        TrackInfo info = new TrackInfo(url);
        if (title != null && !title.isBlank() && durationSeconds >= 0) {
            info.setTitle(title);
            info.setDurationSeconds(durationSeconds);
            info.setStatus(TrackInfo.Status.READY);
        } else {
            metadataPool.submit(() -> fetchMetadata(info));
        }
        synchronized (this) {
            tracks.add(info);
            if (shuffleMode == ShuffleMode.ON) shuffledIndices.add(tracks.size() - 1);
        }
        if (currentIndex >= 0) schedulePreloads(currentIndex);
    }

    public synchronized void removeTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        cancelAllPreloads();
        if (index == currentIndex) {
            stopPlayer();
            currentIndex = -1;
        } else if (index < currentIndex) {
            currentIndex--;
        }
        tracks.remove(index);

        // Remove from shuffledIndices and fix up all indices > removed index
        if (shuffleMode == ShuffleMode.ON) {
            shuffledIndices.removeIf(i -> i == index);
            shuffledIndices.replaceAll(i -> i > index ? i - 1 : i);
            if (shufflePos >= shuffledIndices.size()) {
                shufflePos = Math.max(0, shuffledIndices.size() - 1);
            }
        }

        if (currentIndex >= 0) schedulePreloads(currentIndex);
    }

    public synchronized void clearAll() {
        cancelAllPreloads();
        stopPlayer();
        currentIndex = -1;
        shuffledIndices.clear();
        shufflePos = 0;
        restoredPositionSeconds = 0;
        tracks.clear();
    }

    // ── Playback control ──────────────────────────────────────────────────────

    /** Play track at {@code index} from the beginning. */
    public void play(int index) {
        play(index, 0);
    }

    /**
     * Play track at {@code index} starting at {@code seekOffset} seconds.
     * Stops and replaces any currently playing track.
     */
    public synchronized void play(int index, double seekOffset) {
        if (index < 0 || index >= tracks.size()) return;
        stopPlayer();

        // Consume a restored position only if it matches this exact track
        if (seekOffset <= 0 && index == currentIndex && restoredPositionSeconds > 0) {
            seekOffset = restoredPositionSeconds;
        }
        restoredPositionSeconds = 0;

        currentIndex = index;

        // Keep shufflePos in sync when a track is selected directly
        if (shuffleMode == ShuffleMode.ON) {
            int pos = shuffledIndices.indexOf(index);
            if (pos >= 0) shufflePos = pos;
        }

        // Preloaded players started from position 0 — discard them when seeking to a non-zero offset
        EnhancedAudioPlayer p;
        if (seekOffset <= 0) {
            p = preloadedPlayers.remove(index);
        } else {
            EnhancedAudioPlayer stale = preloadedPlayers.remove(index);
            if (stale != null) stale.stop();
            p = null;
        }
        int effectiveVolume = listenMode == ListenMode.MUTE ? 0 : volume;
        if (p != null) {
            p.setVolume(effectiveVolume);
            LOGGER.info("Playlist: playing index {} from preload — {}", index, tracks.get(index).getTitle());
        } else {
            p = new EnhancedAudioPlayer(tracks.get(index).getUrl(), effectiveVolume);
            if (seekOffset > 0) p.setSeekOffset(seekOffset);
            LOGGER.info("Playlist: playing index {} (cold start, seek={}s) — {}",
                    index, (int) seekOffset, tracks.get(index).getTitle());
        }
        p.setOnComplete(this::onTrackComplete);
        player = p;
        p.play();

        schedulePreloads(index);
    }

    /** Start or resume. If paused → resume. If stopped → play current (or first) track. */
    public synchronized void playOrResume() {
        EnhancedAudioPlayer p = player;
        if (p != null && p.isPaused()) {
            p.resume();
        } else if (p == null || (!p.isPlaying() && !p.isPaused())) {
            int idx = currentIndex >= 0 ? currentIndex : (tracks.isEmpty() ? -1 : 0);
            if (idx >= 0) {
                // play(int, double) will consume restoredPositionSeconds for us
                play(idx, 0);
            }
        }
    }

    public void pause() {
        EnhancedAudioPlayer p = player;
        if (p != null) p.pause();
    }

    public void stop() {
        stopPlayer();
    }

    public synchronized void next() {
        if (tracks.isEmpty()) return;
        if (shuffleMode == ShuffleMode.ON && !shuffledIndices.isEmpty()) {
            int nextPos = shufflePos + 1;
            if (nextPos >= shuffledIndices.size()) {
                if (repeatMode == RepeatMode.ALL) nextPos = 0;
                else return;
            }
            shufflePos = nextPos;
            play(shuffledIndices.get(shufflePos));
        } else {
            int next = currentIndex + 1;
            if (next >= tracks.size()) {
                if (repeatMode == RepeatMode.ALL) next = 0;
                else return;
            }
            play(next);
        }
    }

    public synchronized void prev() {
        if (tracks.isEmpty()) return;
        if (shuffleMode == ShuffleMode.ON && !shuffledIndices.isEmpty()) {
            int prevPos = shufflePos - 1;
            if (prevPos < 0) {
                if (repeatMode == RepeatMode.ALL) prevPos = shuffledIndices.size() - 1;
                else return;
            }
            shufflePos = prevPos;
            play(shuffledIndices.get(shufflePos));
        } else {
            int prev = currentIndex - 1;
            if (prev < 0) {
                if (repeatMode == RepeatMode.ALL) prev = tracks.size() - 1;
                else return;
            }
            play(prev);
        }
    }

    public void setVolume(int v) {
        volume = Math.max(0, Math.min(100, v));
        EnhancedAudioPlayer p = player;
        if (p != null) p.setVolume(listenMode == ListenMode.MUTE ? 0 : volume);
    }

    /** Seek the current track to {@code seconds}. No-op if nothing is playing. */
    public void seekTo(double seconds) {
        EnhancedAudioPlayer p = player;
        if (p != null) p.seekTo(seconds);
    }

    // ── Shuffle / repeat control ───────────────────────────────────────────────

    /**
     * Cycle shuffle: OFF → ON → OFF.
     * When enabling, a random play order is built with the current track first.
     */
    public synchronized void cycleShuffleMode() {
        if (shuffleMode == ShuffleMode.OFF) {
            shuffleMode = ShuffleMode.ON;
            rebuildShuffledOrder();
        } else {
            shuffleMode = ShuffleMode.OFF;
            shuffledIndices.clear();
            shufflePos = 0;
        }
    }

    /** Cycle repeat: NONE → ALL → ONE → NONE. */
    public synchronized void cycleRepeatMode() {
        repeatMode = switch (repeatMode) {
            case NONE -> RepeatMode.ALL;
            case ALL  -> RepeatMode.ONE;
            case ONE  -> RepeatMode.NONE;
        };
    }

    /**
     * Cycle listen mode: HEADPHONES → SPEAKER → MUTE → HEADPHONES.
     * Switching to MUTE silences the player without stopping it.
     * Switching away from MUTE restores volume immediately.
     */
    public synchronized void cycleListenMode() {
        listenMode = switch (listenMode) {
            case HEADPHONES -> ListenMode.SPEAKER;
            case SPEAKER    -> ListenMode.MUTE;
            case MUTE       -> ListenMode.HEADPHONES;
        };
        applyMuteToPlayer();
    }

    // ── Display order (respects shuffle for the playlist panel) ───────────────

    /**
     * Returns tracks in their current display order.
     * When shuffle is ON this follows {@code shuffledIndices}; otherwise insertion order.
     */
    public synchronized List<TrackInfo> getDisplayTracks() {
        if (shuffleMode == ShuffleMode.OFF || shuffledIndices.isEmpty()) {
            return new ArrayList<>(tracks);
        }
        List<TrackInfo> result = new ArrayList<>(shuffledIndices.size());
        for (int idx : shuffledIndices) {
            if (idx >= 0 && idx < tracks.size()) result.add(tracks.get(idx));
        }
        return result;
    }

    /**
     * The display-list position of the currently selected track.
     * When shuffle is OFF this equals {@link #getCurrentIndex()}.
     */
    public synchronized int getCurrentDisplayIndex() {
        return (shuffleMode == ShuffleMode.OFF || shuffledIndices.isEmpty())
                ? currentIndex : shufflePos;
    }

    /**
     * Converts a position in the display list back to a real track index.
     * Used when the user clicks a row or removes a track.
     */
    public synchronized int displayIndexToTrackIndex(int displayIdx) {
        if (shuffleMode == ShuffleMode.OFF || shuffledIndices.isEmpty()) return displayIdx;
        if (displayIdx >= 0 && displayIdx < shuffledIndices.size()) {
            return shuffledIndices.get(displayIdx);
        }
        return displayIdx;
    }

    private void rebuildShuffledOrder() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) indices.add(i);
        Collections.shuffle(indices);
        // Move current track to position 0 so it plays now; rest is random
        if (currentIndex >= 0) {
            indices.remove(Integer.valueOf(currentIndex));
            indices.add(0, currentIndex);
            shufflePos = 0;
        }
        shuffledIndices.clear();
        shuffledIndices.addAll(indices);
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    /**
     * Restore the current-track index and saved playback position from disk
     * without starting playback.  The position is used on the next
     * {@link #playOrResume()} call and is shown in the progress bar in the meantime.
     */
    public synchronized void restoreCurrentTrack(int index, double positionSeconds) {
        if (index >= 0 && index < tracks.size()) {
            currentIndex = index;
            restoredPositionSeconds = positionSeconds;
        }
    }

    /** Returns a snapshot of the current shuffle order (for saving to disk). */
    public synchronized List<Integer> getShuffledIndicesCopy() {
        return new ArrayList<>(shuffledIndices);
    }

    /** Restores a previously saved shuffle order (call after all tracks are loaded). */
    public synchronized void restoreShuffledIndices(List<Integer> order, int savedShufflePos) {
        shuffledIndices.clear();
        shuffledIndices.addAll(order);
        shufflePos = Math.max(0, Math.min(savedShufflePos, shuffledIndices.size() - 1));
    }

    // ── State queries ──────────────────────────────────────────────────────────

    public List<TrackInfo> getTracks()    { return new ArrayList<>(tracks); }
    public int             getCurrentIndex() { return currentIndex; }
    public int             getVolume()    { return volume; }
    public synchronized ShuffleMode getShuffleMode() { return shuffleMode; }
    public synchronized RepeatMode  getRepeatMode()  { return repeatMode; }
    public synchronized ListenMode  getListenMode()  { return listenMode; }
    public synchronized void setShuffleMode(ShuffleMode m) { shuffleMode = m; }
    public synchronized void setRepeatMode(RepeatMode m)   { repeatMode  = m; }
    public synchronized void setListenMode(ListenMode m) {
        listenMode = m;
        applyMuteToPlayer();
    }

    private void applyMuteToPlayer() {
        EnhancedAudioPlayer p = player;
        if (p != null) {
            p.setVolume(listenMode == ListenMode.MUTE ? 0 : volume);
        }
    }

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

    /**
     * Position in the current track in seconds.
     * Returns {@link #restoredPositionSeconds} when no player is active
     * so the progress bar shows the last-known position before playback starts.
     */
    public double getPositionSeconds() {
        EnhancedAudioPlayer p = player;
        if (p != null) return p.getPositionSeconds();
        return restoredPositionSeconds;
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
    private synchronized void onTrackComplete() {
        if (repeatMode == RepeatMode.ONE) {
            play(currentIndex);
            return;
        }

        if (shuffleMode == ShuffleMode.ON && !shuffledIndices.isEmpty()) {
            int nextPos = shufflePos + 1;
            if (nextPos >= shuffledIndices.size()) {
                if (repeatMode == RepeatMode.ALL) {
                    nextPos = 0;
                } else {
                    currentIndex = -1;
                    player = null;
                    cancelAllPreloads();
                    return;
                }
            }
            shufflePos = nextPos;
            play(shuffledIndices.get(shufflePos));
        } else {
            int next = currentIndex + 1;
            if (next >= tracks.size()) {
                if (repeatMode == RepeatMode.ALL) {
                    next = 0;
                } else {
                    currentIndex = -1;
                    player = null;
                    cancelAllPreloads();
                    return;
                }
            }
            play(next);
        }
    }

    // ── Preload management ────────────────────────────────────────────────────

    private void schedulePreloads(int fromIndex) {
        int count = preloadCount();
        if (count == 0) return;
        preloadedPlayers.entrySet().removeIf(entry -> {
            int idx = entry.getKey();
            boolean outOfWindow = idx <= fromIndex || idx > fromIndex + count;
            if (outOfWindow) entry.getValue().stop();
            return outOfWindow;
        });
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
