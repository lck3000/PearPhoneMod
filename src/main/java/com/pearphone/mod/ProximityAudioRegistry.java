package com.pearphone.mod;

import com.pearphone.mod.config.AudioPlayerConfig;
import com.pearphone.mod.network.ProximityAudioPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry for players broadcasting audio in SPEAKER mode.
 *
 * <p>Call {@link #tick(MinecraftServer)} every few server ticks to push
 * volume-adjusted packets to nearby players.
 */
public class ProximityAudioRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioPlayer");

    private record Entry(UUID uuid, String url, int baseVolume, String levelKey) {}

    /** UUID of the broadcasting player → broadcast entry. */
    private static final ConcurrentHashMap<UUID, Entry> broadcasters = new ConcurrentHashMap<>();

    /**
     * Register or update a player as a speaker-mode broadcaster.
     * Called when the client sends {@link com.pearphone.mod.network.SpeakerBroadcastPacket}.
     */
    public static void register(ServerPlayer player, String url, int volume) {
        if (url == null || url.isBlank()) { deregister(player); return; }
        UUID id = player.getUUID();
        String key = player.level().dimension().location().toString();
        broadcasters.put(id, new Entry(id, url, volume, key));
        LOGGER.info("[Proximity] {} registered as broadcaster ({})", player.getName().getString(), url);
    }

    /** Remove a player from the broadcaster registry and tell nearby clients to stop. */
    public static void deregister(ServerPlayer player) {
        if (broadcasters.remove(player.getUUID()) != null) {
            // Notify all players who might be listening: stop this broadcaster's stream
            for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(player.getUUID())) {
                    PacketDistributor.sendToPlayer(p,
                            new ProximityAudioPacket(player.getUUID(), "", 0, false));
                }
            }
            LOGGER.info("[Proximity] {} deregistered", player.getName().getString());
        }
    }

    /**
     * Tick — call every ~40 ticks (2 s).  For each active broadcaster, finds players
     * within the configured proximity range and sends a volume-scaled packet.
     * Players who left range receive a stop packet.
     */
    public static void tick(MinecraftServer server) {
        if (broadcasters.isEmpty()) return;

        int maxRange = proximityRange();
        List<UUID> stale = new ArrayList<>();

        for (Entry entry : broadcasters.values()) {
            ServerPlayer broadcaster = server.getPlayerList().getPlayer(entry.uuid());
            if (broadcaster == null || !broadcaster.isAlive()) {
                stale.add(entry.uuid());
                continue;
            }

            Vec3 bPos = broadcaster.position();
            String bDim = broadcaster.level().dimension().location().toString();

            for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
                // The broadcaster hears themselves already via local EnhancedAudioPlayer
                if (listener.getUUID().equals(entry.uuid())) continue;

                // Must be in the same dimension
                if (!listener.level().dimension().location().toString().equals(bDim)) {
                    PacketDistributor.sendToPlayer(listener,
                            new ProximityAudioPacket(entry.uuid(), "", 0, false));
                    continue;
                }

                double dist = listener.position().distanceTo(bPos);
                if (dist > maxRange) {
                    PacketDistributor.sendToPlayer(listener,
                            new ProximityAudioPacket(entry.uuid(), "", 0, false));
                } else {
                    // Linear volume falloff: full volume at dist=0, silent at dist=maxRange
                    double ratio = Math.max(0, 1.0 - dist / maxRange);
                    int scaledVol = (int) Math.round(entry.baseVolume() * ratio);
                    PacketDistributor.sendToPlayer(listener,
                            new ProximityAudioPacket(entry.uuid(), entry.url(), scaledVol, true));
                }
            }
        }

        stale.forEach(broadcasters::remove);
    }

    private static int proximityRange() {
        try { return AudioPlayerConfig.COMMON.proximityRange.get(); }
        catch (Exception e) { return 64; }
    }
}
