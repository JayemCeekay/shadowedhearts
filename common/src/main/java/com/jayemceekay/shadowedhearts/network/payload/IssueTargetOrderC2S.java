package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: player confirms a target for the current order (uses server-side selection set).
 */
public record IssueTargetOrderC2S(TacticalOrderType orderType, int targetEntityId) implements CustomPacketPayload {
    public static final Type<IssueTargetOrderC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("shadowedhearts", "issue_target_order"));

    public static final StreamCodec<FriendlyByteBuf, IssueTargetOrderC2S> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IssueTargetOrderC2S decode(FriendlyByteBuf buf) {
            TacticalOrderType type = buf.readEnum(TacticalOrderType.class);
            int id = buf.readVarInt();
            return new IssueTargetOrderC2S(type, id);
        }

        @Override
        public void encode(FriendlyByteBuf buf, IssueTargetOrderC2S pkt) {
            buf.writeEnum(pkt.orderType());
            buf.writeVarInt(pkt.targetEntityId());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
