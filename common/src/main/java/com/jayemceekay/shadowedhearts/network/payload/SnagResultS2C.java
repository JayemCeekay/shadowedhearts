package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → Client: notifies the client that a snag attempt succeeded or failed for a target.
 */
public record SnagResultS2C(boolean success, UUID targetUuid) implements CustomPacketPayload {
    public static final Type<SnagResultS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_result"));

    public static final StreamCodec<FriendlyByteBuf, SnagResultS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SnagResultS2C decode(FriendlyByteBuf buf) {
            boolean ok = buf.readBoolean();
            UUID id = buf.readUUID();
            return new SnagResultS2C(ok, id);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SnagResultS2C pkt) {
            buf.writeBoolean(pkt.success);
            buf.writeUUID(pkt.targetUuid);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
