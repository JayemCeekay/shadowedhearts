package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class SnagArmPacket(val armed: Boolean) : NetworkPacket<SnagArmPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(armed)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_arm_request")

        fun decode(buf: RegistryFriendlyByteBuf): SnagArmPacket {
            return SnagArmPacket(buf.readBoolean())
        }
    }
}
