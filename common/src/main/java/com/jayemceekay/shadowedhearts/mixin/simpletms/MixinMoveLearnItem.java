package com.jayemceekay.shadowedhearts.mixin.simpletms;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dragomordor.simpletms.config.SimpleTMsConfig;
import dragomordor.simpletms.item.custom.MoveLearnItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SimpleTMs writes active/benched moves directly, bypassing Pokemon.exchangeMove. */
@Mixin(value = MoveLearnItem.class, remap = false)
public abstract class MixinMoveLearnItem {
    @Shadow @Final private String moveName;

    @Inject(method = "canUseOnPokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$hideBlockedRelearning(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        var move = Moves.INSTANCE.getByName(moveName);
        var rules = ItemRuleSettings.forPokemon(pokemon);
        if (ShadowMoveUtil.isShadowMove(move) && (rules == null || !rules.permitsShadowTM(move.getName())
                || shadowedhearts$incompatible(pokemon, move))) {
            cir.setReturnValue(false);
            return;
        }
        if (MoveLifecycle.blocksLearning(pokemon, move)) cir.setReturnValue(false);
    }

    @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$rejectBlockedRelearning(ServerPlayer player, ItemStack stack, Pokemon pokemon,
                                                       CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        var move = Moves.INSTANCE.getByName(moveName);
        if (ShadowMoveUtil.isShadowMove(move) && !ItemRuleSettings.serverCurrent().permitsShadowTM(move.getName())) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.shadow_tm.disabled")
                    .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }
        if (ShadowMoveUtil.isShadowMove(move) && shadowedhearts$incompatible(pokemon, move)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.shadow_tm.incompatible")
                    .withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }
        if (!MoveLifecycle.blocksLearning(pokemon, move)) return;
        player.displayClientMessage(Component.translatable("message.shadowedhearts.move_lifecycle.cannot_relearn")
                .withStyle(ChatFormatting.RED), true);
        cir.setReturnValue(InteractionResultHolder.fail(stack));
    }

    @Unique
    private static boolean shadowedhearts$incompatible(Pokemon pokemon, MoveTemplate move) {
        var profile = ShadowLearnsets.get(pokemon);
        return profile != null && !profile.permits(move.getName(), pokemon.getLevel(), ShadowAspectUtil.hasShadowAspect(pokemon));
    }

    /** Extend the current form's teachable sources after SimpleTMs' own item and already-known checks. */
    @WrapOperation(method = "canPokemonLearnMove(Lcom/cobblemon/mod/common/pokemon/Pokemon;Lcom/cobblemon/mod/common/api/moves/MoveTemplate;)Z",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Ldragomordor/simpletms/item/custom/MoveLearnItem;canPokemonLearnMove$canLearnFromLearnset(Lcom/cobblemon/mod/common/api/moves/MoveTemplate;Ldragomordor/simpletms/config/SimpleTMsConfig;Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;)Z"))
    private boolean shadowedhearts$considerAuthoredSource(MoveTemplate move, SimpleTMsConfig config, Learnset learnset,
                                                          Operation<Boolean> original, @Local(argsOnly = true) Pokemon pokemon) {
        var profile = ShadowLearnsets.get(pokemon);
        if (profile == null || !ShadowMoveUtil.isShadowMove(move)) return original.call(move, config, learnset);
        boolean shadow = ShadowAspectUtil.hasShadowAspect(pokemon);
        if (config.getTmMovesLearnable() && profile.permits(move.getName(), pokemon.getLevel(), shadow)) return true;
        return config.getLevelMovesLearnable() && profile.levelMoves(pokemon.getLevel(), shadow).contains(move.getName());
    }
}
