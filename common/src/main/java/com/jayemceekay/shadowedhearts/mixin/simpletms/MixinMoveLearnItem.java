package com.jayemceekay.shadowedhearts.mixin.simpletms;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleSettings;
import dragomordor.simpletms.item.custom.MoveLearnItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
        if (ShadowMoveUtil.isShadowMove(move) && (rules == null || !rules.permitsShadowTM(move.getName()))) {
            cir.setReturnValue(false);
            return;
        }
        if (MoveLifecycle.blocksLearning(pokemon, Moves.INSTANCE.getByName(moveName))) cir.setReturnValue(false);
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
        if (!MoveLifecycle.blocksLearning(pokemon, Moves.INSTANCE.getByName(moveName))) return;
        player.displayClientMessage(Component.translatable("message.shadowedhearts.move_lifecycle.cannot_relearn")
                .withStyle(ChatFormatting.RED), true);
        cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
}
