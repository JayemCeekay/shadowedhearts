package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: sends the set of entity IDs selected with the Trainer's Whistle
 * via quick tap or brush selection.
 */
public record WhistleSelectionC2S(int[] entityIds) implements CustomPacketPayload {
    public static final Type<WhistleSelectionC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "whistle_selection"));

    public static final StreamCodec<FriendlyByteBuf, WhistleSelectionC2S> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WhistleSelectionC2S decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) ids[i] = buf.readVarInt();
            return new WhistleSelectionC2S(ids);
        }

        @Override
        public void encode(FriendlyByteBuf buf, WhistleSelectionC2S pkt) {
            int[] ids = pkt.entityIds();
            buf.writeVarInt(ids.length);
            for (int id : ids) buf.writeVarInt(id);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
