package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ShowdownMovesetAdapter;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ShowdownMovesetAdapter.class, remap = false)
public abstract class MixinShowdownMovesetAdapter {

    @WrapOperation(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/cobblemon/mod/common/battles/ShowdownMoveset;",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/battles/ShowdownMoveset;setGimmickMapping()Lkotlin/Unit;")
    )
    private Unit shadowedhearts$wrapSetGimmickMapping(ShowdownMoveset instance, Operation<Unit> original) {
        List<InBattleMove> originalMoves = instance.getMoves();
        if (originalMoves != null) {
            ///instance.setMoves(shadowedhearts$getFilteredMoves(originalMoves));
            Unit result = original.call(instance);
            instance.setMoves(originalMoves);
            return result;
        }
        return original.call(instance);
    }

    @WrapOperation(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/cobblemon/mod/common/battles/ShowdownMoveset;",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/battles/ShowdownMoveset;setMaxMoves(Ljava/util/List;)V")
    )
    private void shadowedhearts$wrapSetMaxMoves(ShowdownMoveset instance, List<?> maxMoves, Operation<Void> original) {
        List<InBattleMove> originalMoves = instance.getMoves();
        if (originalMoves != null) {
            //instance.setMoves(shadowedhearts$getFilteredMoves(originalMoves));
            original.call(instance, maxMoves);
            instance.setMoves(originalMoves);
        } else {
            original.call(instance, maxMoves);
        }
    }

    private List<InBattleMove> shadowedhearts$getFilteredMoves(List<InBattleMove> moves) {
        List<InBattleMove> filteredMoves = new ArrayList<>();
        for (InBattleMove move : moves) {
            if (!ShadowGate.isShadowMoveId(move.getId())) {
                filteredMoves.add(move);
            }
        }
        return filteredMoves;
    }
}