package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.AuraBroadcastQueue;
import com.jayemceekay.shadowedhearts.network.S2CUtils;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import kotlin.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import java.util.Random;

/**
 * Listens for wild Pokemon entities spawning and applies Shadow state
 * based on configurable chance and blacklist. Also injects 1–2 Shadow
 * moves into slots 1 and 2 (indexes 0 and 1) and plays a spawn sound
 * audible to nearby players.
 */
public final class WildShadowSpawnListener {
    private WildShadowSpawnListener() {
    }

    public static void init() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.NORMAL, (SpawnEvent<PokemonEntity> e) -> {
            PokemonEntity entity = e.getEntity();
            if (!(entity.level() instanceof ServerLevel level))
                return Unit.INSTANCE; // server side only

            Pokemon pokemon = entity.getPokemon();
            if (pokemon == null) return Unit.INSTANCE;

            // Only wild entities: no owner and not sent out battle clones
            if (entity.getOwnerUUID() != null) return Unit.INSTANCE;
            if (pokemon.isBattleClone()) return Unit.INSTANCE;

            // Respect blacklist
            if (ShadowSpawnUtil.isBlacklisted(pokemon)) return Unit.INSTANCE;

            // Respect immunization
            if (ShadowAspectUtil.isImmunized(pokemon)) return Unit.INSTANCE;

            // Roll chance
            double chance = ShadowSpawnUtil.getChancePercent();
            if (ShadowAspectUtil.isNearMeteoroid(level, entity.blockPosition(), 48, 48)) {
                chance *= ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().meteoroidShadowSpawnChanceMultiplier();
            }

            if (chance <= 0) return Unit.INSTANCE;
            if (chance < 100) {
                int r = RANDOM.nextInt(100);
                if (r >= chance) return Unit.INSTANCE;
            }

            // Apply shadow aspects/state and initialize heart gauge to maxt
            ShadowService.setShadow(pokemon, entity, true);
            ShadowService.setHeartGauge(pokemon, entity, HeartGaugeConfig.getMax(pokemon));

            // Ensure required aspects for shadow
            ShadowAspectUtil.ensureRequiredShadowAspects(pokemon);

            // Insert shadow moves
            ShadowMoveUtil.assignShadowMoves(pokemon, RANDOM);

            // Play spawn sound near by
            if (ModSounds.SHADOW_AURA_INITIAL_BURST != null) {
                S2CUtils.broadcastPlaySound(
                        ModSounds.SHADOW_AURA_INITIAL_BURST.getId(),
                        SoundSource.NEUTRAL,
                        level,
                        entity.getX(), entity.getY(), entity.getZ(),
                        1.0f,
                        64.0f
                );
            }

            // Broadcast specialized aura for wild spawn: 2.5x height for 30 seconds (600 ticks)
            // If it's holding a Shadow Shard, make it 24/7.
            int sustain = ShadowAspectUtil.isHoldingShadowShard(pokemon) ? Integer.MAX_VALUE / 2 : 600;
            AuraBroadcastQueue.queueBroadcast(entity, 2.5f, sustain);

            return Unit.INSTANCE;
        });
    }

    private static final Random RANDOM = new Random();
}
