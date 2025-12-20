package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;
import com.jayemceekay.shadowedhearts.config.ShadowSpawnConfig;
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
                new UnvalidatedPlaySoundS2CPacket(ModSounds.SHADOW_SPAWN.getId(), SoundSource.MASTER,
                        entity.getX(), entity.getY(), entity.getZ(), 0.6f, 1.0f).sendToPlayersAround(entity.getX(), entity.getY(), entity.getZ(), 32.0f, level.dimension(), serverPlayer -> false);
            }

            return Unit.INSTANCE;
        });
    }

    private static final Random RANDOM = new Random();

    private static void assignShadowMoves(Pokemon pokemon) {
        // Collect all known shadow move ids
        List<String> shadowIds = new ArrayList<>();
        // Using ShadowGate utilities list
        for (String id : SHADOW_IDS) shadowIds.add(id);

        // Pick 1 or 2 distinct shadow moves
        int count = 1 + RANDOM.nextInt(2); // 1..2
        String first = pickShadow(shadowIds, null);
        String second = count == 2 ? pickShadow(shadowIds, first) : null;

        // Place into slots 0 and 1
        if (first != null) {
            var tmpl = Moves.INSTANCE.getByNameOrDummy(first);
            pokemon.getMoveSet().setMove(0, tmpl.create(tmpl.getPp(), 0));
        }
        if (second != null) {
            var tmpl = Moves.INSTANCE.getByNameOrDummy(second);
            pokemon.getMoveSet().setMove(1, tmpl.create(tmpl.getPp(), 0));
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
