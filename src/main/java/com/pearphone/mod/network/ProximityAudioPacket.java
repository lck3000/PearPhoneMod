package com.pearphone.mod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server → client.
 * Tells the receiving client to play (or stop) audio from a specific broadcaster.
 *
 * <ul>
 *   <li>{@code playing=true}  — play {@code url} at {@code volume} (distance-scaled)</li>
 *   <li>{@code playing=false} — stop whatever was playing from this broadcaster</li>
 * </ul>
 */
public record ProximityAudioPacket(UUID broadcasterUuid, String url, int volume, boolean playing)
        implements CustomPacketPayload {

    public static final Type<ProximityAudioPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("pearphone", "proximity_audio"));

    public static final StreamCodec<ByteBuf, ProximityAudioPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
                                              ProximityAudioPacket::broadcasterUuid,
                    ByteBufCodecs.STRING_UTF8, ProximityAudioPacket::url,
                    ByteBufCodecs.INT,         ProximityAudioPacket::volume,
                    ByteBufCodecs.BOOL,        ProximityAudioPacket::playing,
                    ProximityAudioPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Handling is done in {@link com.pearphone.mod.client.ClientEventHandlers}. */
    public static void handle(ProximityAudioPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                com.pearphone.mod.client.ClientEventHandlers.handleProximityAudio(pkt));
    }
}
