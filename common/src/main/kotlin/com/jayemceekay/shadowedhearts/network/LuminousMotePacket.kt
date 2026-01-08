package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class LuminousMotePacket(
    @get:JvmName("getEntityId") val entityId: Int
) : NetworkPacket<LuminousMotePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(entityId)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "luminous_mote")

        fun decode(buf: RegistryFriendlyByteBuf): LuminousMotePacket {
            return LuminousMotePacket(
                buf.readVarInt()
            )
        }
    }
}
