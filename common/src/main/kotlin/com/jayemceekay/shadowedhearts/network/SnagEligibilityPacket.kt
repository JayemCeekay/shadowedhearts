package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class SnagEligibilityPacket(val eligible: Boolean) : NetworkPacket<SnagEligibilityPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBoolean(eligible)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "snag_eligibility")

        fun decode(buf: RegistryFriendlyByteBuf): SnagEligibilityPacket {
            return SnagEligibilityPacket(buf.readBoolean())
        }
    }
}
