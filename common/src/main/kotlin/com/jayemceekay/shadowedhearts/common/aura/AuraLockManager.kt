package com.jayemceekay.shadowedhearts.common.aura

import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * Tracks independent player grants. Only native natural despawn is temporarily blocked;
 * entity saving, chunk unloading and other removal paths remain under Cobblemon's control.
 */
object AuraLockManager {
    private val grants = AuraLockGrants()

    /**
     * Apply or extend a lock on the given entity. Returns the locked-until tick value.
     */
    fun applyLock(entity: Entity, playerId: UUID, nowTick: Long, requestedDurationTicks: Int): Long {
        val cfg = ShadowedHeartsConfigs.getInstance().shadowConfig
        val maxTicks = (cfg.auraLockMaxSeconds() * 20).coerceAtLeast(1)
        val clamped = requestedDurationTicks.coerceIn(1, maxTicks)
        val desiredUntil = nowTick + clamped

        val newUntil = grants.grant(entity.uuid, playerId, desiredUntil)

        // Schedule an unlock check shortly after expiry; if extended later, this check will no-op.
        val delay = (newUntil - nowTick + 1).coerceAtLeast(1).toInt()
        afterOnServer(delay, entity.level()) {
            clearIfExpired(entity, playerId, entity.level().gameTime)
        }

        return newUntil
    }

    /**
     * A scheduled expiry only clears this player's expired grant, including removed entities.
     */
    fun clearIfExpired(entity: Entity, playerId: UUID, nowTick: Long) {
        if (!grants.isGranted(entity.uuid, playerId, nowTick)) grants.clear(entity.uuid, playerId)
    }

    fun clear(entity: Entity, playerId: UUID) {
        grants.clear(entity.uuid, playerId)
    }

    fun clearPlayer(playerId: UUID) {
        grants.clearPlayer(playerId)
    }

    fun clearAll() {
        grants.clearAll()
    }

    /** Utility to check if an entity is currently under an aura lock. */
    fun isLocked(entity: Entity, playerId: UUID, nowTick: Long): Boolean {
        return grants.isGranted(entity.uuid, playerId, nowTick)
    }

    /** Revalidate at the native despawn decision so stale equipment/range grants never protect it. */
    fun preventsNaturalDespawn(entity: PokemonEntity): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        val now = level.gameTime
        var protected = false
        for (playerId in grants.players(entity.uuid, now)) {
            val player = level.server.playerList.getPlayer(playerId)
            if (player != null && AuraReaderService.isValidWildLockOwner(player, entity)) {
                protected = true
            } else {
                grants.clear(entity.uuid, playerId)
            }
        }
        return protected
    }
}
