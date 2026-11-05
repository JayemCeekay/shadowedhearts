package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPixelationScale;
import com.jayemceekay.shadowedhearts.config.AuraPixelationStyle;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * Offscreen framebuffer wrapper for the shadow Pokemon model-following aura.
 */
public final class ShadowPokemonAuraFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(2, 0.82f);
    private static final Map<ShadowAuraRenderBatch, DensityFboPipeline> STYLE_PIPELINES =
            new HashMap<>();

    static {
        STYLE_PIPELINES.put(new ShadowAuraRenderBatch(ShadowAuraStyle.SIGNATURE, false), PIPELINE);
    }
    private static final String PIXEL_PRESENTATION_OVERRIDE =
            System.getProperty("shadowedhearts.shadowAuraPixelPresentation");
    private static final int WORLD_PIXEL_SIZE = readPixelSize(
            "shadowedhearts.shadowAuraPixelSize", 8, 8);
    private static final int GUI_LOGICAL_PIXEL_SIZE = readPixelSize(
            "shadowedhearts.shadowAuraGuiLogicalPixelSize", 2, 2);

    private ShadowPokemonAuraFBO() {}

    public static boolean beginDensityPass() {
        return beginDensityPass(ShadowAuraStyle.DEFAULT);
    }

    public static boolean beginDensityPass(ShadowAuraStyle style) {
        return pipeline(style).beginDensityPass();
    }

    public static boolean beginDensityPass(boolean clearTarget, boolean copyMainDepth) {
        return beginDensityPass(ShadowAuraStyle.DEFAULT, clearTarget, copyMainDepth);
    }

    public static boolean beginDensityPass(ShadowAuraStyle style,
                                           boolean clearTarget,
                                           boolean copyMainDepth) {
        return beginDensityPass(new ShadowAuraRenderBatch(style, false), clearTarget, copyMainDepth);
    }

    static boolean beginDensityPass(ShadowAuraRenderBatch batch,
                                    boolean clearTarget,
                                    boolean copyMainDepth) {
        return pipeline(batch).beginDensityPass(clearTarget, copyMainDepth);
    }

    public static void endDensityPass() {
        endDensityPass(ShadowAuraStyle.DEFAULT);
    }

    public static void endDensityPass(ShadowAuraStyle style) {
        pipeline(style).endDensityPass();
    }

    static void endDensityPass(ShadowAuraRenderBatch batch) {
        pipeline(batch).endDensityPass();
    }

    /** Pristine scene depth copied by this frame's density pass, before final world post-processing. */
    public static int getDensityDepthTextureId(ShadowAuraStyle style) {
        return pipeline(style).getDensityDepthTextureId();
    }

    public static int getDensityWidth(ShadowAuraStyle style) {
        return pipeline(style).getTargetWidth();
    }

    public static int getDensityHeight(ShadowAuraStyle style) {
        return pipeline(style).getTargetHeight();
    }

    public static int blur() {
        return blur(ShadowAuraStyle.DEFAULT);
    }

    public static int blur(ShadowAuraStyle style) {
        return blur(new ShadowAuraRenderBatch(style, false));
    }

    static int blur(ShadowAuraRenderBatch batch) {
        ShadowAuraStyle safeStyle = batch.style();
        // XD stores its narrow ribbon/point core in G. Keep that channel sharp
        // while filtering its aligned haze and halo; a missing dedicated shader
        // leaves XD unblurred rather than silently erasing its fine detail.
        return pipeline(batch).blur(safeStyle == ShadowAuraStyle.XD_FAITHFUL
                ? ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_BLUR
                : ModShaders.PENUMBRA_BLUR);
    }

    public static boolean composite() {
        return composite((float) (Math.log(maximumWorldPixelSize())
                / Math.log(2.0)));
    }

    public static boolean composite(float worldPixelLod) {
        return composite(ShadowAuraStyle.DEFAULT, worldPixelLod);
    }

    public static boolean composite(ShadowAuraStyle style, float worldPixelLod) {
        return composite(new ShadowAuraRenderBatch(style, false), worldPixelLod);
    }

    static boolean composite(ShadowAuraRenderBatch batch, float worldPixelLod) {
        return composite(batch, worldPixelLod, null);
    }

    static boolean composite(ShadowAuraRenderBatch batch, float worldPixelLod,
                             MoonwoundPixelationScale.Grid grid) {
        ShadowAuraStyle safeStyle = batch.style();
        DensityFboPipeline pipeline = pipeline(batch);
        if (safeStyle != ShadowAuraStyle.SIGNATURE) {
            if (pixelationStyle(safeStyle) != AuraPixelationStyle.SMOOTH && grid != null
                    && pipeline.compositeContinuousPixels(worldCompositeShader(safeStyle),
                    shader -> setupCompositeUniforms(shader, batch.modeTint()), grid, false,
                    0, 0, 1, 1)) return true;
            return pipeline.composite(worldCompositeShader(safeStyle),
                    shader -> setupCompositeUniforms(shader, batch.modeTint()));
        }
        float maximumLod = (float) (Math.log(maximumWorldPixelSize())
                / Math.log(2.0));
        float effectivePixelLod = Float.isFinite(worldPixelLod)
                ? Math.max(0.0f, Math.min(maximumLod, worldPixelLod))
                : maximumLod;
        float pixelScale = ShadowAuraStyleProfiles.forStyle(safeStyle)
                .render()
                .pixelSizeScale();
        if (pixelScale > 0.0f && Float.isFinite(pixelScale)) {
            effectivePixelLod = Math.max(
                    0.0f,
                    Math.min(
                            maximumLod,
                            effectivePixelLod
                                    + (float) (Math.log(pixelScale) / Math.log(2.0))));
        }
        if (ShadowAuraPresentationPolicy.pixelated(safeStyle, PIXEL_PRESENTATION_OVERRIDE)
                && effectivePixelLod > 0.001f
                && pipeline.compositeFractionalMip(
                worldPixelCompositeShader(safeStyle),
                shader -> setupCompositeUniforms(shader, batch.modeTint()),
                ModShaders.SHADOW_POKEMON_AURA_PRESENT,
                effectivePixelLod,
                0.0f, 0.0f, 1.0f, 1.0f)) {
            return true;
        }
        return pipeline.composite(
                worldCompositeShader(safeStyle),
                shader -> setupCompositeUniforms(shader, batch.modeTint()));
    }

    /**
     * Composites only inside a normalized framebuffer rectangle. GUI callers
     * use this instead of relying on ModelWidget's full-resolution scissor
     * while the aura material itself is rendered at reduced resolution. This
     * overload deliberately uses the GUI-owned shader instance so its uniforms
     * cannot leak into the world composite.
     */
    public static boolean composite(float minimumU,
                                    float minimumV,
                                    float maximumU,
                                    float maximumV,
                                    float animationTicks) {
        return composite(
                ShadowAuraStyle.DEFAULT,
                minimumU,
                minimumV,
                maximumU,
                maximumV,
                animationTicks);
    }

    public static boolean composite(ShadowAuraStyle style,
                                    float minimumU,
                                    float minimumV,
                                    float maximumU,
                                    float maximumV,
                                    float animationTicks) {
        return composite(new ShadowAuraRenderBatch(style, false), minimumU, minimumV,
                maximumU, maximumV, animationTicks);
    }

    static boolean composite(ShadowAuraRenderBatch batch,
                             float minimumU,
                             float minimumV,
                             float maximumU,
                             float maximumV,
                             float animationTicks) {
        return composite(batch, minimumU, minimumV, maximumU, maximumV, animationTicks, null);
    }

    static boolean composite(ShadowAuraRenderBatch batch,
                             float minimumU, float minimumV, float maximumU, float maximumV,
                             float animationTicks, MoonwoundPixelationScale.Grid grid) {
        ShadowAuraStyle safeStyle = batch.style();
        DensityFboPipeline pipeline = pipeline(batch);
        if (safeStyle != ShadowAuraStyle.SIGNATURE) {
            if (pixelationStyle(safeStyle) != AuraPixelationStyle.SMOOTH && grid != null
                    && pipeline.compositeContinuousPixels(guiCompositeShader(safeStyle),
                    shader -> setupCompositeUniforms(shader, animationTicks, batch.modeTint()), grid, true,
                    minimumU, minimumV, maximumU, maximumV)) return true;
            return pipeline.composite(guiCompositeShader(safeStyle),
                    shader -> setupCompositeUniforms(shader, animationTicks, batch.modeTint()), false,
                    minimumU, minimumV, maximumU, maximumV);
        }
        int guiPixelSize = Math.max(
                1,
                Math.round(guiPixelSize()
                        * ShadowAuraStyleProfiles.forStyle(safeStyle)
                        .render()
                        .pixelSizeScale()));
        if (ShadowAuraPresentationPolicy.pixelated(safeStyle, PIXEL_PRESENTATION_OVERRIDE)
                && guiPixelSize > 1
                && pipeline.compositePixelated(
                guiPixelCompositeShader(safeStyle),
                shader -> setupCompositeUniforms(shader, animationTicks, batch.modeTint()),
                ModShaders.SHADOW_POKEMON_AURA_GUI_PRESENT,
                guiPixelSize,
                true,
                minimumU, minimumV, maximumU, maximumV)) {
            return true;
        }
        return pipeline.composite(
                guiCompositeShader(safeStyle),
                shader -> setupCompositeUniforms(shader, animationTicks, batch.modeTint()),
                false,
                minimumU,
                minimumV,
                maximumU,
                maximumV
        );
    }

    static int guiPixelSize() {
        Minecraft mc = Minecraft.getInstance();
        int guiScale = Math.max(
                1,
                (int) Math.round(mc.getWindow().getGuiScale()));
        return Math.min(6, guiScale * GUI_LOGICAL_PIXEL_SIZE);
    }

    static AuraPixelationStyle pixelationStyle(ShadowAuraStyle style) {
        var config = ShadowedHeartsConfigs.getInstance().getClientConfig();
        AuraPixelationStyle selected = switch (safeStyle(style)) {
            case COLOSSEUM -> config.colosseumAuraPixelation();
            case XD_FAITHFUL -> config.xdAuraPixelation();
            case SIGNATURE -> AuraPixelationStyle.SMOOTH;
        };
        return safeStyle(style) == ShadowAuraStyle.SIGNATURE ? AuraPixelationStyle.SMOOTH
                : ShadowAuraPresentationPolicy.classicPixelation(selected, PIXEL_PRESENTATION_OVERRIDE);
    }

    static boolean compositeColosseumMotes(MoonwoundPixelationScale.Grid grid, Runnable draw) {
        return grid != null && pixelationStyle(ShadowAuraStyle.COLOSSEUM) != AuraPixelationStyle.SMOOTH
                && pipeline(ShadowAuraStyle.COLOSSEUM).compositeContinuousPixels(
                null, null, grid, false, 0, 0, 1, 1, draw, false);
    }

    static int maximumWorldPixelSize() {
        return Integer.highestOneBit(Math.max(1, WORLD_PIXEL_SIZE));
    }

    public static ShaderInstance densityShader(ShadowAuraStyle style, boolean gui) {
        return gui ? guiDensityShader(style) : worldDensityShader(style);
    }

    public static ShaderInstance worldDensityShader(ShadowAuraStyle style) {
        return switch (safeStyle(style)) {
            case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_DENSITY;
            case COLOSSEUM -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_DENSITY;
            case XD_FAITHFUL -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_DENSITY;
        };
    }

    public static ShaderInstance guiDensityShader(ShadowAuraStyle style) {
        return switch (safeStyle(style)) {
            case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_GUI_DENSITY;
            case COLOSSEUM -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_DENSITY;
            case XD_FAITHFUL -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_DENSITY;
        };
    }

    public static ShaderInstance filamentDensityShader(boolean gui) {
        return gui
                ? ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_FILAMENT_DENSITY
                : ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_FILAMENT_DENSITY;
    }

    private static ShaderInstance worldCompositeShader(ShadowAuraStyle style) {
        return switch (safeStyle(style)) {
            case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_COMPOSITE;
            case COLOSSEUM -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_COMPOSITE;
            case XD_FAITHFUL -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_COMPOSITE;
        };
    }

    private static ShaderInstance guiCompositeShader(ShadowAuraStyle style) {
        return switch (safeStyle(style)) {
            case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_GUI_COMPOSITE;
            case COLOSSEUM -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_COMPOSITE;
            case XD_FAITHFUL -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_COMPOSITE;
        };
    }

    private static ShaderInstance worldPixelCompositeShader(ShadowAuraStyle style) {
        return switch (safeStyle(style)) {
            case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_PIXEL_COMPOSITE;
            case COLOSSEUM -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_PIXEL_COMPOSITE;
            case XD_FAITHFUL -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_PIXEL_COMPOSITE;
        };
    }

    private static ShaderInstance guiPixelCompositeShader(ShadowAuraStyle style) {
        return switch (safeStyle(style)) {
            case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE;
            case COLOSSEUM -> ModShaders.SHADOW_POKEMON_AURA_COLOSSEUM_GUI_PIXEL_COMPOSITE;
            case XD_FAITHFUL -> ModShaders.SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_PIXEL_COMPOSITE;
        };
    }

    private static ShadowAuraStyle safeStyle(ShadowAuraStyle style) {
        return style == null ? ShadowAuraStyle.DEFAULT : style;
    }

    private static DensityFboPipeline pipeline(ShadowAuraStyle style) {
        return pipeline(new ShadowAuraRenderBatch(style, false));
    }

    private static DensityFboPipeline pipeline(ShadowAuraRenderBatch batch) {
        synchronized (STYLE_PIPELINES) {
            return STYLE_PIPELINES.computeIfAbsent(
                    batch,
                    ignored -> new DensityFboPipeline(
                            2,
                            0.82f * ShadowAuraStyleProfiles.forStyle(batch.style())
                                    .render()
                                    .blurRadiusScale()));
        }
    }

    private static int readPixelSize(
            String propertyName,
            int fallback,
            int maximum) {
        try {
            return Math.max(
                    1,
                    Math.min(maximum, Integer.parseInt(
                            System.getProperty(
                                    propertyName,
                                    Integer.toString(fallback)))));
        } catch (NumberFormatException invalidValue) {
            return fallback;
        }
    }

    private static void setupCompositeUniforms(ShaderInstance shader, boolean modeTint) {
        Minecraft mc = Minecraft.getInstance();
        float gameTime = 0f;
        if (mc.level != null) {
            gameTime = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        }

        setupCompositeUniforms(shader, gameTime, modeTint);
    }

    private static void setupCompositeUniforms(ShaderInstance shader, float gameTime, boolean modeTint) {

        Uniform uModeTint = shader.getUniform("AuraModeTint");
        if (uModeTint != null) uModeTint.set(modeTint ? 1.0f : 0.0f);

        Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) {
            uGameTime.set(gameTime / 1200.0f);
        }
        Uniform uAuraTime = shader.getUniform("AuraTime");
        if (uAuraTime != null) {
            uAuraTime.set(gameTime / 1200.0f);
        }
    }

    public static void destroy() {
        synchronized (STYLE_PIPELINES) {
            for (DensityFboPipeline pipeline : STYLE_PIPELINES.values()) {
                pipeline.destroy();
            }
            STYLE_PIPELINES.clear();
            STYLE_PIPELINES.put(new ShadowAuraRenderBatch(ShadowAuraStyle.SIGNATURE, false), PIPELINE);
        }
    }
}
