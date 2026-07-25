package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderService
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object AuraReaderModeHandler : ServerNetworkPacketHandler<AuraReaderModeC2SPacket> {
    override fun handle(packet: AuraReaderModeC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)
        if (!auraReader.isEmpty) {
            AuraReaderService.setMode(player, packet.mode, auraReader)
        }
    }
}
