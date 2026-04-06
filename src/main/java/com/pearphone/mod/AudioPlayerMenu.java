package com.pearphone.mod;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class AudioPlayerMenu extends AbstractContainerMenu {

    /** Non-null when opened from the block; null in standalone (item) mode. */
    private final AudioPlayerBlockEntity audioPlayer;

    public AudioPlayerMenu(int containerId, Inventory playerInventory, AudioPlayerBlockEntity audioPlayer) {
        super(ModMenuTypes.AUDIO_PLAYER_MENU.get(), containerId);
        this.audioPlayer = audioPlayer;
        // No inventory slots — this is an audio UI, not a storage container.
    }

    public AudioPlayerBlockEntity getAudioPlayer() {
        return audioPlayer;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
