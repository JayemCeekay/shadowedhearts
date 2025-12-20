package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Simple HUD preview for the Poké Ball glow/ring quads used in MixinPokeBallRenderer.
 * Renders small thumbnails for each state so tuning can be done without spawning entities.
 */
public final class BallGlowHudDebug {

    private BallGlowHudDebug() {}

    private static final int FULLBRIGHT = 0x00F000F0; // left for potential ring rendering

    public static boolean ENABLED = true; // toggle if needed

    public static void render(GuiGraphics g, DeltaTracker tickDelta) {
        if (!ENABLED) return;

        PoseStack ps = g.pose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

        int x0 = 75;
        int y0 = 12;
        int stepX = 72;
        int stepY = 88;
        int size = 106; // pixels

        // Row 1: Orb states
        renderOrb(ps, buffers, x0 + size / 2, y0 + 18 + size / 2, size, 1.0f);

        // Ensure all pending HUD draws are flushed; endBatch is safer than endLastBatch for custom RenderTypes
        //buffers.endBatch();
    }

    private static void drawLabel(GuiGraphics g, int x, int y, String text) {
        g.drawString(Minecraft.getInstance().font, text, x, y, 0xE0E0E0, false);
    }

    private static void renderOrb(PoseStack ps, MultiBufferSource buffers, int cx, int cy, int sizePx, float alpha) {
        ps.pushPose();
        // position to screen center for this thumbnail
        ps.translate(cx, cy, 0);
        // Delegate to BallEmitters to ensure identical rendering path as in-world orb
        BallEmitters.renderHudOrb(ps, buffers, sizePx, alpha);
        ps.popPose();
    }
}
