package com.jayemceekay.shadowedhearts.network.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

/**
 * Server -> Client: Informs the client that a Pokémon in the purification chamber has been purified.
 */
class PokemonPurifiedPacket(
    val purificationStoreID: UUID,
    val setIndex: Int,
    val slotIndex: Int
) : NetworkPacket<PokemonPurifiedPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(purificationStoreID)
        buffer.writeInt(setIndex)
        buffer.writeInt(slotIndex)
    }

    companion object {
        val ID = cobblemonResource("shadowedhearts/pokemon_purified")
        fun decode(buffer: RegistryFriendlyByteBuf) = PokemonPurifiedPacket(
            buffer.readUUID(),
            buffer.readInt(),
            buffer.readInt()
        )
    }
}
