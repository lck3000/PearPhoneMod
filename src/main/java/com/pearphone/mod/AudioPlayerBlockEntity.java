package com.pearphone.mod;

import com.pearphone.mod.audio.PlaylistManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import com.pearphone.mod.audio.TrackInfo;
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
        if (tag.contains("Playlist", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Playlist", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String url      = entry.getString("url");
                String title    = entry.contains("title")    ? entry.getString("title")  : null;
                int    duration = entry.contains("duration") ? entry.getInt("duration")   : -1;
                playlist.addTrackWithMeta(url, title, duration);
            }
        } else if (tag.contains("PlaylistUrls", Tag.TAG_LIST)) {
            // Legacy format (URLs only) — fetch metadata in background as before
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
        for (TrackInfo track : playlist.getTracks()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("url", track.getUrl());
            entry.putString("title", track.getTitle());
            entry.putInt("duration", track.getDurationSeconds());
            list.add(entry);
        }
        tag.put("Playlist", list);
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
