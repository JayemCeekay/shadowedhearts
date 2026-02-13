package com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras;

import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(value = Summary.class, remap = false)
public abstract class MixinCobblemonPartyExtrasSummaryUIMixin {

    @Shadow
    private Pokemon selectedPokemon;

    @WrapMethod(method = "renderPortraitStats")
    private void shadowedhearts$blockRenderStats(GuiGraphics graphics, int baseX, int baseY, Operation<Void> original) {
        if (ShadowAspectUtil.hasShadowAspect(selectedPokemon)) {
            return;
        }
        original.call(graphics, baseX, baseY);
    }


}
