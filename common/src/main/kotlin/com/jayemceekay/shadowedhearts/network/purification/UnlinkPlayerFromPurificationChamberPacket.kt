package com.jayemceekay.shadowedhearts.network.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Client -> Server packet to remove the player's Purification Chamber link.
 * Mirrors Cobblemon's UnlinkPlayerFromPCPacket.
 */
class UnlinkPlayerFromPurificationChamberPacket : NetworkPacket<UnlinkPlayerFromPurificationChamberPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // no payload
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            Shadowedhearts.MOD_ID,
            "unlock_player_from_purification_chamber"
        )

        @JvmStatic
        fun decode(buffer: RegistryFriendlyByteBuf) = UnlinkPlayerFromPurificationChamberPacket()
    }
}
