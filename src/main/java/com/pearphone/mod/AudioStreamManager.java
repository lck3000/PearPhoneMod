package com.pearphone.mod;

import com.pearphone.mod.audio.EnhancedAudioPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AudioStreamManager {
    private String currentUrl = "";
    private boolean isPlaying = false;
    private int volume = 100;
    private List<ServerPlayer> nearbyPlayers = new ArrayList<>();
    private EnhancedAudioPlayer audioPlayer;

    public void playMusic(String youtubeUrl, BlockPos position, int volume, int proximityRange) {
        this.currentUrl = youtubeUrl;
        this.volume = volume;
        this.isPlaying = true;

        try {
            if (this.audioPlayer != null) {
                this.audioPlayer.stop();
            }
            this.audioPlayer = new EnhancedAudioPlayer(youtubeUrl, volume);
            this.audioPlayer.play();
        } catch (Exception e) {
            System.err.println("Failed to play audio: " + e.getMessage());
            this.isPlaying = false;
        }
    }

    public void stopMusic() {
        this.isPlaying = false;
        if (this.audioPlayer != null) {
            this.audioPlayer.stop();
        }
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        if (this.audioPlayer != null) {
            this.audioPlayer.setVolume(this.volume);
        }
    }

    public void updateProximityPlayers(Level level, BlockPos blockPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ServerPlayer> players = new ArrayList<>();
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);

        // Find all players within proximity range
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == serverLevel) {
                double distance = player.position().distanceTo(blockCenter);
                if (distance <= 64) { // 64 blocks proximity
                    players.add(player);
                }
            }
        }

        this.nearbyPlayers = players;
    }

    public List<ServerPlayer> getNearbyPlayers() {
        return new ArrayList<>(this.nearbyPlayers);
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public String getCurrentUrl() {
        return this.currentUrl;
    }

    public int getVolume() {
        return this.volume;
    }
}
