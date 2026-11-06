package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.network.aura.AuraServerSync;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers fresh spawns without replaying their reveal during saved loads or dimension transfers. */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelAura {
    @Inject(method = {"addFreshEntity", "addWithUUID"}, at = @At("RETURN"))
    private void shadowedhearts$trackFreshSpawnAura(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof PokemonEntity pokemon) {
            AuraServerSync.onEntityAdded(pokemon);
        }
    }
}
