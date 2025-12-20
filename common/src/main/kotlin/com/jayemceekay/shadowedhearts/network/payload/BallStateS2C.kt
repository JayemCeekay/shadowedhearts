package com.jayemceekay.shadowedhearts.network.payload

import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Server → Client payload carrying authoritative state for a thrown Poké Ball emitter.
 */
data class BallStateS2C(
    val entityId: Int,
    val x: Double, val y: Double, val z: Double,
    val dx: Double, val dy: Double, val dz: Double,
    val serverTick: Long
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<BallStateS2C> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "ball_state")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BallStateS2C> = object : StreamCodec<FriendlyByteBuf, BallStateS2C> {
            override fun decode(buf: FriendlyByteBuf): BallStateS2C {
                val id = buf.readVarInt()
                val x = buf.readDouble()
                val y = buf.readDouble()
                val z = buf.readDouble()
                val dx = buf.readDouble()
                val dy = buf.readDouble()
                val dz = buf.readDouble()
                val tick = buf.readVarLong()
                return BallStateS2C(id, x, y, z, dx, dy, dz, tick)
            }

            override fun encode(buf: FriendlyByteBuf, pkt: BallStateS2C) {
                buf.writeVarInt(pkt.entityId)
                buf.writeDouble(pkt.x)
                buf.writeDouble(pkt.y)
                buf.writeDouble(pkt.z)
                buf.writeDouble(pkt.dx)
                buf.writeDouble(pkt.dy)
                buf.writeDouble(pkt.dz)
                buf.writeVarLong(pkt.serverTick)
            }
        }
    }
}
