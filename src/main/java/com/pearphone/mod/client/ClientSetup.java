package com.pearphone.mod.client;

import com.pearphone.mod.ModMenuTypes;
import com.pearphone.mod.PearPhoneMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PearPhoneMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.AUDIO_PLAYER_MENU.get(), AudioPlayerScreen::new);
    }
}
