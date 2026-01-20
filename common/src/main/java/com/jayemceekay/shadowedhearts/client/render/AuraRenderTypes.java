package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class AuraRenderTypes {

    private static final RenderStateShard.TransparencyStateShard PREMULTIPLIED_TRANSPARENCY = new RenderStateShard.TransparencyStateShard(
            "shadowedhearts_premultiplied",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.ONE,
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

    public static RenderType shadow_fog(boolean throughTerrain) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.SHADOW_AURA_FOG_CYLINDER != null
                        ? ModShaders.SHADOW_AURA_FOG_CYLINDER
                        : GameRenderer.getParticleShader()))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setDepthTestState(throughTerrain ? RenderStateShard.NO_DEPTH_TEST : RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_aura_fog_rendertype" + (throughTerrain ? "_no_depth" : ""), DefaultVertexFormat.PARTICLE, VertexFormat.Mode.TRIANGLES, 512, false, true, state);
    }

    public static RenderType shadow_xd(boolean throughTerrain) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.SHADOW_AURA_XD_CYLINDER != null
                        ? ModShaders.SHADOW_AURA_XD_CYLINDER
                        : GameRenderer.getParticleShader()))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setDepthTestState(throughTerrain ? RenderStateShard.NO_DEPTH_TEST : RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(RenderStateShard.CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_aura_xd_rendertype" + (throughTerrain ? "_no_depth" : ""), DefaultVertexFormat.PARTICLE, VertexFormat.Mode.TRIANGLES, 512, false, true, state);
    }

    public static RenderType shadow_fog() {
        return shadow_fog(false);
    }

    public static RenderType shadow_xd() {
        return shadow_xd(false);
    }

    public static RenderType shadow_pool() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.SHADOW_POOL != null
                        ? ModShaders.SHADOW_POOL
                        : GameRenderer.getParticleShader()))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_pool", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    public static RenderType whistle_ground_overlay() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.WHISTLE_GROUND_OVERLAY != null
                        ? ModShaders.WHISTLE_GROUND_OVERLAY
                        : GameRenderer.getParticleShader()))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:whistle_ground_overlay", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.TRIANGLES, 256, false, true, state);
    }

    public static RenderType screen_decal() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(PREMULTIPLIED_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:screen_decal", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, state);
    }
}
