package com.pearphone.mod;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, "pearphone");

    public static final DeferredHolder<MenuType<?>, MenuType<AudioPlayerMenu>> AUDIO_PLAYER_MENU =
            MENU_TYPES.register("audio_player_menu",
                    () -> IMenuTypeExtension.create((containerId, inventory, buf) ->
                            new AudioPlayerMenu(containerId, inventory, null)));

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
