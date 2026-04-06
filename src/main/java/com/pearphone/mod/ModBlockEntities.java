package com.pearphone.mod;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "pearphone");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AudioPlayerBlockEntity>> AUDIO_PLAYER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("audio_player_block_entity",
                    () -> BlockEntityType.Builder.of(AudioPlayerBlockEntity::new, ModBlocks.AUDIO_PLAYER_BLOCK.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
