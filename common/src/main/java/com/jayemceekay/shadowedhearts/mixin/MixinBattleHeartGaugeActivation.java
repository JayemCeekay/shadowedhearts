package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.jayemceekay.shadowedhearts.common.event.battle.BattleSentOnceListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ActiveBattlePokemon.class, remap = false)
public abstract class MixinBattleHeartGaugeActivation {
    @Inject(method = "setBattlePokemon(Lcom/cobblemon/mod/common/battles/pokemon/BattlePokemon;)V",
            at = @At("TAIL"), remap = false, require = 1)
    private void shadowedhearts$creditAlreadySentOutPokemon(BattlePokemon pokemon, CallbackInfo ci) {
        // Covers initial and later switch-ins that reuse an entity without POKEMON_SENT_POST.
        BattleSentOnceListener.onBattlePokemonActivated((ActiveBattlePokemon) (Object) this);
    }
}
