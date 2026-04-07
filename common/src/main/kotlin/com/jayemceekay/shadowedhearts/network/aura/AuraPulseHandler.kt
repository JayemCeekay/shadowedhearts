package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderCharge
import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonData
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalPoolRegistry
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSpeciesTrait
import com.jayemceekay.shadowedhearts.common.tracking.TrailManager
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.content.items.AuraReaderItem
import com.jayemceekay.shadowedhearts.content.items.ShadowSignalDataItem
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import com.jayemceekay.shadowedhearts.network.AuraBroadcastQueue
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import com.jayemceekay.shadowedhearts.network.trail.TrailSyncS2CPacket
import com.jayemceekay.shadowedhearts.registry.util.ModItemComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object AuraPulseHandler : ServerNetworkPacketHandler<AuraPulsePacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: AuraPulsePacket, server: MinecraftServer, player: ServerPlayer) {
        LOG.debug("[ShadowHunt] AuraPulse received from player={} with slotIndex={}", player.name.string, packet.slotIndex)
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)

        if (!auraReader.isEmpty && auraReader.item is AuraReaderItem) {
            val isActive = auraReader.get(ModItemComponents.AURA_SCANNER_ACTIVE.get()) ?: false
            if (isActive) {
                val stack = if (packet.slotIndex >= 0) {
                    player.inventory.getItem(packet.slotIndex)
                } else {
                    findSignalData(player)?.third?.let { player.inventory.getItem(it) } ?: ItemStack.EMPTY
                }

                if (!stack.isEmpty && stack.item is ShadowSignalDataItem) {
                    triggerPulse(player, stack, server)
                } else {
                    LOG.debug("[ShadowHunt] No valid Shadow Signal Data found.")
                }
            }
        }
    }

    fun triggerPulse(player: ServerPlayer, stack: ItemStack, server: MinecraftServer): Boolean {
        val auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(player)
        if (auraReader.isEmpty || auraReader.item !is AuraReaderItem) return false

        val isActive = auraReader.get(ModItemComponents.AURA_SCANNER_ACTIVE.get()) ?: false
        if (!isActive) return false

        // Pulse costs some charge, say 200 ticks worth (10 seconds)
        AuraReaderCharge.consume(auraReader, 200, AuraReaderItem.MAX_CHARGE)

        val tier = ShadowSignalDataItem.getTierFromStack(stack)
        val seed = ShadowSignalDataItem.getEffectiveSeed(stack)

        // Coordinate and radius validation: check if the player is in the correct region
        if (ShadowSignalDataItem.hasCoords(stack)) {
            val x = ShadowSignalDataItem.getX(stack)
            val y = ShadowSignalDataItem.getY(stack)
            val z = ShadowSignalDataItem.getZ(stack)
            val radius = ShadowSignalDataItem.getRadius(stack)
            val playerPos = player.position()
            val distance = Math.sqrt(Math.pow(playerPos.x - x, 2.0) + Math.pow(playerPos.y - y, 2.0) + Math.pow(playerPos.z - z, 2.0))

            if (distance > radius) {
                LOG.debug("[ShadowHunt] Range mismatch: target=({},{},{}), player=({},{},{}), radius={}, distance={}", x, y, z, playerPos.x, playerPos.y, playerPos.z, radius, distance)
                player.sendSystemMessage(
                    Component.translatable("message.shadowedhearts.hunt.wrong_region", String.format("%d, %d, %d", x, y, z))
                        .withStyle(ChatFormatting.RED)
                )
                return false
            }
            LOG.debug("[ShadowHunt] Range validated: distance={}", distance)
        } else {
            val origin = ShadowSignalDataItem.getOrigin(stack)
            if (origin.isNotEmpty()) {
                val playerBiome = player.serverLevel().getBiome(player.blockPosition())
                val biomeKey = playerBiome.unwrapKey().map { it.location().toString() }.orElse("")
                val playerDimension = player.serverLevel().dimension().location().toString()
                // Accept if origin matches biome key or dimension key
                if (origin != biomeKey && origin != playerDimension) {
                    LOG.debug("[ShadowHunt] Region mismatch: origin={}, playerBiome={}, playerDim={}", origin, biomeKey, playerDimension)
                    player.sendSystemMessage(
                        Component.translatable("message.shadowedhearts.hunt.wrong_region", origin)
                            .withStyle(ChatFormatting.RED)
                    )
                    return false
                }
                LOG.debug("[ShadowHunt] Region validated: origin={}, playerBiome={}", origin, biomeKey)
            }
        }

        // Pre-resolve species trait from the rarity pool for hunt layout generation
        val traitRng = java.util.Random(seed)
        val previewSpecies = ShadowSignalPoolRegistry.selectFromPool(tier.rarityPool, traitRng)
        val speciesTrait = if (previewSpecies != null) ShadowSpeciesTrait.resolve(previewSpecies) else ShadowSpeciesTrait.NEUTRAL

        // Start/restart a trail session using tier-aware generation with species trait
        LOG.debug("[ShadowHunt] Starting hunt: tier={}, seed={}, trait={}, player={}", tier, seed, speciesTrait, player.name.string)
        val session = TrailManager.startOrReset(player, tier, seed, speciesTrait)

        // Consume the signal data item (one use)
        stack.shrink(1)

        // After a few seconds, reveal the trail and send the message
        val executor = CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS)
        CompletableFuture.runAsync({
            server.execute {
                val tierMessage = when (tier) {
                    ShadowSignalTier.FAINT -> "faint"
                    ShadowSignalTier.WEAK -> "weak"
                    ShadowSignalTier.MODERATE -> "moderate"
                    ShadowSignalTier.STRONG -> "strong"
                    ShadowSignalTier.RESONANT -> "resonant"
                }
                player.sendSystemMessage(
                    Component.literal("The Aura Reader locks onto a $tierMessage shadow signal. A Shadow Pokemon is near!")
                        .withStyle(ChatFormatting.DARK_PURPLE)
                )
                val hotspot = session.advanceToNextHotspot(2.5f)
                val nodes = session.nodes.subList(session.index, session.nodes.size).map { it.pos() }
                val eventTypes = session.nodes.subList(session.index, session.nodes.size).map { it.eventType() }
                LOG.debug("[ShadowHunt] Trail revealed: hotspot={}, nodeCount={}, eventTypes={}, index={}",
                    hotspot?.pos(), nodes.size, eventTypes, session.index)
                ShadowedHeartsNetwork.sendToPlayer(
                    player,
                    TrailSyncS2CPacket(
                        nodes = nodes,
                        hotspot = hotspot?.pos(),
                        tier = tier,
                        eventTypes = eventTypes,
                        tension = session.tension,
                        trailQuality = session.trailQuality,
                        currentNodeIndex = session.index,
                        huntSeed = session.huntSeed,
                        falseTrails = session.falseTrails,
                        speciesTraitId = session.speciesTrait.toId()
                    )
                )
            }
        }, executor)

        // Maintain legacy: also ping nearby shadow entities with a delayed aura pulse for compatibility
        val shadowRange = ShadowedHeartsConfigs.getInstance().shadowConfig.auraScannerShadowRange()
        val entities: List<Entity> = player.level().getEntities(null, player.boundingBox.inflate(shadowRange.toDouble()))
        for (entity in entities) {
            if (entity is PokemonEntity && ShadowPokemonData.isShadow(entity)) {
                AuraBroadcastQueue.queueBroadcast(entity, 2.5f, 100, 100)
            }
        }
        return true
    }

    /**
     * Search the player's inventory for a Shadow Signal Data item.
     * Returns a Triple of (tier, seed, inventorySlotIndex), or null if none found.
     * Prefers higher-tier signals if the player has multiple.
     */
    private fun findSignalData(player: ServerPlayer): Triple<ShadowSignalTier, Long, Int>? {
        var bestTier: ShadowSignalTier? = null
        var bestSeed: Long = 0
        var bestSlot: Int = -1

        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item is ShadowSignalDataItem) {
                val item = stack.item as ShadowSignalDataItem
                val tier = item.tier
                if (bestTier == null || tier.tier > bestTier.tier) {
                    bestTier = tier
                    bestSeed = ShadowSignalDataItem.getEffectiveSeed(stack)
                    bestSlot = i
                }
            }
        }

        return if (bestTier != null && bestSlot >= 0) {
            Triple(bestTier, bestSeed, bestSlot)
        } else {
            null
        }
    }
}
