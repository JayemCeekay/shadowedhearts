package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraScannerC2SPacket(val active: Boolean) : NetworkPacket<AuraScannerC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(active)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_scanner_state")

        fun decode(buf: RegistryFriendlyByteBuf): AuraScannerC2SPacket {
            return AuraScannerC2SPacket(buf.readBoolean())
        }
    }
}
