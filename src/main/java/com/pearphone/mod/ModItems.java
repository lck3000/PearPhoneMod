package com.pearphone.mod;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("pearphone");

    public static final DeferredItem<PearPhoneItem> PORTABLE_PLAYER = ITEMS.register("pearphone",
            () -> new PearPhoneItem(new Item.Properties()
                    .stacksTo(1)
            ));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
