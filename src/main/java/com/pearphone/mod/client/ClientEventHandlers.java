package com.pearphone.mod.client;

import com.pearphone.mod.PearPhoneMod;
import com.pearphone.mod.audio.EnhancedAudioPlayer;
import com.pearphone.mod.network.ProximityAudioPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = PearPhoneMod.MODID, value = Dist.CLIENT)
public class ClientEventHandlers {

    /**
     * One audio player per remote broadcaster UUID.
     * Entries are created/updated when a {@link ProximityAudioPacket} arrives
     * and removed when the broadcaster stops or goes out of range.
     */
    private static final Map<UUID, ProximityEntry> proximityPlayers = new ConcurrentHashMap<>();

    private record ProximityEntry(EnhancedAudioPlayer player, String url) {}

    // ── Level lifecycle ───────────────────────────────────────────────────────

    /**
     * Stop any standalone (item-mode) audio AND all proximity streams
     * when the client level unloads (disconnect / world close).
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PearPhoneScreen.shutdownStandalonePlaylist();
            stopAllProximityPlayers();
        }
    }

    // ── Proximity audio handling ──────────────────────────────────────────────

    /**
     * Called on the client main thread when a {@link ProximityAudioPacket} arrives.
     * Starts, updates, or stops the proximity audio stream for the given broadcaster.
     */
    public static void handleProximityAudio(ProximityAudioPacket pkt) {
        UUID id = pkt.broadcasterUuid();

        if (!pkt.playing() || pkt.url().isBlank()) {
            // Stop and remove this broadcaster's stream
            ProximityEntry entry = proximityPlayers.remove(id);
            if (entry != null) entry.player().stop();
            return;
        }

        ProximityEntry existing = proximityPlayers.get(id);

        if (existing != null && existing.url().equals(pkt.url())) {
            // Same URL — just update the volume
            existing.player().setVolume(pkt.volume());
        } else {
            // New URL or first packet for this broadcaster — (re)start the stream
            if (existing != null) existing.player().stop();
            EnhancedAudioPlayer p = new EnhancedAudioPlayer(pkt.url(), pkt.volume());
            p.play();
            proximityPlayers.put(id, new ProximityEntry(p, pkt.url()));
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static void stopAllProximityPlayers() {
        proximityPlayers.values().forEach(e -> e.player().stop());
        proximityPlayers.clear();
    }
}
