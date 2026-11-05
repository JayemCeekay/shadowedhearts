package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BattleRegistry.class, remap = false)
public abstract class MixinVoidShadowBattleStartup {
    @WrapMethod(method = "startShowdown(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;)V", remap = false, require = 1)
    private void shadowedhearts$prepareVoidShadow(PokemonBattle battle, Operation<Void> original) {
        try {
            VoidShadowService.prepareBattle(battle);
            original.call(battle);
        } catch (RuntimeException | Error failure) {
            VoidShadowService.abortBattle(battle);
            throw failure;
        }
    }
}
