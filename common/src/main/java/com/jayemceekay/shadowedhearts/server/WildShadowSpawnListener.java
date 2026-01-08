package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowSpawnConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModSounds;
import kotlin.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Listens for wild Pokemon entities spawning and applies Shadow state
 * based on configurable chance and blacklist. Also injects 1–2 Shadow
 * moves into slots 1 and 2 (indexes 0 and 1) and plays a spawn sound
 * audible to nearby players.
 * <p>
 * Canonical ref: 01_Core_Mechanics_Shadow_Purity; 05_Aggro_Combat_System (sound UX)
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
            if (ShadowSpawnConfig.isBlacklisted(pokemon)) return Unit.INSTANCE;

            // Roll chance
            double chance = ShadowSpawnConfig.getChancePercent();
            if (chance <= 0) return Unit.INSTANCE;
            if (chance < 100) {
                int r = RANDOM.nextInt(100);
                if (r >= chance) return Unit.INSTANCE;
            }

            // Apply shadow aspects/state and initialize heart gauge to maxt
            ShadowService.setShadow(pokemon, entity, true);
            ShadowService.setHeartGauge(pokemon, entity, HeartGaugeConfig.getMax(pokemon));

            // Ensure required aspects for shadow
            PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);

            // Insert 1–2 shadow moves into slots 0 and 1.
            assignShadowMoves(pokemon);

            // Play spawn sound near by
            if (ModSounds.SHADOW_SPAWN != null) {
                new UnvalidatedPlaySoundS2CPacket(ModSounds.SHADOW_AURA_INITIAL_BURST.getId(), SoundSource.NEUTRAL,
                        entity.getX(), entity.getY(), entity.getZ(), 1.0f, 1.0f).sendToPlayersAround(entity.getX(), entity.getY(), entity.getZ(), 64.0f, level.dimension(), serverPlayer -> false);
            }

            // Broadcast specialized aura for wild spawn: 2.5x height for 10 seconds (200 ticks)
            AuraBroadcastQueue.queueBroadcast(entity, 2.5f, 600);

            return Unit.INSTANCE;
        });
    }

    private static final Random RANDOM = new Random();

    public static void assignShadowMoves(Pokemon pokemon) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowMovesOnlyShadowRush()) {
            var tmpl = Moves.INSTANCE.getByNameOrDummy("shadowrush");
            pokemon.getMoveSet().setMove(0, tmpl.create(tmpl.getPp(), 0));
            return;
        }

        // Pick shadow moves based on config
        int count = ModConfig.resolveReplaceCount(RANDOM);
        List<String> pool = new ArrayList<>();
        for (String id : SHADOW_IDS) pool.add(id);

        for (int i = 0; i < Math.min(count, 4); i++) {
            String moveId = (i == 0) ? "shadowrush" : pickShadow(pool, null);
            if (moveId != null) {
                var tmpl = Moves.INSTANCE.getByNameOrDummy(moveId);
                pokemon.getMoveSet().setMove(i, tmpl.create(tmpl.getPp(), 0));
                // Remove from pool to avoid duplicates if possible
                pool.remove(moveId);
            }
        }
    }

    private static String pickShadow(List<String> ids, String exclude) {
        if (ids.isEmpty()) return null;
        int tries = 0;
        while (tries++ < 8) {
            String id = ids.get(RANDOM.nextInt(ids.size()));
            if (exclude == null || !exclude.equalsIgnoreCase(id)) return id;
        }
        return ids.get(0);
    }

    private static final String[] SHADOW_IDS = new String[]{
            "shadowblast", "shadowblitz", "shadowbolt", "shadowbreak", "shadowchill",
            "shadowdown", "shadowend", "shadowfire", "shadowhalf", "shadowhold",
            "shadowmist", "shadowpanic", "shadowrave", "shadowrush", "shadowshed",
            "shadowsky", "shadowstorm", "shadowwave"
    };
}
