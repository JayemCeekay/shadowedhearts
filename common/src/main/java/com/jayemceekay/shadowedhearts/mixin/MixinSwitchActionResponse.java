package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(value = SwitchActionResponse.class, remap = false)
public abstract class MixinSwitchActionResponse {
    @Shadow
    public abstract UUID getNewPokemonId();

    @WrapMethod(method = "isValid(Lcom/cobblemon/mod/common/battles/ActiveBattlePokemon;"
            + "Lcom/cobblemon/mod/common/battles/ShowdownMoveset;Z)Z", remap = false, require = 1)
    private boolean shadowedhearts$excludeCapturedSwitch(ActiveBattlePokemon activeBattlePokemon,
                                                        ShowdownMoveset showdownMoveset,
                                                        boolean forceSwitch,
                                                        Operation<Boolean> original) {
        UUID targetId = getNewPokemonId();
        for (var pokemon : activeBattlePokemon.getActor().getPokemonList()) {
            if (pokemon.getUuid().equals(targetId)) {
                if (pokemon.getGone()) return false;
                break;
            }
        }
        // Do not call canBeSentOut(): a valid AI choice may already be reserved for switching in.
        return original.call(activeBattlePokemon, showdownMoveset, forceSwitch);
    }
}
