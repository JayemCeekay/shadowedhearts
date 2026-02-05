package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Client -> Server: request nearby meteoroid centers within range.
 * Server will use player's current position as center; range provided by client config for UX, but server may clamp.
 */
data class MeteoroidScanRequestPacket(val range: Int) : NetworkPacket<MeteoroidScanRequestPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(range)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "meteoroid_scan_request")

        @JvmStatic
        fun decode(buf: RegistryFriendlyByteBuf): MeteoroidScanRequestPacket {
            val range = buf.readVarInt()
            return MeteoroidScanRequestPacket(range)
        }
    }
}
