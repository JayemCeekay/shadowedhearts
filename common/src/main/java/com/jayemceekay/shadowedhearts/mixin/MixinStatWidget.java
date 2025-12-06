package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.featurerenderers.BarSummarySpeciesFeatureRenderer;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.stats.StatWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.client.gui.summary.widgets.screens.stats.features.HeartGaugeFeatureRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects the Heart Gauge feature into the Cobblemon Summary Stats "Other" page for Shadow Pokémon.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * 02 §5 Mission Entrance flow (UI alignment) — display custom meters via mixin on client.
 */
@Mixin(StatWidget.class)
public class MixinStatWidget {

    // Shadow the target widget's fields we need to read/modify
    @Shadow @Final @Mutable private List<BarSummarySpeciesFeatureRenderer> universalFeatures;

    /**
     * After StatWidget constructor finishes, append our Heart Gauge feature for Shadow Pokémon.
     */
    @Inject(method = "<init>*", at = @At("TAIL"))
    private void shadowedhearts$appendHeartGauge(int pX, int pY, Pokemon pokemon, int tabIndex, CallbackInfo ci) {
        // Only show on Shadow Pokémon
        if (!PokemonAspectUtil.hasShadowAspect(pokemon)) {
            return;
        }

        // Ensure list is mutable for appending
        List<BarSummarySpeciesFeatureRenderer> mutated = new ArrayList<>(this.universalFeatures);

        // Prevent duplicates if other mods/mixins also add it
        boolean exists = mutated.stream().anyMatch(r -> "heart_gauge".equals(r.getName()));
        if (!exists) {
            mutated.add(new HeartGaugeFeatureRenderer(pokemon));
        }

        this.universalFeatures = mutated;
    }
}
