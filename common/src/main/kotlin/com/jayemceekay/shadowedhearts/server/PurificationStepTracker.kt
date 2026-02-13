package com.jayemceekay.shadowedhearts.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.platform.events.ChangeDimensionEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.ServerPlayerEvent
import com.cobblemon.mod.common.platform.events.ServerPlayerTickEvent
import com.jayemceekay.shadowedhearts.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Server-side movement listener that converts player movement into Purification Chamber steps.
 * Guards against teleports by ignoring very large per-tick position deltas.
 */
object PurificationStepTracker {
    private val lastPositions: MutableMap<UUID, Vec3> = ConcurrentHashMap()
    private val partyStepAccumulators: MutableMap<UUID, Int> = ConcurrentHashMap()

    // Ignore deltas greater than this many blocks in a single tick (likely teleport).
    private const val TELEPORT_THRESHOLD_BLOCKS = 64.0

    // Maximum steps credited per tick to avoid spikes (sprinting, lag bursts).
    private const val MAX_STEPS_PER_TICK = 8

    fun init() {
        // Track joins/leaves to manage last position state
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe { ev: ServerPlayerEvent.Login ->
            lastPositions[ev.player.uuid] = ev.player.position()
            partyStepAccumulators[ev.player.uuid] = 0
        }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe { ev: ServerPlayerEvent.Logout ->
            lastPositions.remove(ev.player.uuid)
            partyStepAccumulators.remove(ev.player.uuid)
        }
        PlatformEvents.CHANGE_DIMENSION.subscribe { ev: ChangeDimensionEvent ->
            // Reset on dimension change to avoid cross-dimension deltas
            lastPositions[ev.player.uuid] = ev.player.position()
        }

        PlatformEvents.SERVER_PLAYER_TICK_POST.subscribe { ev: ServerPlayerTickEvent.Post ->
            val player: ServerPlayer = ev.player
            if (player.isSpectator) {
                // Do not count spectator mode
                lastPositions[player.uuid] = player.position()
                return@subscribe
            }

            val prev = lastPositions.put(player.uuid, player.position())
            if (prev == null) return@subscribe

            val cur = player.position()
            val dx = cur.x - prev.x
            val dz = cur.z - prev.z
            val dy = cur.y - prev.y

            // Only consider horizontal movement; vertical spikes (falls/jumps) shouldn't count as "steps"
            val horizontalDist = kotlin.math.sqrt(dx * dx + dz * dz)

            // If overall displacement (including Y) is suspiciously huge, treat as teleport and ignore
            val manhattan = abs(dx) + abs(dy) + abs(dz)
            if (manhattan > TELEPORT_THRESHOLD_BLOCKS) {
                return@subscribe
            }

            // Convert distance to step count. 1 block ~ 1 step. Floor to int, clamp per-tick.
            val steps = floor(horizontalDist).toInt().coerceAtLeast(0)
            if (steps <= 0) return@subscribe

            val clamped = min(steps, MAX_STEPS_PER_TICK)

            // Resolve the player's Purification store and advance steps
            val reg = player.registryAccess()
            val store = Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, player.uuid, reg)
            store?.advanceSteps(clamped)

            // Also accrue PARTY walking steps: every 256 steps, apply party onStep to all shadow Pokémon
            if (clamped > 0) {
                val total = (partyStepAccumulators[player.uuid] ?: 0) + clamped
                var remainder = total
                val intervals = remainder / 256
                remainder %= 256
                partyStepAccumulators[player.uuid] = remainder

                if (intervals > 0) {
                    val party = Cobblemon.storage.getParty(player)
                    // Take a defensive snapshot to avoid ConcurrentModificationException if event handling
                    // causes party mutations while iterating
                    val snapshot = party.toList()
                    // For each completed 256-step block, trigger once per shadow Pokémon in party
                    repeat(intervals) {
                        for (mon in snapshot) {
                            if (ShadowAspectUtil.hasShadowAspect(mon)) {
                                // Wire up HeartGaugeEvents.onPartyStep; live entity may be null when in party
                                com.jayemceekay.shadowedhearts.heart.HeartGaugeEvents.onPartyStep(mon, null)
                            }
                        }
                    }
                }
            }
        }
    }
}
