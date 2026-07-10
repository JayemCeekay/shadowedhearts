package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Offscreen framebuffer wrapper for the penumbra trail metaball density pipeline.
 */
public final class PenumbraDensityFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(1, 0.45f);

    private PenumbraDensityFBO() {}

    public static boolean beginDensityPass() {
        return PIPELINE.beginDensityPass();
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    public static int blur() {
        return PIPELINE.blur(ModShaders.PENUMBRA_BLUR);
    }

    public static boolean composite() {
        return PIPELINE.composite(ModShaders.PENUMBRA_COMPOSITE, PenumbraDensityFBO::setupCompositeUniforms);
    }

    private static void setupCompositeUniforms(ShaderInstance shader) {
        Minecraft mc = Minecraft.getInstance();
        float gameTime = 0f;
        if (mc.level != null) {
            gameTime = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        }

        Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) {
            uGameTime.set(gameTime / 1200.0f);
        }
    }

    public static void destroy() {
        PIPELINE.destroy();
    }
}
