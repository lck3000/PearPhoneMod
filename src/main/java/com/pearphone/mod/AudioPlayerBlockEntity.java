package com.pearphone.mod;

import com.pearphone.mod.audio.PlaylistManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AudioPlayerBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {

    private final PlaylistManager playlist = new PlaylistManager();

    public AudioPlayerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.AUDIO_PLAYER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public PlaylistManager getPlaylist() {
        return playlist;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        playlist.shutdown();
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        playlist.clearAll();
        if (tag.contains("PlaylistUrls", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PlaylistUrls", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                playlist.addTrack(list.getString(i));
            }
        }
        if (tag.contains("Volume")) {
            playlist.setVolume(tag.getInt("Volume"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (var track : playlist.getTracks()) {
            list.add(StringTag.valueOf(track.getUrl()));
        }
        tag.put("PlaylistUrls", list);
        tag.putInt("Volume", playlist.getVolume());
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.literal("Portable Audio Player");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AudioPlayerMenu(containerId, playerInventory, this);
    }
}
