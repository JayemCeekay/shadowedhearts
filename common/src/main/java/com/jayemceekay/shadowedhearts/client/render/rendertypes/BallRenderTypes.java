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

public final class BallRenderTypes {

    private BallRenderTypes() {
    }

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
