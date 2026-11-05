package com.jayemceekay.shadowedhearts.network.items

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.shadow.NullCharmEffect
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/** One selected effect, rather than a copy of the server's battle configuration. */
data class NullCharmEffectS2CPacket(val effect: NullCharmEffect) : NetworkPacket<NullCharmEffectS2CPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(effect)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "null_charm_effect")

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf) =
            NullCharmEffectS2CPacket(buffer.readEnum(NullCharmEffect::class.java))
    }
}
