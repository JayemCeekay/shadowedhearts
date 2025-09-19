package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: clear current tactical orders for the player's selected allies.
 */
public record CancelOrdersC2S() implements CustomPacketPayload {
    public static final Type<CancelOrdersC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "cancel_orders"));

    public static final StreamCodec<FriendlyByteBuf, CancelOrdersC2S> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CancelOrdersC2S decode(FriendlyByteBuf buf) { return new CancelOrdersC2S(); }

        @Override
        public void encode(FriendlyByteBuf buf, CancelOrdersC2S pkt) { /* no fields */ }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
