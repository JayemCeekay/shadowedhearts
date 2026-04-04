package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraStatePacket(
    @get:JvmName("getEntityId") val entityId: Int,
    @get:JvmName("getX") val x: Double,
    @get:JvmName("getY") val y: Double,
    @get:JvmName("getZ") val z: Double,
    @get:JvmName("getDx") val dx: Float,
    @get:JvmName("getDy") val dy: Float,
    @get:JvmName("getDz") val dz: Float,
    @get:JvmName("getBbw") val bbw: Float,
    @get:JvmName("getBbh") val bbh: Float,
    @get:JvmName("getBbs") val bbs: Float,
    @get:JvmName("getServerTick") val serverTick: Long,
    @get:JvmName("getCorruption") val corruption: Float
) : NetworkPacket<AuraStatePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        buf.writeFloat(dx)
        buf.writeFloat(dy)
        buf.writeFloat(dz)
        buf.writeFloat(bbw)
        buf.writeFloat(bbh)
        buf.writeFloat(bbs)
        buf.writeVarLong(serverTick)
        buf.writeFloat(corruption)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_state")

        fun decode(buf: RegistryFriendlyByteBuf): AuraStatePacket {
            return AuraStatePacket(
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarLong(),
                buf.readFloat()
            )
        }
    }
}
