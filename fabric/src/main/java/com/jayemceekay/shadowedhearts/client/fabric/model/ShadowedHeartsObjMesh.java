package com.jayemceekay.shadowedhearts.client.fabric.model;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlReader;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjReader;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ShadowedHeartsObjMesh {
    private final Obj geometry;
    private final Map<String, String> materialTextures;

    private ShadowedHeartsObjMesh(Obj geometry, Map<String, String> materialTextures) {
        this.geometry = geometry;
        this.materialTextures = Map.copyOf(materialTextures);
    }

    static ShadowedHeartsObjMesh read(ResourceManager resources, ResourceLocation objId) throws IOException {
        Obj geometry;
        try (InputStream input = resources.open(objId)) {
            geometry = ObjReader.read(input);
        }

        String path = objId.getPath();
        String directory = path.substring(0, path.lastIndexOf('/') + 1);
        Map<String, String> materialTextures = new HashMap<>();
        for (String library : geometry.getMtlFileNames()) {
            ResourceLocation materialId = library.contains(":")
                    ? ResourceLocation.parse(library) : objId.withPath(directory + library);
            try (InputStream input = resources.open(materialId)) {
                for (Mtl material : MtlReader.read(input)) {
                    if (material.getMapKd() != null) {
                        materialTextures.put(material.getName(), material.getMapKd());
                    }
                }
            }
        }
        return new ShadowedHeartsObjMesh(geometry, materialTextures);
    }

    List<BakedQuad> bake(ModelState state, boolean flipV, Function<String, TextureAtlasSprite> resolveSprite) {
        Quaternionf rotation = state.getRotation().getLeftRotation();
        List<BakedQuad> quads = new ArrayList<>(geometry.getNumFaces());
        TextureAtlasSprite sprite = null;
        for (int i = 0; i < geometry.getNumFaces(); i++) {
            ObjFace face = geometry.getFace(i);
            String nextMaterial = geometry.getActivatedMaterialGroupName(face);
            if (nextMaterial != null || sprite == null) {
                String material = nextMaterial == null ? "" : nextMaterial;
                sprite = resolveSprite.apply(materialTextures.get(material));
            }
            quads.add(bakeFace(face, rotation, flipV, sprite));
        }
        return List.copyOf(quads);
    }

    private BakedQuad bakeFace(ObjFace face, Quaternionf rotation, boolean flipV, TextureAtlasSprite sprite) {
        int corners = face.getNumVertices();
        if (corners != 3 && corners != 4) {
            throw new IllegalArgumentException("Expected a triangle or quad in OBJ, found a face with " + corners + " vertices");
        }

        int[] data = new int[FaceBakery.VERTEX_COUNT * FaceBakery.VERTEX_INT_SIZE];
        for (int vertex = 0; vertex < FaceBakery.VERTEX_COUNT; vertex++) {
            // Minecraft's block vertex format always has four corners. Repeat the last one for a triangle.
            int corner = Math.min(vertex, corners - 1);
            Vector3f position = vector(geometry.getVertex(face.getVertexIndex(corner)));
            position.sub(0.5f, 0.5f, 0.5f).rotate(rotation).add(0.5f, 0.5f, 0.5f);
            int offset = vertex * FaceBakery.VERTEX_INT_SIZE;
            data[offset] = Float.floatToRawIntBits(position.x);
            data[offset + 1] = Float.floatToRawIntBits(position.y);
            data[offset + 2] = Float.floatToRawIntBits(position.z);
            // Texture alpha and RGB supply the color, as with the original OBJ models.
            data[offset + 3] = 0xFFFFFFFF;
            if (face.containsTexCoordIndices()) {
                FloatTuple uv = geometry.getTexCoord(face.getTexCoordIndex(corner));
                data[offset + FaceBakery.UV_INDEX] = Float.floatToRawIntBits(sprite.getU(uv.getX()));
                data[offset + FaceBakery.UV_INDEX + 1] = Float.floatToRawIntBits(sprite.getV(flipV ? 1 - uv.getY() : uv.getY()));
            } else {
                data[offset + FaceBakery.UV_INDEX] = Float.floatToRawIntBits(sprite.getU0());
                data[offset + FaceBakery.UV_INDEX + 1] = Float.floatToRawIntBits(sprite.getV0());
            }
            if (face.containsNormalIndices()) {
                Vector3f normal = vector(geometry.getNormal(face.getNormalIndex(corner))).rotate(rotation);
                data[offset + 7] = normalByte(normal.x) | normalByte(normal.y) << 8 | normalByte(normal.z) << 16;
            }
        }
        return new BakedQuad(data, -1, FaceBakery.calculateFacing(data), sprite, true);
    }

    private static Vector3f vector(FloatTuple tuple) {
        return new Vector3f(tuple.getX(), tuple.getY(), tuple.getZ());
    }

    private static int normalByte(float component) {
        return Math.round(Math.clamp(component, -1.0f, 1.0f) * 127) & 0xFF;
    }
}
