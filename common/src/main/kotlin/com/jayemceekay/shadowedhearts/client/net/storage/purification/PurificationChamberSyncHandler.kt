package com.jayemceekay.shadowedhearts.client.net.storage.purification

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorage
import com.jayemceekay.shadowedhearts.client.storage.ClientPurificationStorageManager
import net.minecraft.client.Minecraft

/**
 * Applies a full snapshot of a PurificationChamberStore to the client-side storage for UI rendering.
 */
object PurificationChamberSyncHandler : ClientNetworkPacketHandler<PurificationChamberSyncPacket> {
    override fun handle(packet: PurificationChamberSyncPacket, client: Minecraft) {
        val storage = ClientPurificationStorageManager.getOrCreate(packet.storeID)
        storage.clearAll()

        val reg = client.level?.registryAccess() ?: return

        packet.entries.forEach { entry ->
            try {
                val mon = Pokemon.loadFromNBT(reg, entry.pokemonNbt)
                storage.setAt(
                    entry.setIndex,
                    ClientPurificationStorage.PurificationPosition(entry.slotIndex),
                    mon
                )
            } catch (t: Throwable) {
            }
        }
    }
}
