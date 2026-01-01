package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraLifecyclePacket(
    @get:JvmName("getEntityId") val entityId: Int,
    @get:JvmName("getAction") val action: Action,
    @get:JvmName("getOutTicks") val outTicks: Int,
    @get:JvmName("getX") val x: Double,
    @get:JvmName("getY") val y: Double,
    @get:JvmName("getZ") val z: Double,
    @get:JvmName("getDx") val dx: Double,
    @get:JvmName("getDy") val dy: Double,
    @get:JvmName("getDz") val dz: Double,
    @get:JvmName("getBbw") val bbw: Float,
    @get:JvmName("getBbh") val bbh: Float,
    @get:JvmName("getBbs") val bbs: Double,
    @get:JvmName("getCorruption") val corruption: Float
) : NetworkPacket<AuraLifecyclePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeVarInt(action.ordinal)
        buf.writeVarInt(outTicks)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        buf.writeDouble(dx)
        buf.writeDouble(dy)
        buf.writeDouble(dz)
        buf.writeFloat(bbw)
        buf.writeFloat(bbh)
        buf.writeDouble(bbs)
        buf.writeFloat(corruption)
    }

    enum class Action {
        START,
        FADE_OUT
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_lifecycle")

        fun decode(buf: RegistryFriendlyByteBuf): AuraLifecyclePacket {
            return AuraLifecyclePacket(
                buf.readVarInt(),
                Action.entries[buf.readVarInt()],
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readFloat()
            )
        }
    }
}
