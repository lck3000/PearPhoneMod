package com.pearphone.mod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("pearphone");

    public static final DeferredBlock<Block> AUDIO_PLAYER_BLOCK = BLOCKS.register("audio_player_block",
            () -> new AudioPlayerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.JUKEBOX)
                    .noOcclusion()
            ));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
