package com.pearphone.mod;

import com.pearphone.mod.audio.YtDlpManager;
import com.pearphone.mod.config.AudioPlayerConfig;
import com.pearphone.mod.network.ProximityAudioPacket;
import com.pearphone.mod.network.SpeakerBroadcastPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod("pearphone")
public class PearPhoneMod {
    public static final String MODID = "pearphone";

    public PearPhoneMod(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, AudioPlayerConfig.COMMON_SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, AudioPlayerConfig.CLIENT_SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, AudioPlayerConfig.SERVER_SPEC);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        event.registrar(MODID)
             .playToServer(SpeakerBroadcastPacket.TYPE, SpeakerBroadcastPacket.CODEC,
                           SpeakerBroadcastPacket::handle)
             .playToClient(ProximityAudioPacket.TYPE, ProximityAudioPacket.CODEC,
                           ProximityAudioPacket::handle);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Start yt-dlp download in the background immediately at mod load time,
        // so it is ready long before the player tries to use the audio block.
        YtDlpManager.ensureAvailableAsync();
        event.enqueueWork(() -> System.out.println("PearPhone Mod loaded successfully!"));
    }
}
