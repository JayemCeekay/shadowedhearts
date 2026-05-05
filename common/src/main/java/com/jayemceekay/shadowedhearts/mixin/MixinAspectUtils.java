package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.utils.AspectUtils;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import java.util.*;

@Mixin(value = AspectUtils.class, remap = false)
public abstract class MixinAspectUtils {

    /**
     * Wraps applyProperties to preserve shadow moves and shadow forced aspects across the call.
     * applyProperties calls cleanMoveset which strips moves not in the form's learnset —
     * shadow moves are never in any learnset so they get removed. We save them before
     * and restore them after.
     */
    @WrapMethod(method = "applyProperties")
    private static void shadowedhearts$wrapApplyProperties(Pokemon pokemon, Optional<String> propertyString, Operation<Void> original) {
        Move[] savedSlots = saveShadowSlots(pokemon);
        List<BenchedMove> savedBenched = saveShadowBenched(pokemon);
        Set<String> savedForcedShadowAspects = saveShadowForcedAspects(pokemon);
        original.call(pokemon, propertyString);
        restoreShadowForcedAspects(pokemon, savedForcedShadowAspects);
        restoreShadowMoves(pokemon, savedSlots, savedBenched);
        ShadowAspectUtil.syncAspects(pokemon);
        ShadowAspectUtil.syncMoveSet(pokemon);
        ShadowAspectUtil.syncBenchedMoves(pokemon);
    }

    /**
     * Wraps applyAspects to preserve shadow moves and shadow forced aspects across the call.
     * applyAspects calls cleanMoveset which strips moves not in the form's learnset —
     * shadow moves are never in any learnset so they get removed. We save them before
     * and restore them after. Additionally, the shadow aspect itself may be lost from
     * forcedAspects during form changes triggered by mega evolution, so we preserve
     * and restore it as well.
     */
    @WrapMethod(method = "applyAspects")
    private static void shadowedhearts$wrapApplyAspects(Pokemon pokemon, List<String> aspects, Operation<Void> original) {
        Move[] savedSlots = saveShadowSlots(pokemon);
        List<BenchedMove> savedBenched = saveShadowBenched(pokemon);
        Set<String> savedForcedShadowAspects = saveShadowForcedAspects(pokemon);
        original.call(pokemon, aspects);
        restoreShadowForcedAspects(pokemon, savedForcedShadowAspects);
        restoreShadowMoves(pokemon, savedSlots, savedBenched);
        // Explicit sync with snapshots to avoid race conditions
        ShadowAspectUtil.syncAspects(pokemon);
        ShadowAspectUtil.syncMoveSet(pokemon);
        ShadowAspectUtil.syncBenchedMoves(pokemon);
    }

    private static Move[] saveShadowSlots(Pokemon pokemon) {
        MoveSet moveSet = pokemon.getMoveSet();
        Move[] saved = new Move[MoveSet.MOVE_COUNT];
        for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
            Move m = moveSet.get(i);
            if (m != null && m.getType().equals(ElementalTypes.get("shadow"))) {
                saved[i] = m.copy();
            }
        }
        return saved;
    }

    private static List<BenchedMove> saveShadowBenched(Pokemon pokemon) {
        List<BenchedMove> saved = new ArrayList<>();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (bm.getMoveTemplate().getElementalType().equals(ElementalTypes.get("shadow"))) {
                saved.add(bm);
            }
        }
        return saved;
    }

    /**
     * Saves all shadow-related forced aspects from the pokemon before the mega evolution
     * operation potentially strips them.
     */
    private static Set<String> saveShadowForcedAspects(Pokemon pokemon) {
        Set<String> saved = new HashSet<>();
        for (String aspect : pokemon.getForcedAspects()) {
            if (aspect.startsWith("shadowedhearts:")) {
                saved.add(aspect);
            }
        }
        return saved;
    }

    /**
     * Restores shadow-related forced aspects that were present before the operation
     * but may have been stripped during form/aspect changes.
     */
    private static void restoreShadowForcedAspects(Pokemon pokemon, Set<String> savedAspects) {
        if (savedAspects.isEmpty()) return;
        Set<String> currentForced = pokemon.getForcedAspects();
        boolean needsRestore = false;
        for (String aspect : savedAspects) {
            if (!currentForced.contains(aspect)) {
                needsRestore = true;
                break;
            }
        }
        if (needsRestore) {
            Set<String> merged = new HashSet<>(currentForced);
            merged.addAll(savedAspects);
            pokemon.setForcedAspects(merged);
            pokemon.updateAspects();
        }
    }

    private static void restoreShadowMoves(Pokemon pokemon, Move[] savedSlots, List<BenchedMove> savedBenched) {
        // Restore active shadow move slots
        MoveSet moveSet = pokemon.getMoveSet();
        moveSet.doWithoutEmitting(() -> {
            for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
                if (savedSlots[i] != null) {
                    moveSet.setMove(i, savedSlots[i]);
                }
            }
            return null;
        });

        // Restore benched shadow moves that were removed
        BenchedMoves benchedMoves = pokemon.getBenchedMoves();
        benchedMoves.doWithoutEmitting(() -> {
            for (BenchedMove bm : savedBenched) {
                boolean present = false;
                for (BenchedMove existing : benchedMoves) {
                    if (existing.getMoveTemplate() == bm.getMoveTemplate()) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    benchedMoves.add(bm);
                }
            }
            return null;
        });
    }
}
