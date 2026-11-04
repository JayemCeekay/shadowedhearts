package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.network.purification.MovePCToPurificationPacket
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: moves a Pokémon from the player's linked PC to their PurificationChamberStore.
 * Maps targetIndex: 0 -> shadow slot, 1..4 -> support slots 0..3, within provided setIndex.
 */
object MovePCToPurificationHandler : ServerNetworkPacketHandler<MovePCToPurificationPacket> {
    override fun handle(packet: MovePCToPurificationPacket, server: MinecraftServer, player: ServerPlayer) {
        val target = PurificationTransfers.position(
            player.uuid, packet.purificationStoreID, packet.setIndex, packet.targetIndex
        ) ?: return
        val purification = PurificationLinkManager.getStore(player) ?: return
        // Use the player's PC store directly; do not rely on PCLinkManager link from a PC block
        val pc = Cobblemon.storage.getPC(player)

        val moved = PurificationTransfers.transfer(
            source = PurificationTransfers.slot(pc, packet.pcPosition),
            destination = PurificationTransfers.slot(purification, target) {
                target.isShadow == ShadowAspectUtil.hasShadowAspect(it)
            },
            allowSwap = false,
            expectedSource = { it.uuid == packet.pokemonID }
        )
        if (moved) purification.sendTo(player)
    }
}
