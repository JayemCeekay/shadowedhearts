package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.client.gui.summary.featurerenderers.BarSummarySpeciesFeatureRenderer;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.stats.StatWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.client.gui.summary.widgets.screens.stats.features.HeartGaugeFeatureRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    @Shadow @Final private Pokemon pokemon;
    @Shadow @Final private List<String> statOptions;

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

    /**
     * Mask EV polygon ratios by zeroing them when nature is hidden by the gauge (> 40%).
     * Targets the call building EV ratios list in StatWidget.render (ordinal aligned to EV case).
     */
    // Note: We avoid polygon tampering to reduce fragility; numeric EV labels will be masked below.

    /**
     * Mask EV numeric labels "value/MAX" rendering when gauge hides nature.
     * We intercept the text argument for EV label rendering by ordinal.
     */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/stats/StatWidget;statValuesAsText(Ljava/util/List;Z)Ljava/util/List;"
            ),
            remap = false
    )
    private List<MutableComponent> shadowedhearts$maskEvTexts(StatWidget instance, List<Stat> stats, boolean asPercent, com.llamalad7.mixinextras.injector.wrapoperation.Operation<List<MutableComponent>> original) {
        List<MutableComponent> list = original.call(instance, stats, asPercent);
        try {
            String tab = statOptions.get(instance.getStatTabIndex());
            // Mask EVs when nature is hidden (> 2 bars), mask IVs until 2 bars or fewer remain
            if (("evs".equals(tab) && PokemonAspectUtil.isEVHiddenByGauge(pokemon))
                    || ("ivs".equals(tab) && PokemonAspectUtil.isIVHiddenByGauge(pokemon))) {
                List<MutableComponent> masked = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) masked.add(Component.literal("??"));
                return masked;
            }
        } catch (Throwable ignored) { }
        return list;
    }
}
