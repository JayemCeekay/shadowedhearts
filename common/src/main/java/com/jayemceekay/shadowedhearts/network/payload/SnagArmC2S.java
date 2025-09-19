package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → Server: request to set the Snag Machine armed state (e.g., from battle UI button).
 */
public record SnagArmC2S(boolean armed) implements CustomPacketPayload {
    public static final Type<SnagArmC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_arm_request"));

    public static final StreamCodec<FriendlyByteBuf, SnagArmC2S> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SnagArmC2S decode(FriendlyByteBuf buf) {
            return new SnagArmC2S(buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, SnagArmC2S pkt) {
            buf.writeBoolean(pkt.armed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
