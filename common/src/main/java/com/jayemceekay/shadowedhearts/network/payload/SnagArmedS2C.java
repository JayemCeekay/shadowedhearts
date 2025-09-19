package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: updates the client's knowledge of whether their Snag Machine is armed.
 */
public record SnagArmedS2C(boolean armed) implements CustomPacketPayload {
    public static final Type<SnagArmedS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_armed"));

    public static final StreamCodec<FriendlyByteBuf, SnagArmedS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SnagArmedS2C decode(FriendlyByteBuf buf) {
            return new SnagArmedS2C(buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, SnagArmedS2C pkt) {
            buf.writeBoolean(pkt.armed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
