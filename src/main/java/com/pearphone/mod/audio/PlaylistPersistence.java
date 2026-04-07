package com.pearphone.mod.audio;

import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads the standalone playlist (item-mode) to/from
 * {@code config/pearphone/playlist.json}.
 *
 * <p>Persisted fields:
 * <ul>
 *   <li>volume</li>
 *   <li>shuffleMode, repeatMode</li>
 *   <li>currentIndex, position (seconds)</li>
 *   <li>tracks — url, title, duration cached to avoid re-querying yt-dlp</li>
 *   <li>shuffleOrder — exact play order preserved across sessions</li>
 * </ul>
 */
public class PlaylistPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");
    private static final Path SAVE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("pearphone/playlist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Persist the full playlist state to disk. Safe to call from any thread. */
    public static void save(PlaylistManager pl) {
        if (pl == null) return;
        try {
            Files.createDirectories(SAVE_FILE.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("volume",       pl.getVolume());
            root.addProperty("shuffleMode",  pl.getShuffleMode().name());
            root.addProperty("repeatMode",   pl.getRepeatMode().name());
            root.addProperty("listenMode",   pl.getListenMode().name());
            root.addProperty("currentIndex", pl.getCurrentIndex());
            root.addProperty("position",     pl.getPositionSeconds());

            // Tracks
            JsonArray tracksArr = new JsonArray();
            for (TrackInfo t : pl.getTracks()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("url",      t.getUrl());
                obj.addProperty("title",    t.getTitle());
                obj.addProperty("duration", t.getDurationSeconds());
                tracksArr.add(obj);
            }
            root.add("tracks", tracksArr);

            // Shuffle order (only meaningful when shuffle is ON, but save always for safety)
            JsonArray orderArr = new JsonArray();
            for (int idx : pl.getShuffledIndicesCopy()) orderArr.add(idx);
            root.add("shuffleOrder", orderArr);

            Files.writeString(SAVE_FILE, GSON.toJson(root));
            LOGGER.info("[Persist] Saved {} track(s), idx={}, pos={}s",
                    pl.getTracks().size(), pl.getCurrentIndex(), (int) pl.getPositionSeconds());
        } catch (Exception e) {
            LOGGER.error("[Persist] Failed to save playlist: {}", e.getMessage());
        }
    }

    /**
     * Populate {@code pl} from the save file.  If the file does not exist, does nothing.
     * All track metadata is restored directly — no yt-dlp queries needed on load.
     */
    public static void load(PlaylistManager pl) {
        if (pl == null || !Files.exists(SAVE_FILE)) return;
        try {
            String json = Files.readString(SAVE_FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Volume
            if (root.has("volume")) pl.setVolume(root.get("volume").getAsInt());

            // Shuffle / repeat modes
            if (root.has("shuffleMode")) {
                try { pl.setShuffleMode(PlaylistManager.ShuffleMode.valueOf(root.get("shuffleMode").getAsString())); }
                catch (IllegalArgumentException ignored) {}
            }
            if (root.has("repeatMode")) {
                try { pl.setRepeatMode(PlaylistManager.RepeatMode.valueOf(root.get("repeatMode").getAsString())); }
                catch (IllegalArgumentException ignored) {}
            }
            if (root.has("listenMode")) {
                try { pl.setListenMode(PlaylistManager.ListenMode.valueOf(root.get("listenMode").getAsString())); }
                catch (IllegalArgumentException ignored) {}
            }

            // Tracks
            if (root.has("tracks")) {
                for (JsonElement el : root.getAsJsonArray("tracks")) {
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("url")) continue;
                    String url      = obj.get("url").getAsString();
                    String title    = obj.has("title")    ? obj.get("title").getAsString()    : null;
                    int    duration = obj.has("duration") ? obj.get("duration").getAsInt()     : -1;
                    pl.addTrackWithMeta(url, title, duration);
                }
            }

            // Restore shuffle order now that all tracks are loaded
            if (root.has("shuffleOrder")) {
                List<Integer> order = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray("shuffleOrder")) order.add(el.getAsInt());
                // Find the shufflePos that corresponds to currentIndex
                int savedIdx = root.has("currentIndex") ? root.get("currentIndex").getAsInt() : -1;
                int savedPos = order.indexOf(savedIdx);
                pl.restoreShuffledIndices(order, Math.max(0, savedPos));
            }

            // Current track + position (must come after tracks are loaded)
            int    savedIndex = root.has("currentIndex") ? root.get("currentIndex").getAsInt()    : -1;
            double savedPos   = root.has("position")     ? root.get("position").getAsDouble()     : 0;
            if (savedIndex >= 0) pl.restoreCurrentTrack(savedIndex, savedPos);

            LOGGER.info("[Persist] Loaded {} track(s), idx={}, pos={}s",
                    pl.getTracks().size(), savedIndex, (int) savedPos);
        } catch (Exception e) {
            LOGGER.error("[Persist] Failed to load playlist: {}", e.getMessage());
        }
    }
}
