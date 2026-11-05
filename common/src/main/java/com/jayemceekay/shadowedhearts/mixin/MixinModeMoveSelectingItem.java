package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.item.PokemonAndMoveSelectingItem;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowModeItemPolicy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PokemonAndMoveSelectingItem.class, remap = false)
public interface MixinModeMoveSelectingItem {
    @Inject(method = "applyToBattlePokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$battleRefusal(ServerPlayer player, ItemStack stack, BattlePokemon pokemon, Move move, CallbackInfo ci) {
        if (ShadowModeItemPolicy.reject(player, pokemon.getEffectedPokemon(), stack)) ci.cancel();
    }

    @Inject(method = "canUseOnBattlePokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$battleEligibility(ItemStack stack, BattlePokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (ShadowModeItemPolicy.refuses(pokemon.getEffectedPokemon(), stack)) cir.setReturnValue(false);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/item/PokemonAndMoveSelectingItem;applyToPokemon(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/cobblemon/mod/common/pokemon/Pokemon;Lcom/cobblemon/mod/common/api/moves/Move;)V"), remap = true, require = 2)
    private static void shadowedhearts$outsideRefusal(PokemonAndMoveSelectingItem item, ServerPlayer player, ItemStack stack, Pokemon pokemon, Move move, Operation<Void> original) {
        if (!ShadowModeItemPolicy.reject(player, pokemon, stack)) original.call(item, player, stack, pokemon, move);
    }
}
