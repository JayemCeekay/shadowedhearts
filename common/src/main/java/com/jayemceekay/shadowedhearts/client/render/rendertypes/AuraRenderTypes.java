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

    public static RenderType shadow_trail() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.SHADOW_AURA_TRAIL != null
                        ? ModShaders.SHADOW_AURA_TRAIL
                        : GameRenderer.getParticleShader()))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_trail", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    public static RenderType shadowPokemonAuraMask(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> ModShaders.SHADOW_POKEMON_AURA_MASK != null
                        ? ModShaders.SHADOW_POKEMON_AURA_MASK
                        : GameRenderer.getRendertypeEntityCutoutShader()))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_pokemon_aura_mask", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, true, false, state);
    }

    public static RenderType shadowPokemonAuraClassification(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_pokemon_aura_classification", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1024, true, true, state);
    }

    public static RenderType shadowPokemonAuraClassificationBoxes() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_pokemon_aura_classification_boxes", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, true, state);
    }

    public static RenderType shadowPokemonAuraDebugMarkers() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getParticleShader))
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .createCompositeState(true);
        return RenderType.create("shadowedhearts:shadow_pokemon_aura_debug_markers", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.TRIANGLES, 4096, false, true, state);
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
