package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinPokemon {

    @Shadow
    public abstract boolean isPlayerOwned();

    @Shadow
    public abstract boolean isNPCOwned();

    @Inject(method = "isWild", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$isWildShadowCheck(CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            if (isPlayerOwned()) {
                cir.setReturnValue(false);
            } else if (isNPCOwned()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "validateMoveset$lambda$0$0", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/moves/MoveSet;setMove(ILcom/cobblemon/mod/common/api/moves/Move;)V"), cancellable = true)
    private static void shadowedhearts$preventShadowMoveRemoval(Pokemon this$0, LearnsetQuery $query, CallbackInfoReturnable<Unit> cir, @Local(name = "move") Move move) {
        if (move.getType().equals(ElementalTypes.get("shadow"))) {
            cir.cancel();
        }

    }

    @Inject(method = "setFriendship(IZ)Z", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventSetFriendship(int value, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "incrementFriendship", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventIncrementFriendship(int amount, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "decrementFriendship", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventDecrementFriendship(int amount, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateMovesOnFormChange", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$preventShadowMoveRemovalOnFormChange(FormData newForm, CallbackInfo ci) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (pokemon.getAspects().contains(SHAspects.SHADOW)) {
            ci.cancel();
        }
    }

}
