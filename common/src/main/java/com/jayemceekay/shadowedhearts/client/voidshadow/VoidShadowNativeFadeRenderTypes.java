package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;

/** Native shader fading that leaves transparent geometry out of the depth buffer. */
final class VoidShadowNativeFadeRenderTypes extends RenderStateShard {
    private static final Map<VoidShadowRenderTypes.Material, RenderType> TYPES = new HashMap<>();

    private VoidShadowNativeFadeRenderTypes() {
        super("void_shadow_native_fade", () -> { }, () -> { });
    }

    static RenderType nativeFade(VoidShadowRenderTypes.Material material) {
        return TYPES.computeIfAbsent(material, key -> RenderType.create(
                "shadowedhearts:void_shadow_native_fade", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        // This native variant tests texture alpha before vertex opacity.
                        // Keep culling as a separate state to avoid the cull shader's
                        // combined-alpha discard cutting off the end of the fade.
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(key.texture(), false, false))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(key.cull() ? CULL : NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true)));
    }
}
