package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.mixin.client.DarkBallCompositeRenderTypeAccessor;
import com.jayemceekay.shadowedhearts.mixin.client.DarkBallCompositeStateAccessor;
import com.jayemceekay.shadowedhearts.mixin.client.DarkBallTextureStateAccessor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Keeps Dark Ball snapshot capture limited to actual entity model geometry.
 */
final class DarkBallEntityRenderTypeFilter extends RenderStateShard {

    private DarkBallEntityRenderTypeFilter() {
        super("dark_ball_material_policy", () -> { }, () -> { });
    }

    static boolean accepts(RenderType renderType) {
        return captureMaterial(renderType) != null;
    }

    /**
     * Capture model layers with a reproducible resource-backed texture. Glint,
     * outlines, shadows, decals, scrolling UV effects and dynamic/interpolated
     * textures are decoration or cannot be replayed faithfully from a resource.
     * They are omitted; they must never borrow the base model's alpha mask.
     */
    static DarkBallFieldMaskBufferSource.CapturedMaterial captureMaterial(RenderType renderType) {
        if (renderType.mode() != VertexFormat.Mode.QUADS
                || renderType.format() != DefaultVertexFormat.NEW_ENTITY
                || !(renderType instanceof DarkBallCompositeRenderTypeAccessor accessor)) {
            return null;
        }
        DarkBallCompositeStateAccessor state = (DarkBallCompositeStateAccessor)
                (Object) accessor.shadowedhearts$getCompositeState();
        if (state.shadowedhearts$getTexturingState() != DEFAULT_TEXTURING
                || !(state.shadowedhearts$getTextureState() instanceof DarkBallTextureStateAccessor textureState)) {
            return null;
        }
        ResourceLocation texture = textureState.shadowedhearts$getTexture().orElse(null);
        ShaderStateShard shader = state.shadowedhearts$getShaderState();
        if (texture == null || !isModelShader(shader)) {
            return null;
        }
        return new DarkBallFieldMaskBufferSource.CapturedMaterial(texture,
                shader == RENDERTYPE_ENTITY_SOLID_SHADER
                        ? DarkBallFieldMaskBufferSource.AlphaMode.OPAQUE
                        : DarkBallFieldMaskBufferSource.AlphaMode.TEXTURED);
    }

    private static boolean isModelShader(ShaderStateShard shader) {
        return shader == RENDERTYPE_ENTITY_SOLID_SHADER
                || shader == RENDERTYPE_ENTITY_CUTOUT_SHADER
                || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER
                || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER
                || shader == RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER
                || shader == RENDERTYPE_ENTITY_TRANSLUCENT_SHADER
                || shader == RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER
                || shader == RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER
                || shader == RENDERTYPE_ENTITY_NO_OUTLINE_SHADER
                || shader == RENDERTYPE_EYES_SHADER;
    }
}
