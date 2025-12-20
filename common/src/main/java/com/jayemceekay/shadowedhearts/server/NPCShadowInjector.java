package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.jayemceekay.shadowedhearts.data.ShadowAspectPresets;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * NPC Shadow Aspects injector.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * This listens to Cobblemon's BATTLE_STARTED_PRE and, if an NPC entity has specific tags/aspects,
 * mutates up to N of their party members for this battle by applying the Shadow aspect.
 *
 * Minimal MVP behaviors implemented:
 * - Enable via entity tag "shadowedhearts:shadow_party".
 * - Mode: only "convert" supported at the moment (default). If tag "shadowedhearts:shadow_mode_convert" is present,
 *   behavior is the same; other modes are ignored for MVP to keep the footprint small.
 * - Count via tag "shadowedhearts:shadow_n1".."shadowedhearts:shadow_n6". Defaults to 1 if unspecified.
 *
 * The mutation acts on the BattlePokemon's effected Pokemon so it's scoped to this battle instance and
 * doesn’t require altering existing datapacks or party providers.
 */
public final class NPCShadowInjector {
    private NPCShadowInjector() {}

    public static final String TAG_ENABLE = "shadowedhearts:shadow_party";
    public static final String TAG_MODE_CONVERT = "shadowedhearts:shadow_mode_convert";
    public static final String TAG_MODE_APPEND = "shadowedhearts:shadow_mode_append";
    public static final String TAG_MODE_REPLACE = "shadowedhearts:shadow_mode_replace";
    public static final String TAG_COUNT_PREFIX = "shadowedhearts:shadow_n"; // followed by [1-6]
    // Level policy tags
    public static final String TAG_LVL_MATCH = "shadowedhearts:lvl_match";
    public static final String TAG_LVL_FIXED_PREFIX = "shadowedhearts:lvl_fixed_"; // + number
    public static final String TAG_LVL_PLUS_PREFIX = "shadowedhearts:lvl_plus_"; // + number
    public static final String TAG_POOL_PREFIX = "shadowedhearts:pool/"; // + <ns>/<id>
    public static final String TAG_UNIQUE = "shadowedhearts:unique"; // avoid duplicates when injecting
    public static final String TAG_CONVERT_CHANCE_PREFIX = "shadowedhearts:convert_chance_"; // + percent 0-100
    public static final String TAG_PRESET_PREFIX = "shadowedhearts:preset/"; // + <preset id>

    private enum Mode { APPEND, CONVERT, REPLACE }

    private static final int PARTY_MAX = 6;

    /** Call during common init. */
    public static void init() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, (BattleStartedEvent.Pre event) -> {
            try {
                // Iterate battle actors; if an NPC actor has the enabling tag, process their team
                event.getBattle().getActors().forEach(ba -> {
                    if (ba instanceof EntityBackedBattleActor<?> ebActor) {
                        // Include battle id as RNG salt to vary repeated encounters deterministically per battle
                        long battleSalt = 0L;
                        try { battleSalt = event.getBattle().getBattleId().getLeastSignificantBits(); } catch (Throwable ignored) {}
                        Entity entity = ebActor.getEntity();
                        // Expand any preset aspects present on the NPC into their concrete lists
                        expandPresetAspects(entity);
                        Set<String> tags = entity.getTags();
                        if (!tags.contains(TAG_ENABLE)) return; // opt-in per NPC instance

                        final int count = readCountFromTags(tags);
                        if (count <= 0) return;

                        final Mode mode = readMode(tags);
                        final LevelPolicy lvl = readLevelPolicy(tags, ba);
                        final ResourceLocation poolId = readPoolId(tags);
                        final boolean unique = tags.contains(TAG_UNIQUE);
                        final int convertChance = readConvertChance(tags);

                        switch (mode) {
                            case CONVERT -> convertExistingToShadow(ba.getPokemonList(), count, convertChance, makeRng(entity, battleSalt));
                            case APPEND -> appendShadowPokemon(ba, entity, count, lvl, poolId, unique, battleSalt);
                            case REPLACE -> replaceWithShadowPokemon(ba, entity, count, lvl, poolId, unique, battleSalt);
                        }
                    }
                });
            } catch (Throwable ignored) {
                ignored.printStackTrace();
            }
            return Unit.INSTANCE;
        });
    }

    private static void expandPresetAspects(Entity entity) {
        try {
            if (!(entity instanceof NPCEntity npc)) return;
            MinecraftServer server = entity.level().getServer();
            if (server == null) return;
            // Work on a copy to avoid concurrent modification
            java.util.ArrayList<String> toRemove = new java.util.ArrayList<>();
            java.util.ArrayList<String> toAdd = new java.util.ArrayList<>();
            for (String aspect : npc.getAppliedAspects()) {
                if (!ShadowAspectPresets.isPresetKey(aspect)) continue;
                var id = ShadowAspectPresets.toPresetId(aspect);
                if (id == null) continue;
                var list = ShadowAspectPresets.get(server, id);
                if (!list.isEmpty()) {
                    toAdd.addAll(list);
                }
                toRemove.add(aspect);
            }
            // Also allow preset request via entity tag: shadowedhearts:preset/<presetId>
            for (String tag : entity.getTags()) {
                if (!tag.startsWith(TAG_PRESET_PREFIX)) continue;
                String presetId = tag.substring(TAG_PRESET_PREFIX.length());
                if (presetId.isBlank()) continue;
                String presetKey = "shadowedhearts:preset/" + presetId;
                if (!ShadowAspectPresets.isPresetKey(presetKey)) continue;
                var id = ShadowAspectPresets.toPresetId(presetKey);
                if (id == null) continue;
                var list = ShadowAspectPresets.get(server, id);
                if (!list.isEmpty()) {
                    toAdd.addAll(list);
                }
            }
            if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
                npc.getAppliedAspects().removeAll(toRemove);
                npc.getAppliedAspects().addAll(toAdd);
                // Note: We don't directly touch NPCEntity.ASPECTS (private). Server will propagate on next sync.
            }
        } catch (Throwable ignored) {
        }
    }

    private static int readCountFromTags(Set<String> tags) {
        int count = 1; // default
        for (int i = 6; i >= 1; i--) { // prefer highest explicit tag if multiple present
            String key = TAG_COUNT_PREFIX + i;
            if (tags.contains(key)) {
                count = i;
                break;
            }
        }
        return count;
    }

    private static Mode readMode(Set<String> tags) {
        if (tags.contains(TAG_MODE_APPEND)) return Mode.APPEND;
        if (tags.contains(TAG_MODE_REPLACE)) return Mode.REPLACE;
        // default and explicit convert
        return Mode.CONVERT;
    }

    private static void convertExistingToShadow(List<BattlePokemon> list, int count, int convertChance, Random rng) {
        int converted = 0;
        for (BattlePokemon bp : list) {
            if (bp == null) continue;
            Pokemon p = bp.getEffectedPokemon();
            if (p == null) continue;
            // If already Shadow, skip counting it to avoid over-applying
            if (p.getAspects().contains(SHAspects.SHADOW)) {
                continue;
            }

            // Per-slot probability gate if provided
            if (convertChance >= 0 && rng != null) {
                int roll = rng.nextInt(100);
                if (roll >= convertChance) {
                    continue; // skip this slot
                }
            }

            // Force the Shadow aspect for this battle (defensive against immutable collections)
            forceShadow(p);

            // Ensure supporting aspects exist
            PokemonAspectUtil.ensureRequiredShadowAspects(p);
            // Inject 1–2 Shadow moves into slots 0 and 1, preserving other known moves
            assignShadowMoves(p, rng);
            System.out.println("Forced Shadow Aspect on " + p.getDisplayName(false).getString());
            converted++;
            if (converted >= count) break;
        }
    }

    /** Minimal level policy used when creating new mons */
    private record LevelPolicy(LevelMode mode, int value, int base) {
        enum LevelMode { MATCH, FIXED, PLUS }
        int resolve() {
            return switch (mode) {
                case MATCH -> base;
                case FIXED -> value;
                case PLUS -> Math.max(1, base + value);
            };
        }
    }

    private static LevelPolicy readLevelPolicy(Set<String> tags, BattleActor actor) {
        int base = 0;
        try {
            // Use the first team member's level as base when available
            if (!actor.getPokemonList().isEmpty() && actor.getPokemonList().get(0) != null) {
                Pokemon p = actor.getPokemonList().get(0).getEffectedPokemon();
                if (p != null) base = p.getLevel();
            }
        } catch (Throwable ignored) {}

        // Fixed level
        int fixed = scanNumberTag(tags, TAG_LVL_FIXED_PREFIX, -1);
        if (fixed > 0) return new LevelPolicy(LevelPolicy.LevelMode.FIXED, fixed, base);

        // Plus offset
        int plus = scanNumberTag(tags, TAG_LVL_PLUS_PREFIX, Integer.MIN_VALUE);
        if (plus != Integer.MIN_VALUE) return new LevelPolicy(LevelPolicy.LevelMode.PLUS, plus, base);

        // Match policy (default)
        return new LevelPolicy(LevelPolicy.LevelMode.MATCH, 0, base);
    }

    private static int scanNumberTag(Set<String> tags, String prefix, int defaultVal) {
        for (String t : tags) {
            if (t.startsWith(prefix)) {
                try {
                    String n = t.substring(prefix.length());
                    return Integer.parseInt(n);
                } catch (Exception ignored) {}
            }
        }
        return defaultVal;
    }

    private static void appendShadowPokemon(BattleActor actor, Entity entity, int count, LevelPolicy lvl, ResourceLocation poolId, boolean unique, long battleSalt) {
        List<BattlePokemon> list = actor.getPokemonList();
        int free = PARTY_MAX - list.size();
        if (free <= 0) return;
        int toAdd = Math.min(count, free);
        if (toAdd <= 0) return;
        System.out.println("Attempting to add " + toAdd + " shadows to " + entity.getName().getString());
        // Try datapack pool first
        if (poolId != null) {
            var created = createFromPool(actor, entity, poolId, toAdd, lvl, unique, battleSalt);
            if (!created.isEmpty()) {
                for (BattlePokemon bp : created) {
                    bp.setActor(actor);
                    list.add(bp);
                }
                return;
            }
            // Log a warning if pool was empty or not found
            Cobblemon.LOGGER.warn("[ShadowedHearts] NPC {} requested shadow pool {} but it was missing or empty; falling back to cloning team.", entity.getUUID(), poolId);
        }

        // Fallback: Use existing team species as candidates; cycle through them
        int idx = 0;
        for (int i = 0; i < toAdd; i++) {
            if (list.isEmpty()) break;
            BattlePokemon source = list.get(idx % list.size());
            idx++;
            if (source == null || source.getEffectedPokemon() == null) continue;
            BattlePokemon clone = BattlePokemon.Companion.safeCopyOf(source.getEffectedPokemon());
            Pokemon p = clone.getEffectedPokemon();
            forceShadow(p);
            try { p.setLevel(Math.max(1, lvl.resolve())); } catch (Throwable ignored) {}
            PokemonAspectUtil.ensureRequiredShadowAspects(p);
            // If unique, avoid duplicating species already present after injection
            if (unique && speciesAlreadyPresent(list, safeSpeciesId(p))) {
                // skip adding this one; try next source slot
                i--; // keep attempt count towards toAdd
                continue;
            }
            clone.setActor(actor);
            list.add(clone);
        }
    }

    private static void replaceWithShadowPokemon(BattleActor actor, Entity entity, int count, LevelPolicy lvl, ResourceLocation poolId, boolean unique, long battleSalt) {
        List<BattlePokemon> list = actor.getPokemonList();
        if (list.isEmpty() || count <= 0) return;
        int replaced = 0;

        // If a pool exists, pre-create up to 'count' candidates from pool
        List<BattlePokemon> poolCandidates = null;
        if (poolId != null) {
            poolCandidates = createFromPool(actor, entity, poolId, count, lvl, unique, battleSalt);
            if (poolCandidates.isEmpty()) {
                Cobblemon.LOGGER.warn("[ShadowedHearts] NPC {} requested shadow pool {} but it was missing or empty; falling back to cloning team.", entity.getUUID(), poolId);
            }
        }

        for (int i = 0; i < list.size() && replaced < count; i++) {
            BattlePokemon bp = list.get(i);
            if (bp == null || bp.getEffectedPokemon() == null) continue;
            if (bp.getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)) continue; // keep existing shadows

            BattlePokemon clone;
            if (poolCandidates != null && replaced < poolCandidates.size()) {
                clone = poolCandidates.get(replaced);
            } else {
                // Fallback: clone existing species
                clone = BattlePokemon.Companion.safeCopyOf(bp.getEffectedPokemon());
                Pokemon p = clone.getEffectedPokemon();
                forceShadow(p);
                try { p.setLevel(Math.max(1, lvl.resolve())); } catch (Throwable ignored) {}
                PokemonAspectUtil.ensureRequiredShadowAspects(p);
            }
            if (unique) {
                // Avoid duplicates vs current party (including this slot being replaced)
                String speciesId = safeSpeciesId(clone.getEffectedPokemon());
                if (speciesAlreadyPresent(list, speciesId)) {
                    continue; // skip replacing this slot with a duplicate
                }
            }
            clone.setActor(actor);
            list.set(i, clone);
            replaced++;
        }
    }

    private static void forceShadow(Pokemon p) {
        if (p == null) return;
        try {
            var forced = p.getForcedAspects();
            // Create a mutable copy to avoid UnsupportedOperationException from immutable sets (e.g., EmptySet)
            java.util.HashSet<String> mutable = forced == null ? new java.util.HashSet<>() : new java.util.HashSet<>(forced);
            if (!mutable.contains(SHAspects.SHADOW)) {
                mutable.add(SHAspects.SHADOW);
                p.setForcedAspects(mutable);
            }
            // Ensure Shadow-injected Pokémon are catchable in battle by removing the 'uncatchable' flag
            try { UncatchableProperty.INSTANCE.catchable().apply(p); } catch (Throwable ignored) {}
            // Refresh aspects after potential changes
            try { p.updateAspects(); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            // Last resort: set exactly the shadow aspect
            java.util.HashSet<String> fallback = new java.util.HashSet<>();
            fallback.add(SHAspects.SHADOW);
            try { p.setForcedAspects(fallback); } catch (Throwable ignored) {}
            try { UncatchableProperty.INSTANCE.catchable().apply(p); } catch (Throwable ignored) {}
            try { p.updateAspects(); } catch (Throwable ignored) {}
        }
    }

    private static ResourceLocation readPoolId(Set<String> tags) {
        for (String t : tags) {
            if (t.startsWith(TAG_POOL_PREFIX)) {
                String rest = t.substring(TAG_POOL_PREFIX.length());
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    String ns = rest.substring(0, slash);
                    String path = rest.substring(slash + 1);
                    try { return new ResourceLocation(ns, path); } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private static List<BattlePokemon> createFromPool(BattleActor actor, Entity entity, ResourceLocation poolId, int count, LevelPolicy lvl, boolean unique, long battleSalt) {
        try {
            Entity e = entity;
            MinecraftServer server = entity.level().getServer();
            if (server == null) return List.of();
            var entries = ShadowPools.get(server, poolId);
            if (entries.isEmpty()) return List.of();
            // Seed RNG for determinism per NPC + world + battle
            Random rng = makeRng(e, battleSalt);

            // Build unique set of species already present if needed
            java.util.HashSet<String> present = unique ? collectSpeciesIds(actor.getPokemonList()) : null;

            java.util.ArrayList<PokemonProperties> propsList = new java.util.ArrayList<>();
            int attempts = 0;
            while (propsList.size() < count && attempts < count * 10) {
                attempts++;
                var pick = ShadowPools.pick(rng, entries, 1);
                if (pick.isEmpty()) break;
                PokemonProperties candidate = pick.get(0);
                if (unique) {
                    String speciesId = safeSpeciesId(candidate);
                    if (present.contains(speciesId)) continue;
                    present.add(speciesId);
                }
                propsList.add(candidate);
            }
            java.util.ArrayList<BattlePokemon> out = new java.util.ArrayList<>();
            for (PokemonProperties props : propsList) {
                PokemonProperties copy = props.copy();
                try {
                    if (copy.getLevel() == null || copy.getLevel() <= 0) copy.setLevel(Math.max(1, lvl.resolve()));
                } catch (Throwable ignored) {}
                var created = copy.create();
                BattlePokemon bp = BattlePokemon.Companion.safeCopyOf(created);
                Pokemon p = bp.getEffectedPokemon();
                forceShadow(p);
                PokemonAspectUtil.ensureRequiredShadowAspects(p);
                out.add(bp);
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static Random makeRng(Entity e, long battleSalt) {
        long worldSeed = 0L;
        if (e.level() instanceof ServerLevel sl) {
            worldSeed = sl.getSeed();
        }
        long seed = worldSeed ^ e.getUUID().getMostSignificantBits() ^ e.getUUID().getLeastSignificantBits() ^ battleSalt;
        return new Random(seed);
    }

    private static int readConvertChance(Set<String> tags) {
        for (String t : tags) {
            if (t.startsWith(TAG_CONVERT_CHANCE_PREFIX)) {
                try {
                    int p = Integer.parseInt(t.substring(TAG_CONVERT_CHANCE_PREFIX.length()));
                    if (p < 0) p = 0; if (p > 100) p = 100;
                    return p;
                } catch (Exception ignored) {}
            }
        }
        return -1; // negative means not set
    }

    private static boolean speciesAlreadyPresent(List<BattlePokemon> list, String speciesId) {
        if (speciesId == null) return false;
        for (BattlePokemon bp : list) {
            if (bp == null || bp.getEffectedPokemon() == null) continue;
            if (speciesId.equalsIgnoreCase(safeSpeciesId(bp.getEffectedPokemon()))) return true;
        }
        return false;
    }

    private static java.util.HashSet<String> collectSpeciesIds(List<BattlePokemon> list) {
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (BattlePokemon bp : list) {
            if (bp == null || bp.getEffectedPokemon() == null) continue;
            String id = safeSpeciesId(bp.getEffectedPokemon());
            if (id != null) set.add(id);
        }
        return set;
    }

    private static String safeSpeciesId(PokemonProperties props) {
        try { return props.getSpecies(); } catch (Throwable t) { return null; }
    }

    private static String safeSpeciesId(Pokemon p) {
        try { return p.getSpecies() != null ? p.getSpecies().showdownId() : null; } catch (Throwable t) { return null; }
    }

    // === Shadow move assignment for converted Pokémon (convert mode) ===
    private static void assignShadowMoves(Pokemon pokemon, Random rng) {
        if (pokemon == null) return;
        // Pick 1 or 2 distinct shadow moves
        int count = 1 + (rng == null ? new Random() : rng).nextInt(2); // 1..2
        String first = pickShadow(null, null, rng);
        String second = count == 2 ? pickShadow(null, first, rng) : null;

        // Place into slots 0 and 1 (do not clear other known moves)
        if (first != null) {
            var tmpl = Moves.INSTANCE.getByNameOrDummy(first);
            pokemon.getMoveSet().setMove(0, tmpl.create(tmpl.getPp(), 0));
        }
        if (second != null) {
            var tmpl = Moves.INSTANCE.getByNameOrDummy(second);
            pokemon.getMoveSet().setMove(1, tmpl.create(tmpl.getPp(), 0));
        }
    }

    private static String pickShadow(java.util.List<String> ids, String exclude, Random rng) {
        // Use provided list if not null; otherwise use built-in IDs
        java.util.List<String> pool;
        if (ids != null) {
            pool = ids;
        } else {
            pool = new java.util.ArrayList<>(SHADOW_IDS.length);
            for (String id : SHADOW_IDS) pool.add(id);
        }
        if (pool.isEmpty()) return null;
        Random r = rng == null ? new Random() : rng;
        int tries = 0;
        while (tries++ < 8) {
            String id = pool.get(r.nextInt(pool.size()));
            if (exclude == null || !exclude.equalsIgnoreCase(id)) return id;
        }
        return pool.get(0);
    }

    private static final String[] SHADOW_IDS = new String[]{
            "shadowblast", "shadowblitz", "shadowbolt", "shadowbreak", "shadowchill",
            "shadowdown", "shadowend", "shadowfire", "shadowhalf", "shadowhold",
            "shadowmist", "shadowpanic", "shadowrave", "shadowrush", "shadowshed",
            "shadowsky", "shadowstorm", "shadowwave"
    };
}
