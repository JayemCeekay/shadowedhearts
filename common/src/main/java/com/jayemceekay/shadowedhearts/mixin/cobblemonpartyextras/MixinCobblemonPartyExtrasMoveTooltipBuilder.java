package com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras;


import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "party.extras.cobblemon.client.tooltip.MoveTooltipBuilder", remap = false)
public class MixinCobblemonPartyExtrasMoveTooltipBuilder {


    @Unique
    private static boolean shadowedhearts$shouldMask(MoveTemplate m, Pokemon pokemon) {
        if (m == null || pokemon == null) return false;
        return ShadowAspectUtil.shouldMaskMove(pokemon, m.getName());
    }


    @Inject(method = "buildTooltip", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockMoveTooltip(MoveTemplate moveTemplate, int currentPP, int maxPP, Pokemon pokemon, CallbackInfoReturnable<List<Component>> cir) {
        if (shadowedhearts$shouldMask(moveTemplate, pokemon)) {
            cir.cancel();
        }
    }
}
