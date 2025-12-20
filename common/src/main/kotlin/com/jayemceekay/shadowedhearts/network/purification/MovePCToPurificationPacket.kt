package com.jayemceekay.shadowedhearts.network.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readPCPosition
import com.cobblemon.mod.common.util.writePCPosition
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

/**
 * Client -> Server: move a Pokémon from the player's PC (at [pcPosition])
 * into their PurificationChamber store [setIndex] at [targetIndex]
 * (0 center, 1..4 supports), for a specific purification store [purificationStoreID].
 */
class MovePCToPurificationPacket(
    val pokemonID: UUID,
    val pcPosition: PCPosition,
    val purificationStoreID: UUID,
    val targetIndex: Int,
    val setIndex: Int
) : NetworkPacket<MovePCToPurificationPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(pokemonID)
        buffer.writePCPosition(pcPosition)
        buffer.writeUUID(purificationStoreID)
        buffer.writeInt(targetIndex)
        buffer.writeInt(setIndex)
    }

    companion object {
        val ID = cobblemonResource("shadowedhearts/move_pc_to_purification")
        fun decode(buffer: RegistryFriendlyByteBuf) = MovePCToPurificationPacket(
            buffer.readUUID(),
            buffer.readPCPosition(),
            buffer.readUUID(),
            buffer.readInt(),
            buffer.readInt()
        )
    }
}
