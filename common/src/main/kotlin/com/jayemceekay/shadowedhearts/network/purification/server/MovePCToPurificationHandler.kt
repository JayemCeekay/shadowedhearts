package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.network.purification.MovePCToPurificationPacket
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberPosition
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: moves a Pokémon from the player's linked PC to their PurificationChamberStore.
 * Maps targetIndex: 0 -> shadow slot, 1..4 -> support slots 0..3, within provided setIndex.
 */
object MovePCToPurificationHandler : ServerNetworkPacketHandler<MovePCToPurificationPacket> {
    override fun handle(packet: MovePCToPurificationPacket, server: MinecraftServer, player: ServerPlayer) {
        // Use the player's PC store directly; do not rely on PCLinkManager link from a PC block
        val pc = Cobblemon.storage.getPC(player)

        val registryAccess = player.registryAccess()
        var purification = Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, packet.purificationStoreID, registryAccess)
        if (purification == null) {
            // Fallback: try resolving by the player's UUID (expected to match)
            purification = Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, player.uuid, registryAccess)
        }
        if (purification == null) {
            return
        }

        val pos: PCPosition = packet.pcPosition
        val pokemon = pc[pos] ?: run {
            return
        }
        if (pokemon.uuid != packet.pokemonID) {
            return
        }

        // Determine target position inside PurificationChamberStore
        val isShadow = packet.targetIndex == 0
        val supportIdx = (packet.targetIndex - 1).coerceAtLeast(0)
        val target = PurificationChamberPosition(setIndex = packet.setIndex, index = if (isShadow) 0 else supportIdx, isShadow = isShadow)

        // Validate compatibility BEFORE removing from PC
        val monIsShadow = PokemonAspectUtil.hasShadowAspect(pokemon)
        if (isShadow && !monIsShadow) {
            return
        }
        if (!isShadow && monIsShadow) {
            return
        }

        // Slot must be empty and type must be compatible; set() checks type compatibility.
        if (purification[target] != null) {
            return
        }

        // Perform move: remove from PC first so storeCoordinates clear, then place into Purification
        val removed = pc.remove(pos)
        if (!removed) {
            return
        }
        purification[target] = pokemon

        // Optionally we could send a client sync for Purification; for MVP we rely on UI optimistic update.
    }
}
