package com.pearphone.mod.network;

import com.pearphone.mod.AudioPlayerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client sync packet.  Currently carries a URL to add to the block's playlist
 * and the target volume.  Extend as needed for richer sync.
 */
public record AudioPlayerSyncPacket(BlockPos pos, String url, int volume)
        implements CustomPacketPayload {

    public static final Type<AudioPlayerSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("pearphone", "audio_sync"));

    public static final StreamCodec<ByteBuf, AudioPlayerSyncPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AudioPlayerSyncPacket::pos,
            ByteBufCodecs.STRING_UTF8, AudioPlayerSyncPacket::url,
            ByteBufCodecs.INT, AudioPlayerSyncPacket::volume,
            AudioPlayerSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioPlayerSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.getBlockEntity(packet.pos()) instanceof AudioPlayerBlockEntity be) {
                be.getPlaylist().setVolume(packet.volume());
                if (!packet.url().isEmpty()) {
                    be.getPlaylist().addTrack(packet.url());
                }
            }
        });
    }
}
