package com.pearphone.mod.event;

import com.pearphone.mod.PearPhoneMod;
import com.pearphone.mod.ProximityAudioRegistry;
import com.pearphone.mod.audio.YtDlpExtractor;
import com.pearphone.mod.config.AudioPlayerConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = PearPhoneMod.MODID)
public class ServerEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("PortableAudio");

    /** Tick the proximity registry every 2 seconds (40 ticks). */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 40 == 0) {
            ProximityAudioRegistry.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Audio Player Mod - Server Starting");

        // yt-dlp download was started at mod load time (PearPhoneMod.commonSetup).
        // By now it should be complete; getManagedBinary() will wait briefly if still in flight.
        YtDlpExtractor extractor = YtDlpExtractor.getInstance();
        if (extractor.isAvailable()) {
            LOGGER.info("✓ yt-dlp ready — YouTube audio streaming enabled");
        } else {
            LOGGER.warn("yt-dlp not available yet (download may still be in progress or failed). " +
                        "Streaming will retry automatically on the first play attempt.");
        }
        
        // Log configuration
        LOGGER.info("Audio Player Config:");
        LOGGER.info("  - Proximity Range: {} blocks", AudioPlayerConfig.COMMON.proximityRange.get());
        LOGGER.info("  - Default Volume: {}%", AudioPlayerConfig.COMMON.defaultVolume.get());
        LOGGER.info("  - Proximity Sound: {}", AudioPlayerConfig.COMMON.enableProximitySound.get());
        LOGGER.info("  - Volume Decay Factor: {}", AudioPlayerConfig.COMMON.volumeDecayFactor.get());
    }
}
