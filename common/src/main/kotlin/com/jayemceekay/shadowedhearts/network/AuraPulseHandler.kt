package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.aura.AuraReaderCharge
import com.jayemceekay.shadowedhearts.core.ModItemComponents
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import com.jayemceekay.shadowedhearts.items.AuraReaderItem
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object AuraPulseHandler : ServerNetworkPacketHandler<AuraPulsePacket> {
    override fun handle(packet: AuraPulsePacket, server: MinecraftServer, player: ServerPlayer) {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)

        if (!auraReader.isEmpty && auraReader.item is AuraReaderItem) {
            val isActive = auraReader.get(ModItemComponents.AURA_SCANNER_ACTIVE.get()) ?: false
            if (isActive) {
                // Pulse costs some charge, say 200 ticks worth (10 seconds)
                AuraReaderCharge.consume(auraReader, 200, AuraReaderItem.MAX_CHARGE)
            }
        }
    }
}
