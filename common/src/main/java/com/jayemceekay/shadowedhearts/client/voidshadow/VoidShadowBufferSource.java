package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.jayemceekay.shadowedhearts.mixin.client.DarkBallCompositeRenderTypeAccessor;
import com.jayemceekay.shadowedhearts.mixin.client.DarkBallCompositeStateAccessor;
import com.jayemceekay.shadowedhearts.mixin.client.DarkBallTextureStateAccessor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces posed Pokemon surface material while retaining each native layer's alpha.
 * Iris retains the native black draw for pack depth/shadows, then replays a
 * bounded stable body with the liquid shader before the pack's final passes.
 */
public final class VoidShadowBufferSource extends RenderStateShard implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final boolean nativeLiquid;

    public VoidShadowBufferSource(MultiBufferSource delegate, boolean nativeLiquid) {
        super("void_shadow_buffers", () -> { }, () -> { });
        this.delegate = delegate;
        this.nativeLiquid = nativeLiquid;
    }

    /** Added pink eyes must retain their native emissive material and texture colors. */
    public MultiBufferSource nativeBuffers() {
        return delegate;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
        VoidShadowRenderTypes.Material material = material(renderType);
        if (material == null) {
            return delegate.getBuffer(renderType);
        }
        if (nativeLiquid && VoidShadowShaders.LIQUID != null) {
            return delegate.getBuffer(VoidShadowRenderTypes.liquid(material));
        }
        // A pack owns the native material, depth, shadows and extended vertex layout.
        // Keep its render type untouched: alpha cutouts and ordinary lighting survive.
        return new BlackTintVertexConsumer(delegate.getBuffer(renderType));
    }

    static VoidShadowRenderTypes.Material material(RenderType renderType) {
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
        boolean opaque = shader == RENDERTYPE_ENTITY_SOLID_SHADER;
        boolean translucent = shader == RENDERTYPE_ENTITY_TRANSLUCENT_SHADER
                || shader == RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER
                || shader == RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER;
        boolean accepted = opaque || translucent
                || shader == RENDERTYPE_ENTITY_CUTOUT_SHADER
                || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER
                || shader == RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER
                || shader == RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER
                || shader == RENDERTYPE_ENTITY_NO_OUTLINE_SHADER
                || shader == RENDERTYPE_EYES_SHADER;
        if (texture == null || !accepted) {
            return null;
        }
        return new VoidShadowRenderTypes.Material(texture, opaque, translucent,
                opaque || shader == RENDERTYPE_ENTITY_CUTOUT_SHADER
                        || shader == RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER);
    }

    /** Fallback changes RGB only. UVs, alpha, light, overlays and normals stay native. */
    record BlackTintVertexConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(7, 7, 10, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
