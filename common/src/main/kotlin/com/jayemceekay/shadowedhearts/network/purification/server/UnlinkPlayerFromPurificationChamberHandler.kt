package com.jayemceekay.shadowedhearts.network.purification.server

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.network.purification.UnlinkPlayerFromPurificationChamberPacket
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server handler: removes the player's Purification Chamber link on request.
 * Mirrors Cobblemon's UnlinkPlayerFromPCHandler behavior.
 */
object UnlinkPlayerFromPurificationChamberHandler : ServerNetworkPacketHandler<UnlinkPlayerFromPurificationChamberPacket> {
    override fun handle(
        packet: UnlinkPlayerFromPurificationChamberPacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        // Simply drop the link; ticker will turn OFF when no viewers remain.
        PurificationLinkManager.removeLink(player.uuid)
    }
}
