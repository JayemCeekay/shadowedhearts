package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attach the TossOrderGoal to Cobblemon Pokemon entities by injecting into Mob.registerGoals.
 */
@Mixin(Mob.class)
public abstract class MixinMob_TossGoals {

    @Shadow protected GoalSelector goalSelector;

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void shadowedhearts$addTossOrderGoal(CallbackInfo ci) {
        // Only attach to Cobblemon Pokémon
        if (((Object) this) instanceof PokemonEntity) {
            Mob self = (Mob) (Object) this;
            this.goalSelector.addGoal(2, new com.jayemceekay.shadowedhearts.poketoss.ai.TossOrderGoal(self));
        }
    }
}
