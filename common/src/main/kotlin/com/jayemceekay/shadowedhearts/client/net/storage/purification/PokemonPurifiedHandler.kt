package com.jayemceekay.shadowedhearts.client.net.storage.purification

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorageManager
import com.jayemceekay.shadowedhearts.network.purification.PokemonPurifiedPacket
import net.minecraft.client.Minecraft

/**
 * Client-side handler for PokemonPurifiedPacket.
 * Updates the local Pokemon's aspect so the UI reflects the purification immediately.
 */
object PokemonPurifiedHandler : ClientNetworkPacketHandler<PokemonPurifiedPacket> {
    override fun handle(packet: PokemonPurifiedPacket, client: Minecraft) {
        val storage = ClientPurificationStorageManager[packet.purificationStoreID] ?: return
        val pokemon = storage.getAt(packet.setIndex, ClientPurificationStorage.PurificationPosition(packet.slotIndex)) ?: return

        // Update the local Pokemon instance to remove shadow aspect
        ShadowAspectUtil.setShadowAspect(pokemon, false)
        storage.setAt(packet.setIndex, ClientPurificationStorage.PurificationPosition(packet.slotIndex), pokemon)
    }
}
