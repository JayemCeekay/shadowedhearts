package com.jayemceekay.shadowedhearts.client.render.block;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.List;

/** Seed, material kind and opacity travel with each quad, including batched cores. */
public final class UmbrafallCoreSeepRenderTypes extends RenderStateShard {
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0).add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2).add("Normal", VertexFormatElement.NORMAL)
            .padding(1).build();

    public static final RenderType SEEP = material(false);
    public static final RenderType FABULOUS_SEEP = new FabulousSeepRenderType(material(true));

    private UmbrafallCoreSeepRenderTypes() { super("umbrafall_core_seep", () -> { }, () -> { }); }

    private static RenderType material(boolean fabulous) {
        return RenderType.create(fabulous ? "shadowedhearts:umbrafall_core_seep_fabulous"
                        : "shadowedhearts:umbrafall_core_seep", FORMAT, VertexFormat.Mode.QUADS,
                32768, false, true, RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ModShaders.UMBRAFALL_CORE_SEEP))
                        .setLightmapState(LIGHTMAP).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(ITEM_ENTITY_TARGET).createCompositeState(false));
    }

    private static final class FabulousSeepRenderType extends RenderType {
        private FabulousSeepRenderType(RenderType colorPass) {
            super("shadowedhearts:umbrafall_core_seep_fabulous", FORMAT, VertexFormat.Mode.QUADS,
                    32768, false, true, colorPass::setupRenderState, colorPass::clearRenderState);
        }

        @Override
        public void draw(MeshData mesh) {
            RenderSystem.assertOnRenderThread();
            VertexFormat format = mesh.drawState().format();
            setupRenderState();
            try {
                // Soft, overlapping segments must all blend before any of them writes
                // depth, or a nearly transparent edge can cut a hole in another segment.
                BufferUploader.drawWithShader(mesh);
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthMask(true);
                // Fabulous still needs the nearest visible depth for its compositor.
                // Upload consumed the mesh; replay the same VBO and sorted indices.
                format.getImmediateDrawVertexBuffer().drawWithShader(RenderSystem.getModelViewMatrix(),
                        RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            } finally {
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(false);
                clearRenderState();
            }
        }
    }

    public static void emit(VertexConsumer vertices, PoseStack.Pose pose,
                            List<UmbrafallCoreSeepGeometry.Quad> quads, int light) {
        for (var quad : quads) {
            int metadata = OverlayTexture.pack(quad.kind(), Math.clamp(Math.round(quad.seed()), 0, 32767));
            vertex(vertices, pose, quad.a(), quad.alpha(), metadata, light);
            vertex(vertices, pose, quad.b(), quad.alpha(), metadata, light);
            vertex(vertices, pose, quad.c(), quad.alpha(), metadata, light);
            vertex(vertices, pose, quad.d(), quad.alpha(), metadata, light);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, UmbrafallCoreSeepGeometry.Vertex v,
                               float alpha, int metadata, int light) {
        // Keep setters separate: loader sprite/consumer wrappers need not return themselves.
        out.addVertex(pose.pose(), (float) v.x(), (float) v.y(), (float) v.z());
        out.setColor(1F, 1F, 1F, alpha);
        out.setUv(v.u(), v.v());
        out.setOverlay(metadata);
        out.setLight(light);
        out.setNormal(pose, 0, 1, 0);
    }
}
