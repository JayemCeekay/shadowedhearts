package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class AuraReaderSelectSignalC2SPacket(val direction: Direction) : NetworkPacket<AuraReaderSelectSignalC2SPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(direction)
    }

    enum class Direction {
        NEXT,
        PREVIOUS
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "aura_reader_select_signal")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): AuraReaderSelectSignalC2SPacket {
            return AuraReaderSelectSignalC2SPacket(buf.readEnum(Direction::class.java))
        }
    }
}
