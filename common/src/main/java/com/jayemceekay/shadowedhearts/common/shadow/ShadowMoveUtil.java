package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

import java.util.*;

public class ShadowMoveUtil {
    public static final String[] SHADOW_IDS = new String[]{
            "shadowblast", "shadowblitz", "shadowbolt", "shadowbreak", "shadowchill",
            "shadowdown", "shadowend", "shadowfire", "shadowhalf", "shadowhold",
            "shadowmist", "shadowpanic", "shadowrave", "shadowrush", "shadowshed",
            "shadowsky", "shadowstorm", "shadowwave"
    };

    public static String getRandomShadowMove(Random rng, boolean damageOnly) {
        List<String> pool = new ArrayList<>(List.of(SHADOW_IDS));
        if (damageOnly) {
            List<String> damageMoves = new ArrayList<>();
            for (String id : pool) {
                var tmpl = Moves.INSTANCE.getByNameOrDummy(id);
                if (tmpl.getDamageCategory() != com.cobblemon.mod.common.api.moves.categories.DamageCategories.INSTANCE.getSTATUS()) {
                    damageMoves.add(id);
                }
            }
            if (damageMoves.isEmpty()) return "shadowrush";
            return damageMoves.get(rng.nextInt(damageMoves.size()));
        }
        return SHADOW_IDS[rng.nextInt(SHADOW_IDS.length)];
    }

    public static void assignShadowMoves(Pokemon pokemon) {
        assignShadowMoves(pokemon, new Random(pokemon.getUuid().getLeastSignificantBits()));
    }

    public static void assignShadowMoves(Pokemon pokemon, Random rng) {
        if (pokemon == null) return;

        Random r = (rng == null ? new Random() : rng);
        int count = ModConfig.resolveReplaceCount(r);
        List<String> pool = new ArrayList<>(Arrays.asList(SHADOW_IDS));

        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowMovesOnlyShadowRush()) {
            count = 1;
            pool = new ArrayList<>(List.of("shadowrush"));
        }

        var moveSet = pokemon.getMoveSet();
        List<Integer> shadowMoveSlots = new ArrayList<>();
        List<Integer> nonShadowMoveSlots = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            var move = moveSet.get(i);
            if (move != null) {
                if (move.getType() == Shadowedhearts.SH_SHADOW_TYPE) {
                    shadowMoveSlots.add(i);
                } else {
                    nonShadowMoveSlots.add(i);
                }
            } else {
                nonShadowMoveSlots.add(i);
            }
        }

        // 1. If we already have enough shadow moves, we're done.
        if (shadowMoveSlots.size() >= count) {
            return;
        }

        // 2. Try to pull shadow moves from the bench
        var benchedMoves = pokemon.getBenchedMoves();
        List<BenchedMove> benchedShadowMoves = new ArrayList<>();
        for (var bm : benchedMoves) {
            if (bm.getMoveTemplate().getElementalType() == Shadowedhearts.SH_SHADOW_TYPE) {
                benchedShadowMoves.add(bm);
            }
        }

        int needed = count - shadowMoveSlots.size();
        for (var bsm : benchedShadowMoves) {
            if (needed <= 0) break;
            if (nonShadowMoveSlots.isEmpty()) break;

            int slotToOverwrite = nonShadowMoveSlots.remove(0);
            var template = bsm.getMoveTemplate();
            moveSet.setMove(slotToOverwrite, template.create(template.getPp(), bsm.getPpRaisedStages()));
            pokemon.getBenchedMoves().remove(template);
            shadowMoveSlots.add(slotToOverwrite);
            needed--;
        }

        // 3. Inject new shadow moves if still needed
        if (needed > 0) {
            // Remove already present shadow moves from pool to avoid duplicates
            for (int slot : shadowMoveSlots) {
                var move = moveSet.get(slot);
                if (move != null) {
                    pool.remove(move.getTemplate().getName().toLowerCase(Locale.ROOT));
                }
            }

            for (int i = 0; i < needed; i++) {
                if (nonShadowMoveSlots.isEmpty()) break;
                if (pool.isEmpty()) break;

                String moveId = (shadowMoveSlots.isEmpty()) ? pickDamageShadow(pool, null, r) : pickShadow(pool, null, r);
                if (moveId != null) {
                    var tmpl = Moves.INSTANCE.getByNameOrDummy(moveId);
                    int slotToOverwrite = nonShadowMoveSlots.remove(0);
                    var oldMove = moveSet.get(slotToOverwrite);
                    if (oldMove != null && oldMove.getType() != Shadowedhearts.SH_SHADOW_TYPE) {
                        pokemon.getBenchedMoves().add(new BenchedMove(oldMove.getTemplate(), oldMove.getRaisedPpStages()));
                    }
                    moveSet.setMove(slotToOverwrite, tmpl.create(tmpl.getPp(), 0));
                    pool.remove(moveId);
                    shadowMoveSlots.add(slotToOverwrite);
                }
            }
        }
    }

    public static String pickDamageShadow(List<String> ids, String exclude, Random rng) {
        List<String> damageMoves = new ArrayList<>();
        for (String id : ids) {
            var tmpl = Moves.INSTANCE.getByNameOrDummy(id);
            if (tmpl.getDamageCategory() != com.cobblemon.mod.common.api.moves.categories.DamageCategories.INSTANCE.getSTATUS()) {
                damageMoves.add(id);
            }
        }
        return pickShadow(damageMoves, exclude, rng);
    }

    public static String pickShadow(List<String> ids, String exclude, Random rng) {
        if (ids == null || ids.isEmpty()) return null;
        int tries = 0;
        while (tries++ < 8) {
            String id = ids.get(rng.nextInt(ids.size()));
            if (exclude == null || !exclude.equalsIgnoreCase(id)) return id;
        }
        return ids.get(0);
    }

    public static boolean isShadowMove(Move move) {
        return move != null && move.getType() == Shadowedhearts.SH_SHADOW_TYPE;
    }

    public static boolean isShadowMove(MoveTemplate template) {
        return template != null && template.getElementalType() == Shadowedhearts.SH_SHADOW_TYPE;
    }

    public static boolean isShadowMoveId(String moveId) {
        if (moveId == null) return false;
        var tmpl = Moves.INSTANCE.getByNameOrDummy(moveId);
        return tmpl.getElementalType() == Shadowedhearts.SH_SHADOW_TYPE;
    }
}
