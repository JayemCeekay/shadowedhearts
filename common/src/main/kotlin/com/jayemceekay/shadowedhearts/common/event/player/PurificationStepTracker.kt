package com.jayemceekay.shadowedhearts.common.event.player

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.platform.events.ChangeDimensionEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.ServerPlayerEvent
import com.cobblemon.mod.common.platform.events.ServerPlayerTickEvent
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeEvents
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side movement listener that converts player movement into Purification Chamber steps.
 * Guards against teleports by ignoring very large per-tick position deltas.
 */
object PurificationStepTracker {
    private val walkingProgress: MutableMap<UUID, PurificationWalkingProgress> = ConcurrentHashMap()

    fun init() {
        // Track joins/leaves to manage last position state
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe { ev: ServerPlayerEvent.Login ->
            walkingProgress[ev.player.uuid] = PurificationWalkingProgress().apply {
                resetPosition(ev.player.x, ev.player.y, ev.player.z)
            }
        }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe { ev: ServerPlayerEvent.Logout ->
            walkingProgress.remove(ev.player.uuid)
        }
        PlatformEvents.CHANGE_DIMENSION.subscribe { ev: ChangeDimensionEvent ->
            // Reset on dimension change to avoid cross-dimension deltas
            walkingProgress.computeIfAbsent(ev.player.uuid) { PurificationWalkingProgress() }
                .resetPosition(ev.player.x, ev.player.y, ev.player.z)
        }

        PlatformEvents.SERVER_PLAYER_TICK_POST.subscribe { ev: ServerPlayerTickEvent.Post ->
            val player: ServerPlayer = ev.player
            val credit = walkingProgress.computeIfAbsent(player.uuid) { PurificationWalkingProgress() }
                .advance(player.x, player.y, player.z, !player.isSpectator, GameplayTuningConfig.INSTANCE.partyStepInterval())
            if (credit.chamberSteps == 0) return@subscribe

            // Resolve the player's Purification store and advance steps
            val reg = player.registryAccess()
            val store = Cobblemon.storage.getCustomStore(PurificationChamberStore::class.java, player.uuid, reg)
            store?.advanceSteps(credit.chamberSteps)

            if (credit.partyIntervals > 0) {
                val party = Cobblemon.storage.getParty(player)
                // Snapshot before callbacks, which can mutate the party.
                val snapshot = party.toList()
                for (mon in snapshot) {
                    if (ShadowAspectUtil.hasShadowAspect(mon)) {
                        repeat(credit.partyIntervals) {
                            HeartGaugeEvents.onPartyStep(mon, null)
                        }
                    }
                }
            }
        }
    }
}
