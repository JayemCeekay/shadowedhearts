package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class SnagArmedPacket(val armed: Boolean) : NetworkPacket<SnagArmedPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(armed)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_armed")

        fun decode(buf: RegistryFriendlyByteBuf): SnagArmedPacket {
            return SnagArmedPacket(buf.readBoolean())
        }
    }
}
