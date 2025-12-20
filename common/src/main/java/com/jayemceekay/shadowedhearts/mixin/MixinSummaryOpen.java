package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Ensure required Shadow aspects are present when the Summary UI opens via keybind/local calls.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = Summary.class, remap = false)
public abstract class MixinSummaryOpen {

    @Inject(method = "open", at = @At("HEAD"))
    private static void shadowedhearts$ensureOnSummaryOpen(Collection<Pokemon> party, boolean editable, int selection, CallbackInfo ci) {
        try {
            for (Pokemon p : party) {
                if (p != null) PokemonAspectUtil.ensureRequiredShadowAspects(p);
            }
        } catch (Throwable ignored) {
        }
    }
}
