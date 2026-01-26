package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.ShowdownMovesetAdapter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = ShowdownMovesetAdapter.class, remap = false)
public abstract class MixinShowdownMovesetAdapter {

    @Inject(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lcom/cobblemon/mod/common/battles/ShowdownMoveset;",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/battles/ShowdownMoveset;setGimmickMapping()Lkotlin/Unit;")
    )
    private void shadowedhearts$beforeSetGimmickMapping(
            JsonElement jsonElement,
            Type type,
            JsonDeserializationContext context,
            CallbackInfoReturnable<ShowdownMoveset> cir,
            @Local(name = "moveset") ShowdownMoveset moveset
    ) {
        if (moveset.getMoves() != null) {
            List<InBattleMove> filteredMoves = new ArrayList<>();
            for (InBattleMove move : moveset.getMoves()) {
                if (!ShadowGate.isShadowMoveId(move.getId())) {
                    filteredMoves.add(move);
                }
            }
            moveset.setMoves(filteredMoves);
        }
    }
}