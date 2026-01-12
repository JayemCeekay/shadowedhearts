package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

data class RelicStoneMotePacket(
    val pos: BlockPos
) : NetworkPacket<RelicStoneMotePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
    }

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "relic_stone_mote")

        fun decode(buf: RegistryFriendlyByteBuf): RelicStoneMotePacket {
            return RelicStoneMotePacket(
                buf.readBlockPos()
            )
        }
    }
}
