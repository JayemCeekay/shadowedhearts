package com.jayemceekay.shadowedhearts.common.aura

import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages aura locks on entities by writing a locked-until tick timestamp to the entity's persistentData
 * and scheduling an unlock task to clear persistence when the lock expires.
 */
object AuraLockManager {
    private val locks: ConcurrentHashMap<java.util.UUID, Long> = ConcurrentHashMap()

    /**
     * Apply or extend a lock on the given entity. Returns the locked-until tick value.
     */
    fun applyLock(entity: Entity, nowTick: Long, requestedDurationTicks: Int): Long {
        val cfg = ShadowedHeartsConfigs.getInstance().shadowConfig
        val maxTicks = (cfg.auraLockMaxSeconds() * 20).coerceAtLeast(1)
        val clamped = requestedDurationTicks.coerceIn(1, maxTicks)
        val desiredUntil = nowTick + clamped

        val key = entity.uuid
        val current = locks[key] ?: 0L
        val newUntil = if (current > desiredUntil) current else desiredUntil
        locks[key] = newUntil

        // Schedule an unlock check shortly after expiry; if extended later, this check will no-op.
        val delay = (newUntil - nowTick + 1).coerceAtLeast(1).toInt()
        afterOnServer(delay, entity.level()) {
            if (!entity.isRemoved) {
                val now = entity.level().gameTime
                clearIfExpired(entity, now)
            }
        }

        return newUntil
    }

    /**
     * Clears the lock and persistence flag if the stored expiry is in the past.
     */
    fun clearIfExpired(entity: Entity, nowTick: Long) {
        val key = entity.uuid
        val until = locks[key] ?: 0L
        if (until <= nowTick) {
            locks.remove(key)
            // No action needed beyond removing the lock; despawn will be allowed naturally afterwards.
        }
    }

    fun clear(entity: Entity) {
        locks.remove(entity.uuid)
    }

    /** Utility to check if an entity is currently under an aura lock. */
    fun isLocked(entity: Entity, nowTick: Long): Boolean {
        return (locks[entity.uuid] ?: 0L) > nowTick
    }
}
