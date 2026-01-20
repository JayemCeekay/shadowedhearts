package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.core.ModItemComponents
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot

object AuraScannerHandler : ServerNetworkPacketHandler<AuraScannerC2SPacket> {
    override fun handle(packet: AuraScannerC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val helmet = player.getItemBySlot(EquipmentSlot.HEAD)
        if (!helmet.isEmpty && SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(player)) {
            helmet.set(ModItemComponents.AURA_SCANNER_ACTIVE.get(), packet.active)
        } else {
            // Check accessory slot if helmet is empty or not the reader
            val accessory = SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(player)
            if (!accessory.isEmpty && SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(player)) {
                accessory.set(ModItemComponents.AURA_SCANNER_ACTIVE.get(), packet.active)
            }
        }
    }
}
