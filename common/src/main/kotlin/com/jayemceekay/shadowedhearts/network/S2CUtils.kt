package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.network.aura.AuraLifecyclePacket
import com.jayemceekay.shadowedhearts.network.aura.AuraStatePacket
import com.jayemceekay.shadowedhearts.network.aura.LuminousMotePacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity

object S2CUtils {
    @JvmStatic
    fun broadcastToTracking(entity: Entity, packet: NetworkPacket<*>) {
        if (entity.level() is ServerLevel) {
            val level = entity.level() as ServerLevel
            level.chunkSource.chunkMap.getPlayers(entity.chunkPosition(), false).forEach {
                ShadowedHeartsNetwork.sendToPlayer(it, packet)
            }
        }
    }

    /** Utility: broadcast to all players tracking the entity (and the entity if it's a player). */
    @JvmStatic
    fun broadcastStateToTracking(entity: Entity, tick: Long) {
        if (entity is PokemonEntity) {
            val pkt = AuraStatePacket(
                entity.id,
                entity.x,
                entity.y,
                entity.z,
                entity.knownMovement.x.toFloat(),
                entity.knownMovement.y.toFloat(),
                entity.knownMovement.z.toFloat(),
                entity.bbWidth,
                entity.bbHeight,
                entity.boundingBox.size.toFloat(),
                tick,
                ShadowAspectUtil.getHeartGauge(entity.pokemon)
            )
            broadcastToTracking(entity, pkt)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun broadcastAuraStartToTracking(entity: Entity, heightMultiplier: Float = 1.0f, sustainOverride: Int = -1) {
        if (entity is PokemonEntity) {
            val pkt = AuraLifecyclePacket(
                entity.id,
                AuraLifecyclePacket.Action.START,
                0,
                entity.x,
                entity.y,
                entity.z,
                entity.knownMovement.x.toFloat(),
                entity.knownMovement.y.toFloat(),
                entity.knownMovement.z.toFloat(),
                entity.bbWidth,
                entity.bbHeight,
                entity.boundingBox.size.toFloat(),
                ShadowAspectUtil.getHeartGauge(entity.pokemon),
                heightMultiplier,
                sustainOverride
            )
            broadcastToTracking(entity, pkt)
        }
    }

    @JvmStatic
    fun broadcastLuminousMoteToTracking(entity: Entity) {
        if (entity is PokemonEntity) {
            val pkt = LuminousMotePacket(entity.id)
            broadcastToTracking(entity, pkt)
        }
    }

    @JvmStatic
    fun broadcastAuraFadeOutToTracking(entity: Entity, outTicks: Int) {
        if (entity is PokemonEntity) {
            val pkt = AuraLifecyclePacket(
                entity.id, AuraLifecyclePacket.Action.FADE_OUT, outTicks.coerceAtLeast(1),
                entity.x, entity.y, entity.z,
                entity.knownMovement.x.toFloat(), entity.knownMovement.y.toFloat(), entity.knownMovement.z.toFloat(),
                entity.bbWidth, entity.bbHeight, entity.boundingBox.size.toFloat(),
                ShadowAspectUtil.getHeartGauge(entity.pokemon)
            )
            broadcastToTracking(entity, pkt)
        }
    }

    @JvmStatic
    fun broadcastPlaySound(
        soundId: ResourceLocation,
        source: SoundSource,
        level: ServerLevel,
        x: Double,
        y: Double,
        z: Double,
        pitch: Float,
        radius: Float
    ) {
        val pkt = PlaySoundPacket(soundId, source, x, y, z, pitch)
        val radiusSq = (radius * radius).toDouble()
        for (sp in level.players()) {
            if (sp.distanceToSqr(x, y, z) <= radiusSq) {
                ShadowedHeartsNetwork.sendToPlayer(sp, pkt)
            }
        }
    }
}
