package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderService
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object AuraLockHandler : ServerNetworkPacketHandler<AuraLockC2SPacket> {
    override fun handle(packet: AuraLockC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)
        when (packet.action) {
            AuraLockC2SPacket.Action.LOCK_FOCUSED -> AuraReaderService.lockFocusedTarget(player, auraReader)
            AuraLockC2SPacket.Action.CLEAR -> AuraReaderService.clearLock(player, auraReader)
        }
    }
}
