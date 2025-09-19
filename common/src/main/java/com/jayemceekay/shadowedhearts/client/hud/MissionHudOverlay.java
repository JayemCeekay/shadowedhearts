package com.jayemceekay.shadowedhearts.client.hud;

import com.jayemceekay.shadowedhearts.world.WorldspaceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Minimal client HUD overlay shown while the player is in the Missions dimension.
 * NeoForge 1.21.1: invoked from RenderGuiEvent.Post via ShadowedHeartsClient.
 */
public final class MissionHudOverlay {
    private MissionHudOverlay() {}

    private static final int BG_COLOR = 0xAA101018; // semi-transparent dark
    private static final int ACCENT_COLOR = 0xFF6A5ACD; // slate purple accent
    private static final int TEXT_COLOR = 0xFFE0E0E0; // near-white

    public static void render(GuiGraphics gfx, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        ResourceKey<Level> dim = mc.level.dimension();
        if (!dim.equals(WorldspaceManager.MISSIONS_LEVEL_KEY)) return; // Only in missions

        int sw = gfx.guiWidth();
        int sh = gfx.guiHeight();
        int pad = 6;
        int barW = Math.min(240, sw - 2 * pad);
        int barH = 26;
        int x = (sw - barW) / 2;
        int y = pad; // top-center

        // Background panel
        gfx.fill(x, y, x + barW, y + barH, BG_COLOR);
        // Accent bottom line
        gfx.fill(x, y + barH - 2, x + barW, y + barH, ACCENT_COLOR);

        Font font = mc.font;
        Component title = Component.translatable("hud.shadowedhearts.mission_active");
        String dimName = dim.location().toString();
        Component subtitle = Component.literal(dimName);

        int titleW = font.width(title);
        int subW = font.width(subtitle);
        int titleX = x + (barW - titleW) / 2;
        int subX = x + (barW - subW) / 2;

        gfx.drawString(font, title, titleX, y + 7, TEXT_COLOR, true);
        gfx.drawString(font, subtitle, subX, y + 16, 0xFF9AA0A6, false);
    }
}
