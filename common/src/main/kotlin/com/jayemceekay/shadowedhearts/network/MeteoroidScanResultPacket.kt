package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server -> Client: send clustered meteoroid centers.
 */
data class MeteoroidScanResultPacket(val centers: List<BlockPos>) : NetworkPacket<MeteoroidScanResultPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(centers.size)
        for (p in centers) {
            buf.writeBlockPos(p)
        }
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "meteoroid_scan_result")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): MeteoroidScanResultPacket {
            val count = buf.readVarInt()
            val list = ArrayList<BlockPos>(count)
            repeat(count) {
                list.add(buf.readBlockPos())
            }
            return MeteoroidScanResultPacket(list)
        }
    }
}
