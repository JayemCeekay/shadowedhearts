package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client lifecycle control for Shadow Aura emitters.
 * The server is authoritative for when an aura exists; the client only renders.
 */
public record AuraLifecycleS2C(int entityId, Action action, int outTicks, double x, double y, double z, double dx, double dy, double dz, float bbw, float bbh, double bbs, float corruption) implements CustomPacketPayload {
    public static final Type<AuraLifecycleS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_lifecycle"));

    public static final StreamCodec<FriendlyByteBuf, AuraLifecycleS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AuraLifecycleS2C decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int actionOrd = buf.readVarInt();
            int outTicks = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double dx = buf.readDouble();
            double dy = buf.readDouble();
            double dz = buf.readDouble();
            float bbw = buf.readFloat();
            float bbh = buf.readFloat();
            double bbs = buf.readDouble();
            float corruption = buf.readFloat();
            return new AuraLifecycleS2C(id, Action.values()[actionOrd], outTicks, x, y, z, dx, dy, dz, bbw, bbh, bbs, corruption);
            
        }

        @Override
        public void encode(FriendlyByteBuf buf, AuraLifecycleS2C pkt) {
            buf.writeVarInt(pkt.entityId);
            buf.writeVarInt(pkt.action.ordinal());
            buf.writeVarInt(pkt.outTicks);
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
            buf.writeDouble(pkt.dx);
            buf.writeDouble(pkt.dy);
            buf.writeDouble(pkt.dz);
            buf.writeFloat(pkt.bbw);
            buf.writeFloat(pkt.bbh);
            buf.writeDouble(pkt.bbs);
            buf.writeFloat(pkt.corruption);
        }
    };

    public enum Action {
        START,
        FADE_OUT
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
