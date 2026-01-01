package com.jayemceekay.shadowedhearts.network.purification

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.*

/**
 * Client -> Server: requests to fully purify the Pokémon at the center of the purification chamber.
 */
class PurifyPokemonPacket(
    val purificationStoreID: UUID,
    val setIndex: Int
) : NetworkPacket<PurifyPokemonPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(purificationStoreID)
        buffer.writeInt(setIndex)
    }

    companion object {
        val ID = cobblemonResource("shadowedhearts/purify_pokemon")
        fun decode(buffer: RegistryFriendlyByteBuf) = PurifyPokemonPacket(
            buffer.readUUID(),
            buffer.readInt()
        )
    }
}
