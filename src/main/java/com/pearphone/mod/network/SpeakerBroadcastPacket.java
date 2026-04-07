package com.pearphone.mod.network;

import com.pearphone.mod.ProximityAudioRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server.
 * Sent when the player switches their listen mode to/from SPEAKER.
 *
 * <ul>
 *   <li>{@code active=true}  — register as a broadcaster at the given URL/volume</li>
 *   <li>{@code active=false} — deregister (stop broadcasting)</li>
 * </ul>
 */
public record SpeakerBroadcastPacket(String url, int volume, boolean active)
        implements CustomPacketPayload {

    public static final Type<SpeakerBroadcastPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("pearphone", "speaker_broadcast"));

    public static final StreamCodec<ByteBuf, SpeakerBroadcastPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SpeakerBroadcastPacket::url,
                    ByteBufCodecs.INT,         SpeakerBroadcastPacket::volume,
                    ByteBufCodecs.BOOL,        SpeakerBroadcastPacket::active,
                    SpeakerBroadcastPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpeakerBroadcastPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                if (pkt.active()) {
                    ProximityAudioRegistry.register(sp, pkt.url(), pkt.volume());
                } else {
                    ProximityAudioRegistry.deregister(sp);
                }
            }
        });
    }
}
