package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.components.TMMoveComponent;
import com.cobblemon.mod.common.item.interactive.TechnicalMachineItem;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowMoveTeachingPolicy;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.tm.ShadowTMTeaching;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TechnicalMachineItem.class, remap = false)
public abstract class MixinTechnicalMachineItem {
    @Inject(method = "processInteraction", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$guardTMItem(ServerPlayer player, PokemonEntity entity, ItemStack stack,
                                          CallbackInfoReturnable<Boolean> cir) {
        var move = TMMoveComponent.Companion.getTMMove(stack);
        if (ShadowMoveUtil.isShadowMove(move)) {
            cir.setReturnValue(ShadowTMTeaching.beginItem(player, entity, stack, move));
        } else if (ShadowMoveTeachingPolicy.rejectNativeTM(player, entity.getPokemon(), move)) {
            cir.setReturnValue(false);
        }
    }
}
