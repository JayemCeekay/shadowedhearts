package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.DefaultMovesetBuilder;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.pokemon.FormData;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowLearnsetMoves;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DefaultMovesetBuilder.class, remap = false)
public abstract class MixinShadowLearnsetBuilder {
    @ModifyExpressionValue(method = "tryAddMoveFromSlot", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getMoves()Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;"))
    private Learnset shadowedhearts$ordinarySelectorMoves(Learnset original, @Local(argsOnly = true) FormData form,
                                                          @Share("learnset") LocalRef<Learnset> selected) {
        if (selected.get() == null) {
            var profile = ShadowLearnsets.getServer(form.getSpecies().getResourceIdentifier());
            selected.set(profile == null ? original : ShadowLearnsetMoves.ordinaryBuilderLearnset(original, profile));
        }
        return selected.get();
    }
}
