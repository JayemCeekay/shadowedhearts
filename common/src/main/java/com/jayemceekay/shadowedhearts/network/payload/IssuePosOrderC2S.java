package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: player confirms a ground position for a positional order
 * (MOVE_TO or HOLD_POSITION). Uses server-side current selection set.
 */
public record IssuePosOrderC2S(TacticalOrderType orderType, BlockPos pos, float radius, boolean persistent)
        implements CustomPacketPayload {
    public static final Type<IssuePosOrderC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "issue_pos_order"));

    public static final StreamCodec<FriendlyByteBuf, IssuePosOrderC2S> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IssuePosOrderC2S decode(FriendlyByteBuf buf) {
            TacticalOrderType type = buf.readEnum(TacticalOrderType.class);
            BlockPos pos = buf.readBlockPos();
            float radius = buf.readFloat();
            boolean persistent = buf.readBoolean();
            return new IssuePosOrderC2S(type, pos, radius, persistent);
        }

        @Override
        public void encode(FriendlyByteBuf buf, IssuePosOrderC2S pkt) {
            buf.writeEnum(pkt.orderType());
            buf.writeBlockPos(pkt.pos());
            buf.writeFloat(pkt.radius());
            buf.writeBoolean(pkt.persistent());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
