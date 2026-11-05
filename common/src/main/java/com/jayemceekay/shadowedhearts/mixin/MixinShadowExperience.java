package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowProgressionManager;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinShadowExperience {
    @WrapOperation(method = "addExperience(Lcom/cobblemon/mod/common/api/pokemon/experience/ExperienceSource;I)Lcom/cobblemon/mod/common/pokemon/AddExperienceResult;",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;setExperience$common(I)V"),
            require = 1, allow = 1)
    private void shadowedhearts$bufferAcceptedExperience(Pokemon pokemon, int experience, Operation<Void> original,
                                                        @Local(name = "appliedXP") LocalIntRef appliedXP) {
        if (!ShadowRestrictionPolicy.buffersExperience(pokemon)) {
            int earned = ShadowProgressionManager.earnedExperience(pokemon, appliedXP.get());
            if (earned == appliedXP.get()) {
                original.call(pokemon, experience);
            } else {
                earned = Math.min(earned, ShadowProgressionManager.experienceCapacity(pokemon, null));
                appliedXP.set(earned);
                original.call(pokemon, pokemon.getExperience() + earned);
            }
            return;
        }
        // PRE cancellation has already returned. Keep real EXP unchanged while native POST,
        // AddExperienceResult and candy consumption receive exactly the accepted buffer gain.
        appliedXP.set(ShadowProgressionManager.bufferExperience(pokemon, appliedXP.get()));
    }
}
