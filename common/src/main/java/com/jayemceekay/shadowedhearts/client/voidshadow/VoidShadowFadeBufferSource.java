package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.jayemceekay.shadowedhearts.mixin.client.DarkBallCompositeRenderTypeAccessor;
import com.jayemceekay.shadowedhearts.mixin.client.DarkBallCompositeStateAccessor;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

/** Per-vertex opacity keeps batched encounters independent without mutable shader uniforms. */
final class VoidShadowFadeBufferSource extends RenderStateShard implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float opacity;

    VoidShadowFadeBufferSource(MultiBufferSource delegate, float opacity) {
        super("void_shadow_fade", () -> { }, () -> { });
        this.delegate = delegate;
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) {
        boolean emissive = type instanceof DarkBallCompositeRenderTypeAccessor access
                && ((DarkBallCompositeStateAccessor) (Object) access.shadowedhearts$getCompositeState())
                .shadowedhearts$getShaderState() == RENDERTYPE_EYES_SHADER;
        var liquid = VoidShadowRenderTypes.material(type);
        var material = liquid == null ? VoidShadowBufferSource.material(type) : liquid;
        if (material == null) return delegate.getBuffer(type);
        if (opacity < 0.999f && !emissive) {
            if (liquid != null) type = VoidShadowRenderTypes.liquid(liquid.fade());
            else type = VoidShadowNativeFadeRenderTypes.nativeFade(material);
        }
        return new OpacityConsumer(delegate.getBuffer(type), opacity, emissive);
    }

    MultiBufferSource nativeBuffers() { return delegate; }

    static final class OpacityConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float opacity;
        private final boolean emissive;

        OpacityConsumer(VertexConsumer delegate, float opacity, boolean emissive) {
            this.delegate = delegate; this.opacity = opacity; this.emissive = emissive;
        }
        public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            // Native eyes use additive RGB; their shader does not premultiply by alpha.
            delegate.setColor(emissive ? Math.round(r * opacity) : r,
                    emissive ? Math.round(g * opacity) : g, emissive ? Math.round(b * opacity) : b,
                    Math.round(a * opacity));
            return this;
        }
        public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
