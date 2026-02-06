package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.jayemceekay.shadowedhearts.ShadowPokemonData
import com.jayemceekay.shadowedhearts.aura.AuraReaderCharge
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.core.ModItemComponents
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import com.jayemceekay.shadowedhearts.items.AuraReaderItem
import com.jayemceekay.shadowedhearts.server.AuraBroadcastQueue
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

object AuraPulseHandler : ServerNetworkPacketHandler<AuraPulsePacket> {
    override fun handle(packet: AuraPulsePacket, server: MinecraftServer, player: ServerPlayer) {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)

        if (!auraReader.isEmpty && auraReader.item is AuraReaderItem) {
            val isActive = auraReader.get(ModItemComponents.AURA_SCANNER_ACTIVE.get()) ?: false
            if (isActive) {
                // Pulse costs some charge, say 200 ticks worth (10 seconds)
                AuraReaderCharge.consume(auraReader, 200, AuraReaderItem.MAX_CHARGE)

                val shadowRange = ShadowedHeartsConfigs.getInstance().shadowConfig.auraScannerShadowRange()
                val entities: List<Entity> = player.level().getEntities(null, player.boundingBox.inflate(shadowRange.toDouble()))
                for (entity in entities) {
                    if (entity is PokemonEntity && ShadowPokemonData.isShadow(entity)) {
                        // We use heightMultiplier 2.5f and sustainOverride 100 ticks (5s) to match WildShadowSpawnListener
                        // though here it might just be to ensure it shows up for the tracking duration.
                        // The client expects a 5-second delay (100 ticks) before showing the pulse/detection.
                        AuraBroadcastQueue.queueBroadcast(entity, 2.5f, 100, 100)
                    }
                }
            }
        }
    }
}
