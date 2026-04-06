package com.pearphone.mod.client;

import com.pearphone.mod.PearPhoneMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = PearPhoneMod.MODID, value = Dist.CLIENT)
public class ClientEventHandlers {

    /**
     * Stop any standalone (item-mode) audio when the client level unloads,
     * i.e., when the player disconnects or closes the world.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            AudioPlayerScreen.shutdownStandalonePlaylist();
        }
    }
}
