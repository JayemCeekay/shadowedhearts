package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * Applies resolved Shadow profiles to NPC battle parties. Authoring tags and presets stay unchanged.
 * Convert selects existing non-Shadow members; append and replace create bounded candidate sets.
 */
public final class NPCShadowInjector {
    private NPCShadowInjector() { }

    public static final String TAG_ENABLE = "sh_shadow_party";
    public static final String TAG_MODE_CONVERT = "sh_shadow_mode_convert";
    public static final String TAG_MODE_APPEND = "sh_shadow_mode_append";
    public static final String TAG_MODE_REPLACE = "sh_shadow_mode_replace";
    public static final String TAG_COUNT_PREFIX = "sh_shadow_n";
    public static final String TAG_LVL_MATCH = "sh_lvl_match";
    public static final String TAG_LVL_FIXED_PREFIX = "sh_lvl_fixed_";
    public static final String TAG_LVL_PLUS_PREFIX = "sh_lvl_plus_";
    public static final String TAG_POOL_PREFIX = "sh_pool/";
    public static final String TAG_UNIQUE = "sh_unique";
    public static final String TAG_CONVERT_CHANCE_PREFIX = "sh_convert_chance_";
    public static final String TAG_PRESET_PREFIX = "sh_shadow_presets/";
    public static final String TAG_LVL_ENFORCE_EVO_MIN = "sh_lvl_enforce_evo_min";
    private static final int PARTY_MAX = 6;

    public static void init() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, (BattleStartedEvent.Pre event) -> {
            UUID battleId = event.getBattle().getBattleId();
            long salt = battleId == null ? 0L : battleId.getLeastSignificantBits();
            for (BattleActor actor : event.getBattle().getActors()) {
                Entity entity = NPCShadowTargets.observeBattleActor(actor);
                if (entity == null) continue;
                try {
                    apply(actor, entity, salt);
                } catch (RuntimeException | LinkageError error) {
                    Shadowedhearts.LOGGER.error("Could not prepare Shadow party for NPC {} in battle {}",
                            entity.getUUID(), battleId, error);
                }
            }
            return Unit.INSTANCE;
        });
    }

    private static void apply(BattleActor actor, Entity entity, long salt) {
        try {
            NPCShadowProfile.Resolution resolution = NPCShadowProfiles.resolve(entity);
            NPCShadowProfile profile = resolution.profile();
            if (!resolution.valid()) {
                Shadowedhearts.LOGGER.warn("Skipping Shadow injection for NPC {}: {}",
                        entity.getUUID(), com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.join(resolution.errors()).getString());
            } else if (profile.enabled() && profile.count() > 0) {
                int level = resolveLevel(profile, actor);
                switch (profile.mode()) {
                    case CONVERT -> convertExistingToShadow(actor.getPokemonList(), profile.count(),
                            profile.convertChance(), makeRng(entity, salt));
                    case APPEND -> appendShadowPokemon(actor, entity, profile, level, salt);
                    case REPLACE -> replaceWithShadowPokemon(actor, entity, profile, level, salt);
                }
            }
        } finally {
            // Refresh accepted changes even if a later candidate fails, and support native authored Shadows.
            refreshExistingShadows(actor.getPokemonList(), makeRng(entity, salt));
        }
    }

    private static int resolveLevel(NPCShadowProfile profile, BattleActor actor) {
        int base = 1;
        if (!actor.getPokemonList().isEmpty()) {
            Pokemon lead = pokemon(actor.getPokemonList().get(0));
            if (lead != null) base = lead.getLevel();
        }
        long requested = switch (profile.levelMode()) {
            case MATCH -> base;
            case FIXED -> profile.levelValue();
            case PLUS -> (long) base + profile.levelValue();
        };
        return (int) Math.max(1, Math.min(Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel(), requested));
    }

    private static void refreshExistingShadows(List<BattlePokemon> list, Random rng) {
        for (BattlePokemon bp : list) {
            Pokemon p = pokemon(bp);
            if (p != null && p.getAspects().contains(SHAspects.SHADOW)) initializeShadow(p, rng);
        }
    }

    private static void initializeShadow(Pokemon pokemon, Random rng) {
        forceShadow(pokemon);
        pokemon.setOriginalTrainer("?????");
        ShadowStateInitialization.ensureRequiredShadowAspects(pokemon);
        ShadowMoveUtil.assignShadowMoves(pokemon, rng);
    }

    private static void convertExistingToShadow(List<BattlePokemon> list, int count, int chance, Random rng) {
        List<Integer> eligible = nonShadowSlots(list);
        Collections.shuffle(eligible, rng);
        int converted = 0;
        for (int index : eligible) {
            if (chance >= 0 && rng.nextInt(100) >= chance) continue;
            forceShadow(pokemon(list.get(index)));
            if (++converted >= count) break;
        }
    }

    private static void appendShadowPokemon(BattleActor actor, Entity entity, NPCShadowProfile profile,
                                             int level, long salt) {
        List<BattlePokemon> party = actor.getPokemonList();
        int requested = Math.min(profile.count(), Math.max(0, PARTY_MAX - party.size()));
        if (requested == 0) {
            Shadowedhearts.LOGGER.debug("NPC {} has no free party slots; append skipped", entity.getUUID());
            return;
        }
        List<BattlePokemon> additions;
        if (profile.poolId() != null) {
            additions = createFromPool(entity, profile, requested, level, salt, collectSpeciesIds(party, -1));
        } else {
            // An absent pool permits legacy cloning; an invalid/empty explicit pool is rejected by resolution.
            additions = NPCShadowSelection.appendCopies(party, requested, profile.unique(), source -> {
                Pokemon original = pokemon(source);
                if (original == null) return null;
                BattlePokemon clone = BattlePokemon.Companion.safeCopyOf(original);
                clone.getEffectedPokemon().setLevel(level);
                return clone;
            }, NPCShadowInjector::species, candidate -> !profile.enforceMinEvolutionLevel()
                    || getMinEvolutionLevel(candidate.getEffectedPokemon()) <= candidate.getEffectedPokemon().getLevel());
        }
        for (BattlePokemon addition : additions) {
            forceShadow(addition.getEffectedPokemon());
            addition.setActor(actor);
        }
        party.addAll(additions);
        reportShortfall(entity, "append", requested, additions.size());
    }

    private static void replaceWithShadowPokemon(BattleActor actor, Entity entity, NPCShadowProfile profile,
                                                  int level, long salt) {
        List<BattlePokemon> party = actor.getPokemonList();
        List<Integer> targets = nonShadowSlots(party);
        Collections.shuffle(targets, makeRng(entity, salt));
        int requested = Math.min(profile.count(), targets.size());
        int replaced = 0;
        for (int slot : targets) {
            if (replaced >= requested) break;
            BattlePokemon replacement;
            if (profile.poolId() != null) {
                // Exclude this slot only: every accepted replacement is checked against retained teammates.
                List<BattlePokemon> candidates = createFromPool(entity, profile, 1, level, salt + slot,
                        collectSpeciesIds(party, slot));
                if (candidates.isEmpty()) continue;
                replacement = candidates.get(0);
            } else {
                replacement = BattlePokemon.Companion.safeCopyOf(pokemon(party.get(slot)));
                replacement.getEffectedPokemon().setLevel(level);
                if (profile.enforceMinEvolutionLevel()
                        && getMinEvolutionLevel(replacement.getEffectedPokemon()) > replacement.getEffectedPokemon().getLevel()) continue;
            }
            if (profile.unique() && NPCShadowSelection.containsSpecies(party, species(replacement), slot,
                    NPCShadowInjector::species)) continue;
            forceShadow(replacement.getEffectedPokemon());
            replacement.setActor(actor);
            party.set(slot, replacement);
            replaced++;
        }
        reportShortfall(entity, "replace", requested, replaced);
    }

    private static List<BattlePokemon> createFromPool(Entity entity, NPCShadowProfile profile, int count,
                                                       int level, long salt, Set<String> present) {
        var entries = ShadowPools.get(entity.level().getServer(), ResourceLocation.parse(profile.poolId()));
        List<Pokemon> candidates = NPCShadowSelection.poolCandidates(entries, count, entry -> entry.weight, entry -> {
            PokemonProperties copy = entry.props.copy();
            if (copy.getLevel() == null || copy.getLevel() <= 0) copy.setLevel(level);
            try {
                return copy.create();
            } catch (RuntimeException error) {
                Shadowedhearts.LOGGER.warn("NPC {} pool {} rejected a candidate: {}",
                        entity.getUUID(), profile.poolId(), error.toString());
                return null;
            }
        }, candidate -> !profile.enforceMinEvolutionLevel() || getMinEvolutionLevel(candidate) <= candidate.getLevel(),
                candidate -> candidate.getSpecies().showdownId(), present, profile.unique(), makeRng(entity, salt));
        List<BattlePokemon> result = new ArrayList<>();
        for (Pokemon candidate : candidates) result.add(BattlePokemon.Companion.safeCopyOf(candidate));
        return result;
    }

    private static void reportShortfall(Entity entity, String mode, int requested, int completed) {
        if (completed < requested) Shadowedhearts.LOGGER.warn(
                "NPC {} {} requested {} Shadow Pokemon but only {} candidates satisfied the pool, uniqueness and level rules",
                entity.getUUID(), mode, requested, completed);
    }

    private static List<Integer> nonShadowSlots(List<BattlePokemon> party) {
        List<Integer> result = new ArrayList<>();
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon p = pokemon(party.get(slot));
            if (p != null && !p.getAspects().contains(SHAspects.SHADOW)) result.add(slot);
        }
        return result;
    }

    private static void forceShadow(Pokemon pokemon) {
        if (pokemon == null) return;
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        forced.add(SHAspects.SHADOW);
        pokemon.setForcedAspects(forced);
        UncatchableProperty.INSTANCE.catchable().apply(pokemon);
        pokemon.updateAspects();
    }

    private static Set<String> collectSpeciesIds(List<BattlePokemon> party, int excludedSlot) {
        Set<String> result = new HashSet<>();
        for (int slot = 0; slot < party.size(); slot++) {
            if (slot == excludedSlot) continue;
            String species = species(party.get(slot));
            if (species != null) result.add(NPCShadowSelection.normalize(species));
        }
        return result;
    }

    private static String species(BattlePokemon bp) {
        Pokemon p = pokemon(bp);
        return p == null ? null : p.getSpecies().showdownId();
    }

    private static Pokemon pokemon(BattlePokemon bp) { return bp == null ? null : bp.getEffectedPokemon(); }

    private static Random makeRng(Entity entity, long salt) {
        long seed = entity.level() instanceof ServerLevel level ? level.getSeed() : 0;
        return new Random(seed ^ entity.getUUID().getMostSignificantBits()
                ^ entity.getUUID().getLeastSignificantBits() ^ salt);
    }

    private static int getMinEvolutionLevel(Pokemon pokemon) {
        PreEvolution previous = pokemon.getPreEvolution();
        if (previous == null) return 1;
        for (var evolution : previous.getForm().getEvolutions()) {
            for (var requirement : evolution.getRequirements()) {
                if (requirement instanceof LevelRequirement level) return level.getMinLevel();
            }
        }
        return 1;
    }
}
