package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attach the TossOrderActivity (Brain-based) to Cobblemon Pokemon entities by injecting after Mob.registerGoals.
 */
@Mixin(Mob.class)
public abstract class MixinMob_TossGoals {

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void shadowedhearts$installTossOrderActivity(CallbackInfo ci) {
        // Only attach to Cobblemon Pokémon
        if (((Object) this) instanceof PokemonEntity) {
            Mob self = (Mob) (Object) this;
            // Install our Brain-driven behavior into CORE activity
            //TossOrderActivity.install(self);
        }
    }
}
