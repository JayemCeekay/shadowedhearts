package com.jayemceekay.shadowedhearts.client.render.block;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** A batched liquid material: all tank-specific values travel with its vertices. */
public final class UmbraLiquidRenderTypes extends RenderStateShard {
    // A distinct entity layout also keeps Iris from substituting its extended stride.
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1).build();

    public static final RenderType LIQUID = liquid(false);
    public static final RenderType FABULOUS_LIQUID = liquid(true);

    private static RenderType liquid(boolean fabulous) {
        return RenderType.create(
            fabulous ? "shadowedhearts:umbra_liquid_fabulous" : "shadowedhearts:umbra_liquid",
            FORMAT, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> ModShaders.UMBRA_LIQUID))
                    .setLightmapState(LIGHTMAP)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    // Fabulous sorts the item/entity layer by its depth image.
                    .setWriteMaskState(fabulous ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .createCompositeState(false));
    }

    private static final float[][] CORNERS = {
            {1,0,0, 0,0,0, 0,1,0, 1,1,0},
            {1,0,1, 1,0,0, 1,1,0, 1,1,1},
            {0,0,1, 1,0,1, 1,1,1, 0,1,1},
            {0,0,0, 0,0,1, 0,1,1, 0,1,0},
            {0,1,0, 0,1,1, 1,1,1, 1,1,0},
            {0,0,0, 1,0,0, 1,0,1, 0,0,1}
    };
    private static final float[][] NORMALS = {
            {0,0,-1}, {1,0,0}, {0,0,1}, {-1,0,0}, {0,1,0}, {0,-1,0}
    };

    private UmbraLiquidRenderTypes() {
        super("umbra_liquid", () -> { }, () -> { });
    }

    /** RGB carries box coordinates, UV0 height/seed, and UV1 rotation/width; no atlas UVs. */
    public static void emitVolume(VertexConsumer vertices, PoseStack.Pose pose,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  float seed, int quarterTurns, int light) {
        float width = maxX - minX, height = maxY - minY, depth = maxZ - minZ;
        int metadata = OverlayTexture.pack(quarterTurns, Math.round(width * 4096.0F));
        for (int face = 0; face < CORNERS.length; face++) {
            float[] corners = CORNERS[face], normal = NORMALS[face];
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = corners[vertex * 3], y = corners[vertex * 3 + 1], z = corners[vertex * 3 + 2];
                vertices.addVertex(pose.pose(), minX + x * width, minY + y * height, minZ + z * depth)
                        .setColor(x, y, z, 1.0F).setUv(height, seed)
                        .setOverlay(metadata).setLight(light)
                        .setNormal(pose, normal[0], normal[1], normal[2]);
            }
        }
    }
}
