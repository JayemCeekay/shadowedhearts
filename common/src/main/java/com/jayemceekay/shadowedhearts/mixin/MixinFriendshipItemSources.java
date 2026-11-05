package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowFriendshipSources;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Attribute native item friendship independently from walking and explicit codec assignments. */
@Mixin(targets = {
        "com.cobblemon.mod.common.item.battle.DireHitItem",
        "com.cobblemon.mod.common.item.battle.GuardSpecItem",
        "com.cobblemon.mod.common.item.battle.XStatItem",
        "com.cobblemon.mod.common.item.berry.FriendshipRaisingBerryItem",
        "com.cobblemon.mod.common.item.interactive.EnergyRootItem",
        "com.cobblemon.mod.common.item.interactive.HealPowderItem",
        "com.cobblemon.mod.common.item.interactive.RemedyItem",
        "com.cobblemon.mod.common.item.RevivalHerbItem",
        "com.cobblemon.mod.common.item.PokePuffItem"
}, remap = false)
public abstract class MixinFriendshipItemSources {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;incrementFriendship$default(Lcom/cobblemon/mod/common/pokemon/Pokemon;IZILjava/lang/Object;)Z"), require = 0)
    private static boolean shadowedhearts$itemIncrease(Pokemon pokemon, int amount, boolean coerce, int mask, Object marker,
                                                       Operation<Boolean> original) {
        return ShadowFriendshipSources.withItem(() -> original.call(pokemon, amount, coerce, mask, marker));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;decrementFriendship$default(Lcom/cobblemon/mod/common/pokemon/Pokemon;IZILjava/lang/Object;)Z"), require = 0)
    private static boolean shadowedhearts$itemDecrease(Pokemon pokemon, int amount, boolean coerce, int mask, Object marker,
                                                       Operation<Boolean> original) {
        return ShadowFriendshipSources.withItem(() -> original.call(pokemon, amount, coerce, mask, marker));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;setFriendship$default(Lcom/cobblemon/mod/common/pokemon/Pokemon;IZILjava/lang/Object;)Z"), require = 0)
    private static boolean shadowedhearts$itemSet(Pokemon pokemon, int value, boolean coerce, int mask, Object marker,
                                                  Operation<Boolean> original) {
        return ShadowFriendshipSources.withItem(() -> original.call(pokemon, value, coerce, mask, marker));
    }
}
