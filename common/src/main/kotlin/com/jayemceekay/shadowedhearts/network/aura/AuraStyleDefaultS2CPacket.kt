package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleDefault
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** Current server presentation policy, sent on join and whenever that policy changes. */
data class AuraStyleDefaultS2CPacket(val style: AuraStyleDefault) : NetworkPacket<AuraStyleDefaultS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(style)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_style_default")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf) =
            AuraStyleDefaultS2CPacket(buffer.readEnum(AuraStyleDefault::class.java))
    }
}
