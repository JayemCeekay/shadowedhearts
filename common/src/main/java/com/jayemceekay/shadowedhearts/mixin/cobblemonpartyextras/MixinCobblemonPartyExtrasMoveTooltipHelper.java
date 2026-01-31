package com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras;


import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "party.extras.cobblemon.client.tooltip.MoveTooltipHelper")
public class MixinCobblemonPartyExtrasMoveTooltipHelper {

    @WrapMethod(method = "renderDeferredTooltip")
    private static void shadowedhearts$blockRenderTooltip(GuiGraphics context, Operation<Void> original) {
        if(shadowedhearts$shouldMaskTooltip()) {
            return;
        }
        original.call(context);
    }

    private static boolean shadowedhearts$shouldMaskTooltip() {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        if (currentScreen instanceof Summary summary) {
            Pokemon pokemon = summary.getSelectedPokemon$common();
            return ShadowGate.isShadowLockedClient(pokemon);
        }
        return true;
    }

}
