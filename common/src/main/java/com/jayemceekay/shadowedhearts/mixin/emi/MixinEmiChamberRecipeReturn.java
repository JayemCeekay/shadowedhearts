package com.jayemceekay.shadowedhearts.mixin.emi;

import com.jayemceekay.shadowedhearts.integration.recipeviewer.emi.EmiChamberReturn;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/** Other origins and every nested native recipe/tree transition retain EMI's original implementation. */
@Pseudo
@Mixin(targets = "dev.emi.emi.api.EmiApi", remap = false)
public abstract class MixinEmiChamberRecipeReturn {
    @Inject(method = "setPages", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$openRecipes(Map<EmiRecipeCategory, List<EmiRecipe>> recipes,
                                                   EmiIngredient stack, CallbackInfo ci) {
        if (EmiChamberReturn.tryOpenRecipes(recipes, stack)) ci.cancel();
    }

    @Inject(method = "viewRecipeTree", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$openTree(CallbackInfo ci) {
        if (EmiChamberReturn.tryOpenTree()) ci.cancel();
    }
}
