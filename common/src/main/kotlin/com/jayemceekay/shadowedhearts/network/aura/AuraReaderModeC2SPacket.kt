package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraReaderModeC2SPacket(val mode: AuraReaderMode) : NetworkPacket<AuraReaderModeC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(mode.name)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_mode")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): AuraReaderModeC2SPacket {
            return AuraReaderModeC2SPacket(AuraReaderMode.fromName(buf.readUtf()))
        }
    }
}
