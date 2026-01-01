package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

object ShadowedHeartsNetworkingUtils {
    /** Utility: broadcast to all players tracking the entity (and the entity if it's a player). */
    @JvmStatic
    fun broadcastStateToTracking(entity: Entity, tick: Long) {
        if (entity is PokemonEntity) {
            val pkt = AuraStatePacket(
                entity.id,
                entity.x, entity.y, entity.z,
                entity.knownMovement.x, entity.knownMovement.y, entity.knownMovement.z,
                entity.bbWidth, entity.bbHeight, entity.boundingBox.size,
                tick,
                PokemonAspectUtil.getHeartGauge(entity.pokemon)
            )
            val level = entity.level() as ServerLevel
            for (sp in level.players()) {
                ShadowedHeartsNetwork.sendToPlayer(sp, pkt)
            }
        }
    }

    @JvmStatic
    fun broadcastAuraStartToTracking(entity: Entity) {
        if (entity is PokemonEntity) {
            val pkt = AuraLifecyclePacket(
                entity.id, AuraLifecyclePacket.Action.START, 0,
                entity.x, entity.y, entity.z,
                entity.knownMovement.x, entity.knownMovement.y, entity.knownMovement.z,
                entity.bbWidth, entity.bbHeight, entity.boundingBox.size,
                PokemonAspectUtil.getHeartGauge(entity.pokemon)
            )
            if (entity.level() is ServerLevel) {
                val level = entity.level() as ServerLevel
                for (sp in level.players()) {
                    ShadowedHeartsNetwork.sendToPlayer(sp, pkt)
                }
            }
        }
    }

    @JvmStatic
    fun broadcastAuraFadeOutToTracking(entity: Entity, outTicks: Int) {
        if (entity is PokemonEntity) {
            val pkt = AuraLifecyclePacket(
                entity.id, AuraLifecyclePacket.Action.FADE_OUT, outTicks.coerceAtLeast(1),
                entity.x, entity.y, entity.z,
                entity.knownMovement.x, entity.knownMovement.y, entity.knownMovement.z,
                entity.bbWidth, entity.bbHeight, entity.boundingBox.size,
                PokemonAspectUtil.getHeartGauge(entity.pokemon)
            )
            if (entity.level() is ServerLevel) {
                val level = entity.level() as ServerLevel
                for (sp in level.players()) {
                    ShadowedHeartsNetwork.sendToPlayer(sp, pkt)
                }
            }
        }
    }
}
