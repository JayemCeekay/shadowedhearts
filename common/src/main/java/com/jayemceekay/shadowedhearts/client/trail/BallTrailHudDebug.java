package com.jayemceekay.shadowedhearts.client.trail;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.BallRenderTypes;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;

/**
 * Simple HUD overlay that draws a small preview quad using the ball trail render types.
 * This is purely for debugging visuals/palette of the trail shader.
 */
public final class BallTrailHudDebug {
    private static boolean ENABLED = true; // quick toggle if needed

    private BallTrailHudDebug() {}

    public static void render(@NotNull GuiGraphics gg, @NotNull DeltaTracker tickDelta) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int margin = 64;
        int w = Math.max(64, Math.min(256, screenW / 4));
        int h = Math.max(16, w / 4); // keep a wide strip aspect similar to the trail texture

        int x = margin;
        int y = screenH - h - margin;

        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x, y, 0);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // Outer glow trail preview
        uploadPaletteUniforms(tickDelta.getGameTimeDeltaPartialTick(true));
        VertexConsumer vc = buffers.getBuffer(BallRenderTypes.trailAdditive());
        emitHudQuad(vc, 0, 0, w, h, 180, 60, 255, 220, 0f, 1f, pose);
        // Flush to ensure the shader is bound and this quad draws with the just-updated uniforms
        buffers.endLastBatch();

        // Core pass preview, thinner line in the middle area to mimic the core streak
        uploadPaletteUniforms(tickDelta.getGameTimeDeltaPartialTick(true));
        VertexConsumer vcCore = buffers.getBuffer(BallRenderTypes.trailCoreAdditive());
        int coreH = Math.max(2, (int) (h * 0.35f));
        int coreY = (h - coreH) / 2;
        emitHudQuad(vcCore, 0, coreY, w, coreH, 255, 255, 255, 220, 0f, 1f, pose);
        // Flush core pass as well so it uses its own updated uniforms
        buffers.endLastBatch();

        pose.popPose();
        // Let GuiGraphics flush later in the frame; no explicit endBatch here to avoid interfering with other HUD draws.
    }

    // Optional helper if a float partials variant is ever needed elsewhere
    public static void render(@NotNull GuiGraphics gg, float partialTick) {
        render(gg, Minecraft.getInstance().gui.getGuiTicks());
    }

    /**
     * Push palette uniforms each HUD draw so hot-reloaded shaders pick up tweaks like the world trail does.
     * Safe if another shader is bound; guarded by uniform presence.
     */
    private static void uploadPaletteUniforms(float partialTicks) {
        try {
            // Target the actual ball trail shader instance rather than whatever is currently bound.
            // ModShaders.BALL_TRAIL is the ShaderInstance created from ball_trail.json
            ShaderInstance sh = ModShaders.BALL_TRAIL != null ? ModShaders.BALL_TRAIL : RenderSystem.getShader();
            if (sh == null) return;

            Minecraft mc = Minecraft.getInstance();
            float partial = partialTicks;
            float t = 0f;
            if (mc != null) {
                if (mc.level != null) {
                    t = (mc.level.getGameTime() + partial) * 0.05f;
                } else if (mc.gui != null) {
                    t = (mc.gui.getGuiTicks() + partial) * 0.05f;
                }
            }

            // Keep defaults in sync with BallTrailManager: no motion, fixed phase
            set1f(sh, "u_paletteMix", 1.0f);
            float speed = 0.0f;
            set1f(sh, "u_paletteSpeed", speed);
            set1f(sh, "u_paletteShift", 0.5f);
            set1f(sh, "u_paletteSaturation", 1.0f);
            // Luminance coefficients for saturation control (keep in sync with shader JSON defaults)
            set3f(sh, "u_lumaCoeff", 0.299f, 0.587f, 0.114f);
            // Preview with a slight brightness boost to match in-world appearance
            set1f(sh, "uStrength", 2.5f);

            // Upload dynamic palette stops (same defaults as JSON)
            set3f(sh, "u_c0", 1.00f, 1.00f, 1.00f);
            set3f(sh, "u_c1", 0.60f, 0.92f, 0.12f);
            set3f(sh, "u_c2", 0.60f, 0.68f, 0.10f);
            set3f(sh, "u_c3", 0.60f, 0.25f, 0.95f);
            set1f(sh, "u_t1", 0.30f);
            set1f(sh, "u_t2", 0.60f);
            set1f(sh, "u_t3", 1.00f);
        } catch (Throwable ignored) {
        }
    }

    // Small uniform helper (same style as in BallTrailManager/AuraEmitters)
    private static void set1f(ShaderInstance sh, String name, float v) {
        if (sh == null) return;
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set3f(ShaderInstance sh, String name, float x, float y, float z) {
        if (sh == null) return;
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private static void emitHudQuad(@NotNull VertexConsumer vc,
                                    int x, int y, int w, int h,
                                    int r, int g, int b, int a,
                                    float u1, float u2,
                                    PoseStack poseStack) {
        var last = poseStack.last();
        var mat = last.pose();
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;
        // v across thickness (0..1), u along the trail length (u1..u2)
        vc.addVertex(mat, x0, y0, 0)
                .setColor(r, g, b, a)
                .setUv(u1, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0, 0, 1);
        vc.addVertex(mat, x0, y1, 0)
                .setColor(r, g, b, a)
                .setUv(u1, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0, 0, 1);
        vc.addVertex(mat, x1, y1, 0)
                .setColor(r, g, b, a)
                .setUv(u2, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0, 0, 1);
        vc.addVertex(mat, x1, y0, 0)
                .setColor(r, g, b, a)
                .setUv(u2, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0, 0, 1);
    }
}
