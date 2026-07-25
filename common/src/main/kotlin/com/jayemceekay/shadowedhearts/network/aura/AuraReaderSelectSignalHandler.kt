package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderService
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object AuraReaderSelectSignalHandler : ServerNetworkPacketHandler<AuraReaderSelectSignalC2SPacket> {
    override fun handle(packet: AuraReaderSelectSignalC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)
        if (!auraReader.isEmpty) {
            AuraReaderService.setMode(player, AuraReaderMode.SIGNAL_TRACKING, auraReader)
        }
    }
}
