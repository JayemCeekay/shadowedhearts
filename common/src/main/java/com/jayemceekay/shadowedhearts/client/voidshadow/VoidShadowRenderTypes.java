package com.jayemceekay.shadowedhearts.client.voidshadow;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;

/** Batches by actual layer texture and alpha mode; never by mutable entity uniforms. */
final class VoidShadowRenderTypes extends RenderStateShard {
    private static final Map<Material, RenderType> TYPES = new HashMap<>();
    private static final Map<RenderType, Material> MATERIALS = new IdentityHashMap<>();

    private VoidShadowRenderTypes() {
        super("void_shadow_material", () -> { }, () -> { });
    }

    static RenderType liquid(Material material) {
        RenderType type = TYPES.computeIfAbsent(material, key -> RenderType.create(
                "shadowedhearts:void_shadow_liquid", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, false, key.translucent(),
                RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> VoidShadowShaders.LIQUID == null
                                ? GameRenderer.getRendertypeEntityCutoutNoCullShader()
                                : VoidShadowShaders.liquid(key.opaque(), key.translucent() ? 0.01f : 0.1f)))
                        .setTextureState(new TextureStateShard(key.texture(), false, false))
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setTransparencyState(key.translucent() ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                        .setCullState(key.cull() ? CULL : NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(key.fading() ? COLOR_WRITE : COLOR_DEPTH_WRITE)
                        .createCompositeState(true)));
        MATERIALS.put(type, material);
        return type;
    }

    static Material material(RenderType type) { return MATERIALS.get(type); }

    record Material(ResourceLocation texture, boolean opaque, boolean translucent, boolean cull, boolean fading) {
        Material(ResourceLocation texture, boolean opaque, boolean translucent, boolean cull) {
            this(texture, opaque, translucent, cull, false);
        }
        Material fade() { return new Material(texture, opaque, true, cull, true); }
    }
}
