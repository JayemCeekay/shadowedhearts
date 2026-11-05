package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.utils.AspectUtils;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.integration.lostlore.LostLoreShadowMoves;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.*;

@Mixin(value = AspectUtils.class, remap = false)
public abstract class MixinAspectUtils {

    /**
     * Wraps applyProperties to preserve shadow moves and shadow forced aspects across the call.
     * Keeps existing Shadow moves through property changes and move validation.
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
     * Lost Lore's Shadow Energy applies sh_shadow before MSD cleans the moveset.
     * Capture here so the protection includes moves assigned during that conversion.
     * Newer MSD versions removed this cleaner; the public wrappers still apply there.
     */
    @WrapOperation(method = "applyProperties", at = @At(value = "INVOKE",
            target = "Lcom/github/yajatkaul/mega_showdown/utils/AspectUtils;cleanMoveset(Lcom/cobblemon/mod/common/pokemon/Pokemon;)V"),
            require = 0)
    private static void shadowedhearts$preserveNewlyAssignedShadowMoves(Pokemon pokemon, Operation<Void> original) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) {
            original.call(pokemon);
            return;
        }
        Move[] savedSlots = saveShadowSlots(pokemon);
        List<BenchedMove> savedBenched = saveShadowBenched(pokemon);
        original.call(pokemon);
        restoreShadowMoves(pokemon, savedSlots, savedBenched);
    }

    /**
     * Wraps applyAspects to preserve shadow moves and shadow forced aspects across the call.
     * applyAspects calls cleanMoveset which strips moves not in the form's learnset —
     * assigned Shadow moves may not be in that learnset. We save them before
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
            if (ShadowMoveUtil.isShadowMove(m)) {
                saved[i] = m.copy();
            }
        }
        return saved;
    }

    private static List<BenchedMove> saveShadowBenched(Pokemon pokemon) {
        List<BenchedMove> saved = new ArrayList<>();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (ShadowMoveUtil.isShadowMove(bm.getMoveTemplate())) {
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
        Set<String> preferredIds = new HashSet<>();
        LostLoreShadowMoves.getPreferredMoves(pokemon).forEach(move -> preferredIds.add(move.getName()));
        List<BenchedMove> toRestoreBenched = new ArrayList<>(savedBenched);
        // Restore active shadow move slots
        MoveSet moveSet = pokemon.getMoveSet();
        moveSet.doWithoutEmitting(() -> {
            for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
                if (savedSlots[i] != null) {
                    Move current = moveSet.get(i);
                    if (current != null && preferredIds.contains(current.getTemplate().getName())
                            && !current.getTemplate().getName().equals(savedSlots[i].getTemplate().getName())) {
                        // A property conversion deliberately installed the form's authored move here.
                        // Preserve the old move on the bench instead of undoing that assignment.
                        toRestoreBenched.add(new BenchedMove(savedSlots[i].getTemplate(), savedSlots[i].getRaisedPpStages()));
                        continue;
                    }
                    moveSet.setMove(i, savedSlots[i]);
                }
            }
            return null;
        });

        // Restore benched shadow moves that were removed
        BenchedMoves benchedMoves = pokemon.getBenchedMoves();
        benchedMoves.doWithoutEmitting(() -> {
            for (BenchedMove bm : toRestoreBenched) {
                if (!preferredIds.isEmpty() && moveSet.getMoves().stream().anyMatch(move ->
                        move.getTemplate().getName().equals(bm.getMoveTemplate().getName()))) continue;
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
