package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraScannerS2CPacket(val active: Boolean) : NetworkPacket<AuraScannerS2CPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(active)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_scanner_state_s2c")

        fun decode(buf: RegistryFriendlyByteBuf): AuraScannerS2CPacket {
            return AuraScannerS2CPacket(buf.readBoolean())
        }
    }
}
