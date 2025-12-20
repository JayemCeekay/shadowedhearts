package com.jayemceekay.shadowedhearts.network.payload

import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Server → Client lifecycle control for ball emitters. START includes initial transform; FADE_OUT conveys outTicks.
 */
data class BallLifecycleS2C(
    val entityId: Int,
    val action: Action,
    val outTicks: Int,
    val x: Double, val y: Double, val z: Double,
    val dx: Double, val dy: Double, val dz: Double
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    enum class Action { START, FADE_OUT }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<BallLifecycleS2C> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "ball_lifecycle")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BallLifecycleS2C> = object : StreamCodec<FriendlyByteBuf, BallLifecycleS2C> {
            override fun decode(buf: FriendlyByteBuf): BallLifecycleS2C {
                val id = buf.readVarInt()
                val action = Action.values()[buf.readVarInt()]
                val outTicks = buf.readVarInt()
                val x = buf.readDouble()
                val y = buf.readDouble()
                val z = buf.readDouble()
                val dx = buf.readDouble()
                val dy = buf.readDouble()
                val dz = buf.readDouble()
                return BallLifecycleS2C(id, action, outTicks, x, y, z, dx, dy, dz)
            }

            override fun encode(buf: FriendlyByteBuf, pkt: BallLifecycleS2C) {
                buf.writeVarInt(pkt.entityId)
                buf.writeVarInt(pkt.action.ordinal)
                buf.writeVarInt(pkt.outTicks)
                buf.writeDouble(pkt.x)
                buf.writeDouble(pkt.y)
                buf.writeDouble(pkt.z)
                buf.writeDouble(pkt.dx)
                buf.writeDouble(pkt.dy)
                buf.writeDouble(pkt.dz)
            }
        }
    }
}
