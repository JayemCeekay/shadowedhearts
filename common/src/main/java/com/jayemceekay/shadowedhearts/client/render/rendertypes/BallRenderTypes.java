package com.jayemceekay.shadowedhearts.client.render.rendertypes;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Central factory for RenderTypes used by ball, capture, and trail VFX.
 *
 * <p>Shader states are supplied lazily so resource reloads can swap shader
 * instances without rebuilding all call sites. Most methods also fall back to
 * vanilla shaders while custom shader registration is unavailable.
 */
public final class BallRenderTypes {

    private BallRenderTypes() {
    }

    /**
     * Offscreen passes bind their target FBO before entity rendering. A no-op
     * output state prevents RenderType setup from rebinding Minecraft's main
     * target when the hidden Pokemon buffers flush.
     */
    private static final RenderStateShard.OutputStateShard CURRENT_BOUND_TARGET = new RenderStateShard.OutputStateShard(
            "shadowedhearts_current_bound_target",
            () -> {
            },
            () -> {
            }
    );

    /**
     * Full additive blend used by glow, flare, and density-splat quads.
     */
    private static final RenderStateShard.TransparencyStateShard ADDITIVE_TRANSPARENCY = new RenderStateShard.TransparencyStateShard(
            "shadowedhearts_additive",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE
                );
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            }
    );

    /**
     * Additive world-space ball glow billboard.
     *
     * @param texture retained for API compatibility; this shader path is procedural
     */
    public static RenderType ballGlow(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.BALL_GLOW != null
                                ? ModShaders.BALL_GLOW
                                : GameRenderer.getParticleShader()
                ))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:ball_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    /**
     * HUD variant of ball glow: no depth test and no culling so it always shows in GUI overlays.
     */
    public static RenderType ballGlowHud() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.BALL_GLOW != null
                                ? ModShaders.BALL_GLOW
                                : GameRenderer.getParticleShader()
                ))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:ball_glow_hud", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    /**
     * Procedural radial orb glow + starburst + halo — uses ball_orb_glow shader.
     */
    public static RenderType ballOrbGlow() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.BALL_ORB_GLOW != null
                                ? ModShaders.BALL_ORB_GLOW
                                : GameRenderer.getParticleShader()
                ))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:ball_orb_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    /**
     * HUD variant of orb glow.
     */
    public static RenderType ballOrbGlowHud() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.BALL_ORB_GLOW != null
                                ? ModShaders.BALL_ORB_GLOW
                                : GameRenderer.getParticleShader()
                ))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:ball_orb_glow_hud", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    /**
     * Textured additive ribbon used for in-flight ball trails.
     */
    public static RenderType trailAdditive() {
        ResourceLocation tex = ResourceLocation.parse("shadowedhearts:textures/particle/ball_trail128x32.png");
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.BALL_TRAIL != null
                        ? ModShaders.BALL_TRAIL
                        : GameRenderer.getPositionColorShader()))
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.NO_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:ball_trail_add", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, false, true, state);
    }

    /**
     * Penumbra (shadow aura) ribbon trail — uses alpha blending for dark fog core visibility.
     */
    public static RenderType penumbraTrailRibbon() {
        ResourceLocation tex = ResourceLocation.parse("shadowedhearts:textures/particle/penumbra_ribbon_trail.png");
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.PENUMBRA_TRAIL_RIBBON != null
                        ? ModShaders.PENUMBRA_TRAIL_RIBBON
                        : GameRenderer.getPositionColorShader()))
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.NO_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:penumbra_trail_ribbon", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, false, true, state);
    }

    /**
     * Lens flare billboard — uses snag_flare shader with additive blending.
     * Renders procedural streak/spike patterns; texture is optional overlay.
     */
    public static RenderType flareAdditive() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.SNAG_FLARE != null
                                ? ModShaders.SNAG_FLARE
                                : GameRenderer.getParticleShader()
                ))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:snag_flare", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    /**
     * Orb shell billboard — uses snag_orb shader with noise texture for crackling sphere shell.
     */
    public static RenderType orbShell() {
        ResourceLocation tex = ResourceLocation.parse("shadowedhearts:textures/vfx/orb_shell_noise.png");
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.SNAG_ORB != null
                                ? ModShaders.SNAG_ORB
                                : GameRenderer.getParticleShader()
                ))
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:snag_orb", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    /**
     * VFX particle additive — uses ball_trail shader with a specified VFX texture.
     * Used for spark streaks, shock rings, soft glow motes, etc.
     */
    public static RenderType vfxAdditive(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.BALL_TRAIL != null
                        ? ModShaders.BALL_TRAIL
                        : GameRenderer.getPositionColorShader()))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:vfx_add", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, false, true, state);
    }

    /**
     * Standard alpha blending for entity replacement passes that still need to
     * write depth.
     */
    private static final RenderStateShard.TransparencyStateShard ALPHA_TRANSPARENCY = new RenderStateShard.TransparencyStateShard(
            "shadowedhearts_alpha",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
                );
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            }
    );

    /**
     * Pokémon dissolve render type — replaces entityCutout during snag absorption.
     * Uses the snag_dissolve shader with the entity's own texture on Sampler0
     * and dissolve noise on Sampler3 (bound manually before drawing).
     */
    public static RenderType dissolve(ResourceLocation entityTexture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.SNAG_DISSOLVE != null
                                ? ModShaders.SNAG_DISSOLVE
                                : GameRenderer.getRendertypeEntityCutoutShader()
                ))
                .setTextureState(new RenderStateShard.TextureStateShard(entityTexture, false, false))
                .setTransparencyState(ALPHA_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:snag_dissolve", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false, state);
    }

    /**
     * Dark Ball field mask rendered into the currently bound density FBO.
     */
    public static RenderType darkBallMask(ResourceLocation entityTexture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.DARK_BALL_MASK != null
                                ? ModShaders.DARK_BALL_MASK
                                : GameRenderer.getRendertypeEntityCutoutShader()
                ))
                .setTextureState(new RenderStateShard.TextureStateShard(entityTexture, false, false))
                .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setOutputState(CURRENT_BOUND_TARGET)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:dark_ball_mask", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, true, false, state);
    }

    /**
     * Dark Ball proxy-depth pass rendered into the currently bound front/back
     * depth proxy FBO. The caller selects front/back by setting GL cull face.
     */
    public static RenderType darkBallProxyDepth(ResourceLocation entityTexture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() ->
                        ModShaders.DARK_BALL_PROXY_DEPTH != null
                                ? ModShaders.DARK_BALL_PROXY_DEPTH
                                : GameRenderer.getRendertypeEntityCutoutShader()
                ))
                .setTextureState(new RenderStateShard.TextureStateShard(entityTexture, false, false))
                .setTransparencyState(ALPHA_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.CULL)
                .setOutputState(CURRENT_BOUND_TARGET)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:dark_ball_proxy_depth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, true, false, state);
    }

    // Pre-built VFX render types for common particle textures
    private static final ResourceLocation SPARK_STREAK_TEX = ResourceLocation.parse("shadowedhearts:textures/vfx/spark_streak.png");
    private static final ResourceLocation SOFT_GLOW_TEX = ResourceLocation.parse("shadowedhearts:textures/vfx/soft_glow.png");
    private static final ResourceLocation SHOCK_RING_TEX = ResourceLocation.parse("shadowedhearts:textures/vfx/shock_ring.png");
    private static final ResourceLocation CONVERGENCE_RING_TEX = ResourceLocation.parse("shadowedhearts:textures/vfx/convergence_ring.png");

    public static RenderType sparkStreakAdditive() { return vfxAdditive(SPARK_STREAK_TEX); }
    public static RenderType softGlowAdditive()    { return vfxAdditive(SOFT_GLOW_TEX); }
    public static RenderType shockRingAdditive()   { return vfxAdditive(SHOCK_RING_TEX); }

    /**
     * Shock ring glow — uses the vanilla entity-cutout shader (no custom uniforms)
     * so it renders texture × vertex-color directly without uStrength dependency.
     */
    public static RenderType shockRingGlowAdditive() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(
                        GameRenderer::getRendertypeEntityCutoutShader))
                .setTextureState(new RenderStateShard.TextureStateShard(CONVERGENCE_RING_TEX, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shock_ring_glow_add", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, false, true, state);
    }

    /**
     * Simple additive spark glow — uses the vanilla entity-cutout shader so it
     * just renders texture × vertex-color with no custom uniforms required.
     * Suitable for simple billboard spark quads.
     */
    public static RenderType sparkGlowAdditive() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(
                        GameRenderer::getRendertypeEntityCutoutShader))
                .setTextureState(new RenderStateShard.TextureStateShard(SPARK_STREAK_TEX, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:spark_glow_add", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, false, true, state);
    }

    /**
     * Secondary, thinner core streak texture for trails.
     */
    public static RenderType trailCoreAdditive() {
        ResourceLocation tex = ResourceLocation.parse("shadowedhearts:textures/particle/ball_trail128x32_core.png");
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.BALL_TRAIL != null
                        ? ModShaders.BALL_TRAIL
                        : GameRenderer.getPositionColorShader()))
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:ball_trail_core_add", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, false, true, state);
    }
}
