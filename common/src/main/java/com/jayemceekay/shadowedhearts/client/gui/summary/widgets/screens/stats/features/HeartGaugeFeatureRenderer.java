package com.jayemceekay.shadowedhearts.client.gui.summary.widgets.screens.stats.features;

import com.cobblemon.mod.common.client.gui.summary.featurerenderers.BarSummarySpeciesFeatureRenderer;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Heart Gauge renderer for the Cobblemon summary stats screen.
 */
public class HeartGaugeFeatureRenderer extends BarSummarySpeciesFeatureRenderer {

    // Placeholder textures – replace with actual paths when assets are ready
    private static final ResourceLocation BAR_TEXTURE = new ResourceLocation(
            "cobblemon", "textures/gui/summary/summary_stats_other_bar.png"
    );
    private static final ResourceLocation OVERLAY_TEXTURE = new ResourceLocation(
            "shadowedhearts", "textures/gui/summary/heart_gauge_overlay.png"
    );

    private final Pokemon selectedPokemon;

    public HeartGaugeFeatureRenderer(Pokemon selectedPokemon) {
        super(
                "heart_gauge",
                Component.translatable("shadowedhearts.ui.stats.heart_gauge"),
                BAR_TEXTURE,
                OVERLAY_TEXTURE,
                selectedPokemon,
                0,
                100,
                Math.max(0, PokemonAspectUtil.getHeartGaugeValue(selectedPokemon)),
                new Vec3(129.0, 0.0, 255.0)
        );
        this.selectedPokemon = selectedPokemon;
    }

    @Override
    public boolean render(GuiGraphics guiGraphics, float x, float y, Pokemon pokemon) {
        // Recompute the current heart gauge value every frame so the bar updates live
        // when server-side events (e.g., /shadow partysteps) modify the meter.
        int current = Math.max(0, PokemonAspectUtil.getHeartGaugeValue(pokemon));
        super.renderElement(guiGraphics, x, y, pokemon, current);
        return true;
    }

    @Override
    public void renderBar(@NotNull GuiGraphics guiGraphics, float x, float y, int barValue, float barRatio, int barWidth) {
        super.renderBar(guiGraphics, x, y, barValue, barRatio, barWidth);
    }
}
