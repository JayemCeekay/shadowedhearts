package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventType
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier
import com.jayemceekay.shadowedhearts.common.tracking.TrailManager
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import com.jayemceekay.shadowedhearts.registry.ModItems
import com.jayemceekay.shadowedhearts.registry.ModSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Progresses the player's trail session when they complete a hotspot scan.
 * Handles node event type branching, tier-aware encounter generation,
 * and contextual final manifestation spawning.
 */
object EvidenceScanCompleteHandler : ServerNetworkPacketHandler<EvidenceScanCompleteC2SPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: EvidenceScanCompleteC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val sessionOpt = TrailManager.get(player.uuid)
        if (sessionOpt.isEmpty) {
            LOG.debug("[ShadowHunt] EvidenceScan: no session for player={}", player.name.string)
            return
        }
        val session = sessionOpt.get()
        val hotspot = session.currentHotspot ?: run {
            LOG.debug("[ShadowHunt] EvidenceScan: no hotspot for player={}", player.name.string)
            return
        }

        // Block scanning entirely when an interactive state is already active
        // This prevents re-scanning the node to skip the event
        val activeState = session.state
        if (activeState == com.jayemceekay.shadowedhearts.common.tracking.TrailSession.State.NODE_EVENT_ACTIVE
            || activeState == com.jayemceekay.shadowedhearts.common.tracking.TrailSession.State.CALIBRATION_ACTIVE
            || activeState == com.jayemceekay.shadowedhearts.common.tracking.TrailSession.State.MANIFESTATION_BUILDUP
            || activeState == com.jayemceekay.shadowedhearts.common.tracking.TrailSession.State.ENCOUNTER_ACTIVE) {
            LOG.debug("[ShadowHunt] EvidenceScan blocked: player={} state={} (event in progress)",
                player.name.string, activeState)
            return
        }

        val playerPos = player.blockPosition()
        val dx = (playerPos.x + 0.5) - (hotspot.pos().x + 0.5)
        val dy = (playerPos.y + 0.5) - (hotspot.pos().y + 0.5)
        val dz = (playerPos.z + 0.5) - (hotspot.pos().z + 0.5)
        val within = (dx * dx + dy * dy + dz * dz) <= (hotspot.radius() * hotspot.radius())
        if (!within) return

        // Get the current node's event type for branching behavior
        val eventType = session.currentEventType
        LOG.debug("[ShadowHunt] EvidenceScan: player={}, index={}, eventType={}, state={}, within={}",
            player.name.string, session.index, eventType, session.state, within)

        // For interactive event types, initiate the interactive phase
        if (eventType.isInteractive) {
            val rng = java.util.Random(session.huntSeed + session.index)
            val biome = com.jayemceekay.shadowedhearts.common.tracking.BiomeHuntFlavor.categorize(player.serverLevel(), player.blockPosition())
            LOG.debug("[ShadowHunt] Biome category for node event: {}", biome)
            val nodeEvent = session.beginNodeEvent(rng, biome)
            if (nodeEvent != null) {
                LOG.debug("[ShadowHunt] Interactive event started: type={}, phase={}, clues={}",
                    eventType, nodeEvent.phase, nodeEvent.cluePositions.size)
                // Play node scan start sound
                player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_NODE_SCAN.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
                // Play event-specific start sound
                when (eventType) {
                    NodeEventType.WILD_INTERRUPTION ->
                        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_WILD_AGGRO.get(), SoundSource.PLAYERS, 0.7f, 1.0f)
                    NodeEventType.PROVOCATION ->
                        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_PROVOCATION_BUILDUP.get(), SoundSource.PLAYERS, 0.5f, 0.8f)
                    else -> {}
                }
                // Send node event state to client
                syncNodeEventToClient(player, nodeEvent, session)
                return
            }
        }

        // Process node event type-specific logic (non-interactive or fallback)
        processNodeEvent(eventType, player, session)

        // Play node scan sound for non-interactive nodes
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_NODE_SCAN.get(), SoundSource.PLAYERS, 0.8f, 1.0f)

        // advance
        session.markScanned()
        LOG.debug("[ShadowHunt] Node scanned: index={}, hasMore={}, tension={}, quality={}",
            session.index, session.hasMore(), session.tension, session.trailQuality)
        if (session.hasMore()) {
            // Reward drop: chance scales with tier
            val dropChance = 0.15f + session.tier.tier * 0.05f
            if (Random.nextFloat() < dropChance) {
                dropNodeReward(player, session.tier)
            }

            val next = session.advanceToNextHotspot(2.5f)
            // Send only the NEXT segment's nodes (segment-by-segment reveal)
            val nextSegmentEnd = min(session.index + 2, session.nodes.size)
            val segmentNodes = session.nodes.subList(session.index, nextSegmentEnd).map { it.pos() }
            val segmentEventTypes = session.nodes.subList(session.index, nextSegmentEnd).map { it.eventType() }
            // Play trail reveal sound
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_TRAIL_REVEAL.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
            ShadowedHeartsNetwork.sendToPlayer(player, TrailSyncS2CPacket(
                nodes = segmentNodes,
                hotspot = next?.pos(),
                tier = session.tier,
                eventTypes = segmentEventTypes,
                tension = session.tension,
                trailQuality = session.trailQuality,
                currentNodeIndex = session.index,
                huntSeed = session.huntSeed,
                falseTrails = session.falseTrails,
                speciesTraitId = session.speciesTrait.toId()
            ))
        } else {
            // Final manifestation: spawn a tier-aware shadow pokemon at a contextual location
            LOG.debug("[ShadowHunt] All nodes complete — beginning manifestation sequence for player={}", player.name.string)
            beginManifestationSequence(player, session)
        }
    }

    /**
     * Send node event state to the client for HUD rendering.
     */
    private fun syncNodeEventToClient(
        player: ServerPlayer,
        event: com.jayemceekay.shadowedhearts.common.tracking.NodeEventState,
        session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession
    ) {
        ShadowedHeartsNetwork.sendToPlayer(player, NodeEventSyncS2CPacket(
            eventType = event.eventType,
            phase = event.phase.ordinal,
            ticksElapsed = event.ticksElapsed,
            maxTicks = event.maxTicks,
            cluePositions = event.cluePositions,
            wrongGuesses = event.wrongGuesses,
            requiredValidCount = event.requiredValidCount,
            foundValidCount = event.foundValidCount,
            selectedClueIndices = event.selectedClueIndices,
            searchCenter = session.currentHotspot?.pos(),
            searchRadius = event.searchRadius,
            searchSignalStrength = 0f,
            wildResolveTimer = event.wildResolveTimer,
            wildsResolved = event.isWildsResolved,
            signalBuildup = event.signalBuildup,
            provocationRequiredTicks = event.provocationRequiredTicks,
            clueDescriptions = event.clueDescriptions,
            searchHint = event.searchHint
        ))
    }

    /**
     * Process node event type-specific server-side logic.
     * Applies outcome branching: tension, quality, and concrete gameplay consequences.
     * Species trait modifiers are applied to tension and trail quality changes.
     */
    private fun processNodeEvent(eventType: NodeEventType, player: ServerPlayer, session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession) {
        val trait = session.speciesTrait
        when (eventType) {
            NodeEventType.CALIBRATION -> {
                // Calibration grade already applied — apply concrete branching consequences
                applyCalibrationBranching(session, player)
            }
            NodeEventType.EVIDENCE_INTERPRETATION -> {
                // Investigative node — slight tension reduction on success
                session.addTension(-0.05f + trait.extraTensionPerNode)
                session.trailQuality = max(0f, min(1f, session.trailQuality + trait.trailQualityModifier))
                session.lastNodeGrade = "STANDARD"
            }
            NodeEventType.WILD_INTERRUPTION -> {
                // Combat node — increases tension more than normal
                session.addTension(0.08f + trait.extraTensionPerNode)
                if (Random.nextFloat() < 0.3f) {
                    session.trailQuality = max(0f, session.trailQuality - 0.1f + trait.trailQualityModifier)
                }
                session.lastNodeGrade = "STANDARD"
            }
            NodeEventType.ENVIRONMENTAL_SEARCH -> {
                // Search node — neutral tension, slight quality bonus
                session.addTension(trait.extraTensionPerNode)
                session.trailQuality = min(1f, session.trailQuality + 0.05f + trait.trailQualityModifier)
                session.lastNodeGrade = "STANDARD"
            }
            NodeEventType.PROVOCATION -> {
                // Provocation — high tension increase
                session.addTension(0.12f + trait.extraTensionPerNode)
                session.trailQuality = max(0f, min(1f, session.trailQuality + trait.trailQualityModifier))
                session.lastNodeGrade = "STANDARD"
            }
            NodeEventType.FINAL_MANIFESTATION -> {
                // Will be handled in beginManifestationSequence
            }
        }
    }

    /**
     * Apply concrete gameplay consequences based on calibration grade.
     */
    private fun applyCalibrationBranching(session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession, player: ServerPlayer) {
        val grade = session.lastCalibrationGrade ?: return
        session.lastNodeGrade = grade.name
        when (grade) {
            com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.PERFECT -> {
                // Strong success: shorter search radius on next search event, cleaner trail
                session.setNextSearchRadiusModifier(0.7f)
                session.trailQuality = min(1f, session.trailQuality + 0.1f)
            }
            com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.STANDARD -> {
                // Normal: reset modifier
                session.setNextSearchRadiusModifier(1.0f)
            }
            com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.SLOPPY -> {
                // Sloppy: extra noise, wider search radius, chance of false trails
                session.setNextSearchRadiusModifier(1.3f)
                session.addTension(0.05f)
                val falseChance = 0.5f * session.speciesTrait.falseTrailChanceMultiplier
                if (Random.nextFloat() < falseChance) {
                    generateFalseTrails(player, session, 1)
                    LOG.debug("[ShadowHunt] Sloppy calibration spawned 1 false trail")
                }
            }
            com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.FAILED -> {
                // Failure: temporary signal blackout + wider search radius + false trails
                session.beginSignalBlackout(60) // 3 seconds blackout
                session.setNextSearchRadiusModifier(1.5f)
                val falseCount = if (session.speciesTrait == com.jayemceekay.shadowedhearts.common.tracking.ShadowSpeciesTrait.CUNNING) 3 else 2
                generateFalseTrails(player, session, falseCount)
                LOG.debug("[ShadowHunt] Failed calibration spawned {} false trails", falseCount)
                player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_SIGNAL_BLACKOUT.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
            }
        }
    }

    /**
     * Drop reward items at the player's location.
     * Higher tiers have better drops. Includes shadow shards, crafting materials,
     * and other thematic rewards from the design doc.
     */
    private fun dropNodeReward(player: ServerPlayer, tier: ShadowSignalTier) {
        val level = player.serverLevel()
        val dropPos = player.blockPosition()
        val rewards = mutableListOf<net.minecraft.world.item.ItemStack>()

        // Primary reward: shadow shards (thematic crafting material)
        val shardChance = 0.3f + tier.tier * 0.1f // 40%–80%
        if (Random.nextFloat() < shardChance) {
            val shardCount = 1 + Random.nextInt(tier.tier)
            rewards.add(net.minecraft.world.item.ItemStack(ModItems.SHADOW_SHARD.get(), shardCount))
        }

        // Secondary reward: tier-scaled materials
        when {
            tier.tier >= 5 && Random.nextFloat() < 0.25f -> {
                // Resonant: rare drops
                rewards.add(net.minecraft.world.item.ItemStack(Items.DIAMOND, 1))
                if (Random.nextFloat() < 0.3f) {
                    rewards.add(net.minecraft.world.item.ItemStack(Items.ECHO_SHARD, 1))
                }
            }
            tier.tier >= 4 && Random.nextFloat() < 0.3f -> {
                rewards.add(net.minecraft.world.item.ItemStack(Items.DIAMOND, 1))
            }
            tier.tier >= 3 && Random.nextFloat() < 0.4f -> {
                rewards.add(net.minecraft.world.item.ItemStack(Items.AMETHYST_SHARD, Random.nextInt(1, 4)))
            }
            tier.tier >= 2 && Random.nextFloat() < 0.5f -> {
                rewards.add(net.minecraft.world.item.ItemStack(Items.GLOWSTONE_DUST, Random.nextInt(2, 5)))
            }
        }

        // Tertiary: small chance of experience bottles (scanner bonus feel)
        if (Random.nextFloat() < 0.15f * tier.tier) {
            rewards.add(net.minecraft.world.item.ItemStack(Items.EXPERIENCE_BOTTLE, 1 + Random.nextInt(tier.tier)))
        }

        // Fallback: always drop at least something
        if (rewards.isEmpty()) {
            rewards.add(when (Random.nextInt(4)) {
                0 -> net.minecraft.world.item.ItemStack(Items.GLOWSTONE_DUST, 2)
                1 -> net.minecraft.world.item.ItemStack(Items.AMETHYST_SHARD, 1)
                2 -> net.minecraft.world.item.ItemStack(Items.QUARTZ, 1)
                else -> net.minecraft.world.item.ItemStack(ModItems.SHADOW_SHARD.get(), 1)
            })
        }

        // Drop all rewards
        for (stack in rewards) {
            val itemEntity = ItemEntity(level, dropPos.x + 0.5, dropPos.y + 1.0, dropPos.z + 0.5, stack)
            level.addFreshEntity(itemEntity)
        }
    }

    /**
     * Generate false/decoy trail branches near the player's current position.
     * These trails lead in wrong directions to confuse the player.
     * The false trails are stored in the session and synced to the client.
     */
    private fun generateFalseTrails(player: ServerPlayer, session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession, count: Int) {
        session.clearFalseTrails()
        val playerPos = player.blockPosition()
        val rng = java.util.Random(session.huntSeed + session.index + 777)

        for (i in 0 until count) {
            val angle = rng.nextDouble() * Math.PI * 2.0
            val trailPoints = mutableListOf<BlockPos>()
            var cursor = playerPos
            // Generate 4-8 waypoints in a random direction
            val waypointCount = 4 + rng.nextInt(5)
            for (j in 0 until waypointCount) {
                val dist = 8 + rng.nextInt(12)
                // Add some angular variation per step
                val stepAngle = angle + (rng.nextDouble() - 0.5) * 0.8
                val dx = (Math.cos(stepAngle) * dist).toInt()
                val dz = (Math.sin(stepAngle) * dist).toInt()
                val candidate = cursor.offset(dx, 0, dz)
                // Find surface
                val y = player.serverLevel().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    candidate.x, candidate.z
                )
                val surfacePos = BlockPos(candidate.x, maxOf(y + 1, player.serverLevel().minBuildHeight + 1), candidate.z)
                trailPoints.add(surfacePos)
                cursor = surfacePos
            }
            session.addFalseTrail(trailPoints)
        }
        // Set cooldown for false trail display (auto-clear after ~5 seconds)
        session.setFalseTrailCooldownTicks(100)
    }

    /**
     * Begin the dramatic manifestation buildup sequence instead of spawning immediately.
     * The buildup is ticked by NodeEventTickHandler and spawns after the sequence completes.
     */
    fun beginManifestationSequence(player: ServerPlayer, session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession) {
        session.beginManifestationBuildup()
        // Play buildup start sound
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_MANIFESTATION_BUILDUP.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
        // Send initial manifestation sync to client
        ShadowedHeartsNetwork.sendToPlayer(player, ManifestationSyncS2CPacket(
            phase = session.manifestationPhase,
            progress = session.manifestationProgress,
            convergenceTarget = session.currentHotspot?.pos(),
            tension = session.tension
        ))
    }

    /**
     * Spawn the final Shadow Pokémon using tier-aware encounter generation
     * at a contextual location near the final hotspot.
     */
    fun spawnFinalManifestation(player: ServerPlayer, session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession) {
        val level = player.serverLevel()
        val tier = session.tier
        val rng = java.util.Random(session.huntSeed + session.index)

        // --- Tier-aware species selection ---
        val species = selectSpeciesForTier(tier, rng)

        // --- Tier-aware level ---
        val encounterLevel = tier.minEncounterLevel + rng.nextInt(max(1, tier.maxEncounterLevel - tier.minEncounterLevel + 1))

        val properties = PokemonProperties.parse("species=${species.resourceIdentifier} level=$encounterLevel")
        val entity = properties.createEntity(level)
        val pokemon = entity.pokemon

        // --- Tier-aware IVs: guarantee some perfect IVs ---
        applyGuaranteedPerfectIVs(pokemon, tier, rng)

        // --- Tier-aware shiny chance ---
        if (tier.shinyChanceBonus > 0 && rng.nextDouble() < tier.shinyChanceBonus) {
            pokemon.shiny = true
        }

        // --- Contextual spawn location near the final hotspot ---
        val spawnAt = findContextualSpawnPos(level, player, session)

        entity.moveTo(spawnAt.x + 0.5, spawnAt.y + 1.0, spawnAt.z + 0.5, player.yBodyRot + 180f, 0f)

        ShadowService.setShadow(pokemon, entity, true)
        ShadowService.setHeartGauge(pokemon, entity, HeartGaugeConfig.getMax(pokemon))
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon)
        ShadowMoveUtil.assignShadowMoves(pokemon)

        level.addFreshEntity(entity)

        TrailManager.clear(player.uuid)
        // Clear trail display on client
        ShadowedHeartsNetwork.sendToPlayer(player, TrailSyncS2CPacket(
            nodes = emptyList(),
            hotspot = null,
            tier = session.tier,
            eventTypes = emptyList(),
            tension = session.tension,
            trailQuality = session.trailQuality,
            currentNodeIndex = session.index,
            huntSeed = session.huntSeed,
            falseTrails = emptyList(),
            speciesTraitId = session.speciesTrait.toId()
        ))
    }

    /**
     * Select a species weighted by the tier's rarity pool.
     * First tries the data-driven ShadowSignalPoolRegistry (JSON-based weighted pools).
     * Falls back to BST-based filtering if no pool data is loaded.
     */
    private fun selectSpeciesForTier(tier: ShadowSignalTier, rng: java.util.Random): com.cobblemon.mod.common.pokemon.Species {
        // Try data-driven pool first
        val poolSpecies = com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalPoolRegistry.selectFromPool(tier.rarityPool, rng)
        if (poolSpecies != null) {
            LOG.debug("[ShadowHunt] Selected species from data-driven pool '{}': {}", tier.rarityPool, poolSpecies.resourceIdentifier)
            return poolSpecies
        }

        // Fallback: BST-based selection
        LOG.debug("[ShadowHunt] No data-driven pool for '{}', falling back to BST selection", tier.rarityPool)
        val allSpecies = PokemonSpecies.implemented.toList()
        if (allSpecies.isEmpty()) return PokemonSpecies.random()

        val bstThreshold = when (tier.rarityPool) {
            "common" -> 200 to 400
            "uncommon" -> 300 to 480
            "rare" -> 400 to 540
            "elite" -> 450 to 600
            "legendary" -> 500 to 720
            else -> 200 to 500
        }

        val filtered = allSpecies.filter { species ->
            val bst = species.baseStats.values.sumOf { it }
            bst in bstThreshold.first..bstThreshold.second
        }

        return if (filtered.isNotEmpty()) {
            filtered[rng.nextInt(filtered.size)]
        } else {
            allSpecies[rng.nextInt(allSpecies.size)]
        }
    }

    /**
     * Guarantee a number of perfect IVs based on the tier.
     */
    private fun applyGuaranteedPerfectIVs(pokemon: Pokemon, tier: ShadowSignalTier, rng: java.util.Random) {
        val numPerfect = tier.minGuaranteedPerfectIVs +
                rng.nextInt(max(1, tier.maxGuaranteedPerfectIVs - tier.minGuaranteedPerfectIVs + 1))
        if (numPerfect <= 0) return

        val statList = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        val shuffled = statList.shuffled(kotlin.random.Random(rng.nextLong()))
        for (i in 0 until min(numPerfect, shuffled.size)) {
            pokemon.ivs[shuffled[i]] = 31
        }
    }

    /**
     * Find a contextual spawn position near the final hotspot rather than
     * a fixed offset from the player. Searches for a suitable ground position
     * near the last node in the trail.
     */
    private fun findContextualSpawnPos(level: ServerLevel, player: ServerPlayer, session: com.jayemceekay.shadowedhearts.common.tracking.TrailSession): BlockPos {
        // Try to spawn near the last hotspot position
        val hotspotPos = session.currentHotspot?.pos() ?: player.blockPosition()

        // Search in a radius around the hotspot for a valid spawn location
        val searchRadius = 5
        val rng = java.util.Random(session.huntSeed + 999)
        val candidates = mutableListOf<BlockPos>()

        for (dx in -searchRadius..searchRadius) {
            for (dz in -searchRadius..searchRadius) {
                if (dx == 0 && dz == 0) continue
                val candidate = hotspotPos.offset(dx, 0, dz)
                // Search vertically for ground
                for (dy in -3..3) {
                    val pos = candidate.offset(0, dy, 0)
                    if (isValidSpawnPos(level, pos)) {
                        // Prefer positions that are not directly on top of the player
                        val distToPlayer = pos.distSqr(player.blockPosition())
                        if (distToPlayer >= 4) { // at least 2 blocks from player
                            candidates.add(pos)
                        }
                    }
                }
            }
        }

        return if (candidates.isNotEmpty()) {
            // Pick a random candidate, biased toward ones closer to the hotspot
            candidates.sortBy { it.distSqr(hotspotPos) }
            val pickIdx = min(rng.nextInt(max(1, candidates.size / 3)), candidates.size - 1)
            candidates[pickIdx]
        } else {
            // Fallback: near the hotspot
            hotspotPos.offset(2, 0, 2)
        }
    }

    /**
     * Check if a position is suitable for spawning an entity:
     * solid ground below, 2 blocks of air above.
     */
    private fun isValidSpawnPos(level: ServerLevel, pos: BlockPos): Boolean {
        val below = level.getBlockState(pos.below())
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) return false
        val atPos = level.getBlockState(pos)
        val above = level.getBlockState(pos.above())
        return atPos.isAir && above.isAir
    }
}
