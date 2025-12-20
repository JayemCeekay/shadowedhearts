package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.dispatch.DispatchResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PokemonBattle.class, remap = false)
public abstract class MixinPokemonBattle {

    @Shadow
    public abstract void dispatch(@NotNull Function0<? extends DispatchResult> dispatcher);

    @WrapMethod(method = "dispatchWaiting")
    public void shadowedhearts$dispatchWaiting(float delaySeconds, Function0<Unit> dispatcher, Operation<Void> original) {
        /*if (AutoBattleController.isOneTurn(((PokemonBattle) (Object) this).getBattleId())) {
            if (delaySeconds >= 1.0F) {
                dispatcher.invoke();
                this.dispatch(() -> new WaitDispatch(0.5F));
            }
        } else {
            original.call(delaySeconds, dispatcher);
        }*/
    }
}
