package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.ShadowSpawnConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModBlocks;
import com.jayemceekay.shadowedhearts.core.ModSounds;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetworkingUtils;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Set;

public class ShadowMeteoroidProximityHandler {
    private static int checkTimer = 0;

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(level -> {
            IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
            if (!config.meteoroidShadowTransformationEnabled()) return;

            if (++checkTimer >= config.meteoroidShadowTransformationCheckIntervalTicks()) {
                checkTimer = 0;
                processProximity(level);
                processBallDecay(level);
            }
        });
    }

    private static void processProximity(ServerLevel level) {
        IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
        int radius = config.meteoroidShadowTransformationRadius();
        double baseChance = config.meteoroidShadowTransformationChancePerInterval();

        java.util.List<net.minecraft.world.entity.Entity> entities = new java.util.ArrayList<>();
        level.getAllEntities().forEach(entities::add);

        for (net.minecraft.world.entity.Entity e : entities) {
            if (e instanceof PokemonEntity entity && entity.isAlive()) {
                Pokemon pokemon = entity.getPokemon();
                if (pokemon != null && !pokemon.isBattleClone() && !ShadowAspectUtil.hasShadowAspect(pokemon) && !ShadowSpawnConfig.isBlacklisted(pokemon) && !ShadowAspectUtil.isImmunized(pokemon)) {
                    if (isNearMeteoroid(level, entity.blockPosition(), radius)) {
                        double currentExposure = ShadowAspectUtil.getExposure(pokemon);
                        currentExposure += config.meteoroidShadowTransformationExposureIncrease();
                        ShadowAspectUtil.setExposureProperty(pokemon, currentExposure);

                        // Compounding chance: chance increases with exposure
                        double chance = baseChance * currentExposure;
                        if (level.random.nextDouble() < chance) {
                            transformToShadow(level, entity);
                            ShadowAspectUtil.setExposureProperty(pokemon, 0.0);
                        }
                    } else {
                        // Decay exposure if not near meteoroid
                        double currentExposure = ShadowAspectUtil.getExposure(pokemon);
                        if (currentExposure > 0) {
                            double exposure = currentExposure - config.meteoroidShadowTransformationExposureDecay();
                            ShadowAspectUtil.setExposureProperty(pokemon, Math.max(0, exposure));
                        }
                    }
                } else if (pokemon != null && ShadowAspectUtil.hasShadowAspect(pokemon)) {
                    if (isNearMeteoroid(level, entity.blockPosition(), radius)) {
                        int currentGauge = ShadowAspectUtil.getHeartGaugeMeter(pokemon);
                        int maxGauge = HeartGaugeConfig.getMax(pokemon);
                        if (currentGauge < maxGauge) {
                            int increase = (int) Math.max(1, config.meteoroidShadowTransformationExposureIncrease());
                            ShadowService.setHeartGauge(pokemon, entity, currentGauge + increase);
                        }
                    }
                }
            }
        }
    }

    private static void processBallDecay(ServerLevel level) {
        IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
        double decayAmount = config.meteoroidShadowTransformationExposureDecay();
        if (decayAmount <= 0) return;

        // Set to track Pokemon we've already processed this tick (entities were processed in processProximity)
        Set<Pokemon> processed = new HashSet<>();
        java.util.List<net.minecraft.world.entity.Entity> entities = new java.util.ArrayList<>();
        level.getAllEntities().forEach(entities::add);

        for (net.minecraft.world.entity.Entity e : entities) {
            if (e instanceof PokemonEntity entity) {
                Pokemon pokemon = entity.getPokemon();
                if (pokemon != null) processed.add(pokemon);
            }
        }

        for (ServerPlayer player : level.players()) {
            // Party
            var party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (Pokemon mon : party) {
                if (mon != null && !processed.contains(mon)) {
                    decayExposure(mon, decayAmount);
                    processed.add(mon);
                }
            }

            // PC
            var pc = Cobblemon.INSTANCE.getStorage().getPC(player);
            for (Pokemon mon : pc) {
                if (mon != null && !processed.contains(mon)) {
                    decayExposure(mon, decayAmount);
                    processed.add(mon);
                }
            }
        }
    }

    private static void decayExposure(Pokemon pokemon, double decayAmount) {
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) return;
        if (ShadowSpawnConfig.isBlacklisted(pokemon)) return;

        double currentExposure = ShadowAspectUtil.getExposure(pokemon);
        if (currentExposure > 0) {
            ShadowAspectUtil.setExposureProperty(pokemon, Math.max(0, currentExposure - decayAmount));
        }
    }

    private static boolean isNearMeteoroid(ServerLevel level, BlockPos pos, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -4, -radius), pos.offset(radius, 4, radius))) {
            if (level.getBlockState(p).is(ModBlocks.SHADOWFALL_METEOROID.get())) {
                return true;
            }
        }
        return false;
    }

    private static void transformToShadow(ServerLevel level, PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null) return;

        // Apply shadow aspects/state and initialize heart gauge to 0
        ShadowService.setShadow(pokemon, entity, true);
        if(pokemon.getOwnerUUID() != null) {
            //players start at 0
            ShadowService.setHeartGauge(pokemon, entity, 0);
        } else {
            //wild or npc start at max
            ShadowService.setHeartGauge(pokemon, entity, HeartGaugeConfig.getMax(pokemon));
        }


        // Ensure required aspects for shadow
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon);

        // Insert shadow moves
        WildShadowSpawnListener.assignShadowMoves(pokemon);

        // Play spawn sound near by
        if (ModSounds.SHADOW_AURA_INITIAL_BURST != null) {
            ShadowedHeartsNetworkingUtils.broadcastPlaySound(
                    ModSounds.SHADOW_AURA_INITIAL_BURST.getId(),
                    SoundSource.NEUTRAL,
                    level,
                    entity.getX(), entity.getY(), entity.getZ(),
                    1.0f,
                    64.0f
            );
        }

        // Broadcast specialized aura
        AuraBroadcastQueue.queueBroadcast(entity, 2.5f, 600);
        ShadowedHeartsNetworkingUtils.broadcastLuminousMoteToTracking(entity);
    }
}
