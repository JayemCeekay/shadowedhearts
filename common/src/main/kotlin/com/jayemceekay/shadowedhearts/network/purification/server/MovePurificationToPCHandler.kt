package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.network.purification.MovePurificationToPCPacket
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: moves or swaps a Pokémon from the player's PurificationChamberStore (specified setIndex) to their linked PC.
 */
object MovePurificationToPCHandler : ServerNetworkPacketHandler<MovePurificationToPCPacket> {
    override fun handle(packet: MovePurificationToPCPacket, server: MinecraftServer, player: ServerPlayer) {
        val from = PurificationTransfers.position(
            player.uuid, packet.purificationStoreID, packet.setIndex, packet.fromIndex
        ) ?: return
        val purification = PurificationLinkManager.getStore(player) ?: return
        // Obtain the player's PC directly from storage; the player may not be linked to a PC block
        val pc = Cobblemon.storage.getPC(player)

        val moved = PurificationTransfers.transfer(
            source = PurificationTransfers.slot(purification, from) {
                from.isShadow == ShadowAspectUtil.hasShadowAspect(it)
            },
            destination = PurificationTransfers.slot(pc, packet.pcPosition),
            allowSwap = true
        )
        if (moved) purification.sendTo(player)
    }
}
