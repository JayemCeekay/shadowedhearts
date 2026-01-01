package com.jayemceekay.shadowedhearts.mixin.simpletms;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dragomordor.simpletms.item.api.PokemonSelectingItemNonBattle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PokemonSelectingItemNonBattle.class, remap = false)
public interface MixinPokemonSelectingItemNonBattle {

    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Ldragomordor/simpletms/item/api/PokemonSelectingItemNonBattle;applyToPokemon(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/cobblemon/mod/common/pokemon/Pokemon;)Lnet/minecraft/world/InteractionResultHolder;"))
    private @Nullable InteractionResultHolder<@NotNull ItemStack> shadowedhearts$preventShadowPokemonUse(PokemonSelectingItemNonBattle instance, @NotNull ServerPlayer serverPlayer, @NotNull ItemStack itemStack, @NotNull Pokemon pokemon, Operation<InteractionResultHolder<ItemStack>> original) {
        if(PokemonAspectUtil.hasShadowAspect(pokemon)) {
            serverPlayer.sendSystemMessage(Component.literal("You cannot use this item on a shadow pokemon!").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(itemStack);
        }
        return original.call(instance, serverPlayer, itemStack, pokemon);
    }

    @WrapOperation(method = "interactGeneral$lambda$4", at = @At(value = "INVOKE", target = "Ldragomordor/simpletms/item/api/PokemonSelectingItemNonBattle;applyToPokemon(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/cobblemon/mod/common/pokemon/Pokemon;)Lnet/minecraft/world/InteractionResultHolder;"))
    private static InteractionResultHolder<ItemStack> shadowedhearts$preventShadowPokemonInteract(PokemonSelectingItemNonBattle instance, ServerPlayer serverPlayer, ItemStack itemStack, Pokemon pokemon, Operation<InteractionResultHolder<ItemStack>> original) {
        if(PokemonAspectUtil.hasShadowAspect(pokemon)) {
            serverPlayer.sendSystemMessage(Component.literal("You cannot use this item on a shadow pokemon!").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(itemStack);
        }
        return original.call(instance, serverPlayer, itemStack, pokemon);
    }
}
