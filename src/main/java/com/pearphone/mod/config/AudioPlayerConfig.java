package com.pearphone.mod.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class AudioPlayerConfig {
    public static class Common {
        public final ModConfigSpec.IntValue proximityRange;
        public final ModConfigSpec.IntValue maxVolume;
        public final ModConfigSpec.IntValue defaultVolume;
        public final ModConfigSpec.BooleanValue enableProximitySound;
        public final ModConfigSpec.BooleanValue enableYoutubeSupport;
        public final ModConfigSpec.DoubleValue volumeDecayFactor;
        public final ModConfigSpec.IntValue preloadCount;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Audio Player Configuration")
                    .push("audio_player");

            proximityRange = builder
                    .comment("Maximum distance players can hear the audio player (in blocks, 0-256)")
                    .defineInRange("proximityRange", 64, 0, 256);

            maxVolume = builder
                    .comment("Maximum volume level (0-100)")
                    .defineInRange("maxVolume", 100, 0, 100);

            defaultVolume = builder
                    .comment("Default volume when audio player is first placed (0-100)")
                    .defineInRange("defaultVolume", 50, 0, 100);

            enableProximitySound = builder
                    .comment("Enable proximity-based sound attenuation")
                    .define("enableProximitySound", true);

            enableYoutubeSupport = builder
                    .comment("Enable YouTube URL support (requires yt-dlp to be installed)")
                    .define("enableYoutubeSupport", true);

            volumeDecayFactor = builder
                    .comment("Volume decay factor per block distance (0.0-1.0, where 1.0 = no decay)")
                    .defineInRange("volumeDecayFactor", 0.95, 0.0, 1.0);

            preloadCount = builder
                    .comment("Number of upcoming playlist tracks to preload for gapless transitions (0 = disabled, max 5)")
                    .defineInRange("preloadCount", 2, 0, 5);

            builder.pop();
        }
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue showVolumeParticles;
        public final ModConfigSpec.BooleanValue enableGuiAnimations;
        public final ModConfigSpec.IntValue guiScalePercent;

        Client(ModConfigSpec.Builder builder) {
            builder.comment("Client-side Audio Player Configuration")
                    .push("client");

            showVolumeParticles = builder
                    .comment("Show particle effects when adjusting volume")
                    .define("showVolumeParticles", true);

            enableGuiAnimations = builder
                    .comment("Enable GUI animations and transitions")
                    .define("enableGuiAnimations", true);

            guiScalePercent = builder
                    .comment("GUI scale percentage (50-200)")
                    .defineInRange("guiScalePercent", 100, 50, 200);

            builder.pop();
        }
    }

    public static class Server {
        public final ModConfigSpec.BooleanValue requireYtDlp;
        public final ModConfigSpec.IntValue audioStreamTimeout;
        public final ModConfigSpec.BooleanValue cacheAudioStreams;
        public final ModConfigSpec.IntValue maxCacheSize;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("Server-side Audio Player Configuration")
                    .push("server");

            requireYtDlp = builder
                    .comment("Require yt-dlp to be installed for YouTube support")
                    .define("requireYtDlp", false);

            audioStreamTimeout = builder
                    .comment("Audio stream timeout in seconds")
                    .defineInRange("audioStreamTimeout", 30, 5, 300);

            cacheAudioStreams = builder
                    .comment("Cache audio streams for performance")
                    .define("cacheAudioStreams", true);

            maxCacheSize = builder
                    .comment("Maximum cache size in MB (0 = unlimited)")
                    .defineInRange("maxCacheSize", 500, 0, 10000);

            builder.pop();
        }
    }

    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        {
            Pair<Common, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(Common::new);
            COMMON = commonPair.getLeft();
            COMMON_SPEC = commonPair.getRight();
        }
        {
            Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
            CLIENT = clientPair.getLeft();
            CLIENT_SPEC = clientPair.getRight();
        }
        {
            Pair<Server, ModConfigSpec> serverPair = new ModConfigSpec.Builder().configure(Server::new);
            SERVER = serverPair.getLeft();
            SERVER_SPEC = serverPair.getRight();
        }
    }
}
