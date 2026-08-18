package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Offscreen framebuffer wrapper for the shadow Pokemon model-following aura.
 */
public final class ShadowPokemonAuraFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(0, 0.82f);

    private ShadowPokemonAuraFBO() {}

    public static boolean beginDensityPass() {
        return PIPELINE.beginDensityPass();
    }

    public static boolean beginDensityPass(boolean clearTarget, boolean copyMainDepth) {
        return PIPELINE.beginDensityPass(clearTarget, copyMainDepth);
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    public static int blur() {
        return PIPELINE.blur(ModShaders.PENUMBRA_BLUR);
    }

    public static boolean composite() {
        return PIPELINE.composite(ModShaders.SHADOW_POKEMON_AURA_COMPOSITE, ShadowPokemonAuraFBO::setupCompositeUniforms);
    }

    /**
     * Composites only inside a normalized framebuffer rectangle. GUI callers
     * use this instead of relying on ModelWidget's full-resolution scissor
     * while the aura material itself is rendered at reduced resolution.
     */
    public static boolean composite(float minimumU,
                                    float minimumV,
                                    float maximumU,
                                    float maximumV,
                                    float animationTicks) {
        return PIPELINE.composite(
                ModShaders.SHADOW_POKEMON_AURA_COMPOSITE,
                shader -> setupCompositeUniforms(shader, animationTicks),
                false,
                minimumU,
                minimumV,
                maximumU,
                maximumV
        );
    }

    private static void setupCompositeUniforms(ShaderInstance shader) {
        Minecraft mc = Minecraft.getInstance();
        float gameTime = 0f;
        if (mc.level != null) {
            gameTime = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        }

        setupCompositeUniforms(shader, gameTime);
    }

    private static void setupCompositeUniforms(ShaderInstance shader, float gameTime) {

        Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) {
            uGameTime.set(gameTime / 1200.0f);
        }
    }

    public static void destroy() {
        PIPELINE.destroy();
    }
}
