package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHud;
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderHudGeometry;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

/** One Reader infobox for the objective, with room reserved for existing game HUDs. */
public final class MoonwoundObjectiveHud {
    private MoonwoundObjectiveHud() { }

    public static void render(GuiGraphics graphics, MoonwoundSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        var config = ShadowedHeartsConfigs.getInstance().getClientConfig();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        var occupied = new ArrayList<>(AuraReaderHud.occupiedAreas(width, height, config));
        MoonwoundVanillaHudReservations.add(minecraft, width, height, occupied);

        int designWidth = Math.max(176, AuraReaderHud.moonwoundInfoBoxWidth(snapshot));
        float scale = Math.max(AuraReaderHudGeometry.MIN_READOUT_SCALE, Math.min(1.5f, config.auraReaderHudScale()));
        // Keep full text when possible; only shrink to the Reader's normal minimum readable size.
        for (float candidateScale : new float[]{scale, AuraReaderHudGeometry.MIN_READOUT_SCALE}) {
            int cardHeight = (int) Math.ceil(AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT * candidateScale);
            float actualScale = cardHeight / (float) AuraReaderHudGeometry.SIGNAL_POPOVER_HEIGHT;
            int cardWidth = (int) Math.ceil(designWidth * actualScale);
            var bounds = MoonwoundHudLayout.resolve(width, height, cardWidth, cardHeight,
                    config.moonwoundObjectivePlacement(), config.moonwoundObjectiveOffsetX(),
                    config.moonwoundObjectiveOffsetY(), occupied);
            if (bounds.isPresent()) {
                AuraReaderHud.renderMoonwoundInfoBox(graphics, bounds.get(), snapshot, config.auraReaderHudOpacity());
                return;
            }
        }
    }

}
