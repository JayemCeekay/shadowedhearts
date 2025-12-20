package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.network.purification.MovePurificationToPCPacket
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberPosition
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: moves or swaps a Pokémon from the player's PurificationChamberStore (specified setIndex) to their linked PC.
 */
object MovePurificationToPCHandler : ServerNetworkPacketHandler<MovePurificationToPCPacket> {
    override fun handle(packet: MovePurificationToPCPacket, server: MinecraftServer, player: ServerPlayer) {
        // Obtain the player's PC directly from storage; the player may not be linked to a PC block
        val pc = Cobblemon.storage.getPC(player)

        val registryAccess = player.registryAccess()
        val purification = Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, packet.purificationStoreID, registryAccess)
            ?: run {
                return
            }

        val isShadow = packet.fromIndex == 0
        val supportIdx = (packet.fromIndex - 1).coerceAtLeast(0)
        val from = PurificationChamberPosition(setIndex = packet.setIndex, index = if (isShadow) 0 else supportIdx, isShadow = isShadow)

        val fromMon = purification[from] ?: return
        val destMon = pc[packet.pcPosition]

        if (destMon == null) {
            // Simple move: remove from Purification FIRST (so storeCoordinates still match), then place into PC
            val removed = purification.remove(from)
            if (!removed) {
                return
            }
            pc[packet.pcPosition] = fromMon
        } else {
            // Swap if compatible with the source slot type
            val destIsShadow = PokemonAspectUtil.hasShadowAspect(destMon)
            if (from.isShadow == destIsShadow) {
                // Place the destination mon into Purification FIRST so removal of the existing occupant (fromMon) succeeds
                purification[from] = destMon
                pc[packet.pcPosition] = fromMon
            } else {
                // Incompatible for swap; do nothing
                return
            }
        }
    }
}
