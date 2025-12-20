package com.jayemceekay.shadowedhearts.network.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readPCPosition
import com.cobblemon.mod.common.util.writePCPosition
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

/**
 * Client -> Server: move or swap from PurificationChamber store [setIndex] at [fromIndex]
 * (0 center, 1..4 supports) into the player's linked PC at [pcPosition].
 * If the destination PC slot is occupied, perform a swap.
 */
class MovePurificationToPCPacket(
    val purificationStoreID: UUID,
    val fromIndex: Int,
    val setIndex: Int,
    val pcPosition: PCPosition
) : NetworkPacket<MovePurificationToPCPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(purificationStoreID)
        buffer.writeInt(fromIndex)
        buffer.writeInt(setIndex)
        buffer.writePCPosition(pcPosition)
    }

    companion object {
        val ID = cobblemonResource("shadowedhearts/move_purification_to_pc")
        fun decode(buffer: RegistryFriendlyByteBuf) = MovePurificationToPCPacket(
            buffer.readUUID(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readPCPosition()
        )
    }
}
