package com.pearphone.mod;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;

public class AudioPlayer {
    private String youtubeUrl;
    private int volume;
    private Clip audioClip;
    private FloatControl volumeControl;
    private boolean isPlaying = false;

    public AudioPlayer(String youtubeUrl, int volume) {
        this.youtubeUrl = youtubeUrl;
        this.volume = Math.max(0, Math.min(100, volume));
    }

    public void play() {
        try {
            // Extract video ID
            String videoId = YouTubeParser.extractVideoId(this.youtubeUrl);
            if (videoId == null) {
                throw new IllegalArgumentException("Invalid YouTube URL");
            }

            // For actual implementation, you would need to:
            // 1. Use youtube-dl or yt-dlp to extract audio stream URL
            // 2. Stream the audio through javax.sound.sampled
            // 3. Handle proximity-based volume adjustment

            // Placeholder implementation
            System.out.println("Playing audio from: " + videoId);
            this.isPlaying = true;

        } catch (Exception e) {
            System.err.println("Audio playback error: " + e.getMessage());
            this.isPlaying = false;
        }
    }

    public void stop() {
        if (this.audioClip != null && this.audioClip.isRunning()) {
            this.audioClip.stop();
            this.audioClip.close();
        }
        this.isPlaying = false;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        if (this.volumeControl != null) {
            float decibels = 20f * (float) Math.log10((double) this.volume / 100f);
            this.volumeControl.setValue(decibels);
        }
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public int getVolume() {
        return this.volume;
    }
}
