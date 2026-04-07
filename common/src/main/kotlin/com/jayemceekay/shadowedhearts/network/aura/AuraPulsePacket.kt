package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class AuraPulsePacket(val slotIndex: Int = -1) : NetworkPacket<AuraPulsePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeInt(slotIndex)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_pulse_request")

        fun decode(buf: RegistryFriendlyByteBuf): AuraPulsePacket {
            return AuraPulsePacket(buf.readInt())
        }
    }
}
