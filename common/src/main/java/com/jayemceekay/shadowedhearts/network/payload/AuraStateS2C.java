package com.jayemceekay.shadowedhearts.network.payload;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client payload carrying authoritative state for a Pokémon aura.
 * Replaces the old "anchor" system. Contains server-side position, velocity and bounding-box info.
 */
public record AuraStateS2C(
        int entityId,
        double x, double y, double z,
        double dx, double dy, double dz,
        float bbw, float bbh, double bbs,
        long serverTick, float corruption
) implements CustomPacketPayload {
    public static final Type<AuraStateS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_state"));

    public static final StreamCodec<FriendlyByteBuf, AuraStateS2C> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AuraStateS2C decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double dx = buf.readDouble();
            double dy = buf.readDouble();
            double dz = buf.readDouble();
            float bbw = buf.readFloat();
            float bbh = buf.readFloat();
            double bbs = buf.readDouble();
            long tick = buf.readVarLong();
            float corruption = buf.readFloat();
            return new AuraStateS2C(id, x, y, z, dx, dy, dz, bbw, bbh, bbs, tick, corruption);
        }

        @Override
        public void encode(FriendlyByteBuf buf, AuraStateS2C pkt) {
            buf.writeVarInt(pkt.entityId);
            buf.writeDouble(pkt.x);
            buf.writeDouble(pkt.y);
            buf.writeDouble(pkt.z);
            buf.writeDouble(pkt.dx);
            buf.writeDouble(pkt.dy);
            buf.writeDouble(pkt.dz);
            buf.writeFloat(pkt.bbw);
            buf.writeFloat(pkt.bbh);
            buf.writeDouble(pkt.bbs);
            buf.writeVarLong(pkt.serverTick);
            buf.writeFloat(pkt.corruption);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
