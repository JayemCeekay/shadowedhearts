package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderService
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object AuraTrackingStateHandler : ServerNetworkPacketHandler<AuraTrackingStateC2SPacket> {
    override fun handle(packet: AuraTrackingStateC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)
        AuraReaderService.setTracking(player, auraReader, packet.tracking)
    }
}
