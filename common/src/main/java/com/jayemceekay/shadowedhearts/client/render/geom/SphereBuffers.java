package com.jayemceekay.shadowedhearts.client.render.geom;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Small reusable unit icosphere mesh emitter for aura shells.
 *
 * Generates a unit-radius icosphere (subdivided twice by default) and provides
 * a draw method that emits TRIANGLES into a VertexConsumer using the provided matrix.
 *
 * We only need Position + UV0 + Color (+ Light) compatible with DefaultVertexFormat.PARTICLE.
 * UV0 is derived from the normalized local position n.xy mapped to [0,1], suitable for circular mask.
 */
public final class SphereBuffers {
    private SphereBuffers() {}

    private static Mesh SUBDIV2; // cached unit icosphere with 2 subdivisions (~320 tris)

    private record Mesh(float[] positions, int[] indices) {}

    /** Returns the cached unit icosphere mesh (2 subdivisions). */
    private static Mesh mesh() {
        if (SUBDIV2 == null) SUBDIV2 = buildIcosphere(2);
        return SUBDIV2;
    }

    public static void drawPositionOnly(VertexConsumer vc) {
        Mesh m = mesh();
        float[] pos = m.positions;
        int[] idx = m.indices;

        // Identity transform: keep in object space (the shader applies uMVP)
        Matrix4f I = new Matrix4f().identity();

        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i] * 3, ib = idx[i+1] * 3, ic = idx[i+2] * 3;

            vc.addVertex(I, pos[ia],   pos[ia+1],   pos[ia+2]);
            vc.addVertex(I, pos[ib],   pos[ib+1],   pos[ib+2]);
            vc.addVertex(I, pos[ic],   pos[ic+1],   pos[ic+2]);
        }
    }


    /** Draws the cached unit icosphere as triangles into the given VertexConsumer. */
    public static void drawUnitSphere(VertexConsumer vc, Matrix4f mat, float r, float g, float b, float a) {
        Mesh m = mesh();
        float[] pos = m.positions;
        int[] idx = m.indices;
        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i] * 3;
            int ib = idx[i + 1] * 3;
            int ic = idx[i + 2] * 3;
            // Vertex A
            float ax = pos[ia];
            float ay = pos[ia + 1];
            float az = pos[ia + 2];
            float au = 0.5f + 0.5f * ax; // simple sphere mask mapping from n.xy
            float av = 0.5f + 0.5f * ay;
            vc.addVertex(mat, ax, ay, az).setUv(au, av).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);
            // Vertex B
            float bx = pos[ib];
            float by = pos[ib + 1];
            float bz = pos[ib + 2];
            float bu = 0.5f + 0.5f * bx;
            float bv = 0.5f + 0.5f * by;
            vc.addVertex(mat, bx, by, bz).setUv(bu, bv).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);
            // Vertex C
            float cx = pos[ic];
            float cy = pos[ic + 1];
            float cz = pos[ic + 2];
            float cu = 0.5f + 0.5f * cx;
            float cv = 0.5f + 0.5f * cy;
            vc.addVertex(mat, cx, cy, cz).setUv(cu, cv).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);
        }
    }

    // --- Icosphere construction ---

    private static Mesh buildIcosphere(int subdivisions) {
        // Base icosahedron vertices
        float t = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);
        List<float[]> verts = new ArrayList<>();
        addNorm(verts, -1,  t,  0);
        addNorm(verts,  1,  t,  0);
        addNorm(verts, -1, -t,  0);
        addNorm(verts,  1, -t,  0);

        addNorm(verts,  0, -1,  t);
        addNorm(verts,  0,  1,  t);
        addNorm(verts,  0, -1, -t);
        addNorm(verts,  0,  1, -t);

        addNorm(verts,  t,  0, -1);
        addNorm(verts,  t,  0,  1);
        addNorm(verts, -t,  0, -1);
        addNorm(verts, -t,  0,  1);

        int[] faces = {
                0, 11, 5,  0, 5, 1,  0, 1, 7,  0, 7,10,  0,10,11,
                1, 5, 9,  5,11, 4, 11,10,2, 10,7,6,  7,1,8,
                3, 9, 4,  3, 4, 2,  3, 2, 6,  3, 6, 8,  3, 8, 9,
                4, 9, 5,  2, 4,11,  6, 2,10,  8, 6, 7,  9, 8, 1
        };

        // Subdivide
        List<int[]> tris = new ArrayList<>();
        for (int i = 0; i < faces.length; i += 3) tris.add(new int[]{faces[i], faces[i+1], faces[i+2]});
        java.util.Map<Long, Integer> midCache;
        for (int s = 0; s < subdivisions; s++) {
            List<int[]> next = new ArrayList<>();
            midCache = new java.util.HashMap<>();
            for (int[] tri : tris) {
                int a = tri[0], b = tri[1], c = tri[2];
                int ab = midpointIndex(verts, midCache, a, b);
                int bc = midpointIndex(verts, midCache, b, c);
                int ca = midpointIndex(verts, midCache, c, a);
                next.add(new int[]{a, ab, ca});
                next.add(new int[]{b, bc, ab});
                next.add(new int[]{c, ca, bc});
                next.add(new int[]{ab, bc, ca});
            }
            tris = next;
        }

        // Flatten arrays
        float[] positions = new float[verts.size()*3];
        for (int i = 0; i < verts.size(); i++) {
            float[] v = verts.get(i);
            positions[i*3] = v[0]; positions[i*3+1] = v[1]; positions[i*3+2] = v[2];
        }
        int[] indices = new int[tris.size()*3];
        int k = 0;
        for (int[] ttri : tris) {
            indices[k++] = ttri[0];
            indices[k++] = ttri[1];
            indices[k++] = ttri[2];
        }
        return new Mesh(positions, indices);
    }

    private static void addNorm(List<float[]> verts, double x, double y, double z) {
        double len = Math.sqrt(x*x + y*y + z*z);
        verts.add(new float[]{(float)(x/len), (float)(y/len), (float)(z/len)});
    }

    private static int midpointIndex(List<float[]> verts, java.util.Map<Long, Integer> cache, int i1, int i2) {
        long key = (((long) Math.min(i1,i2)) << 32) | (long) Math.max(i1,i2);
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        float[] v1 = verts.get(i1);
        float[] v2 = verts.get(i2);
        float mx = (v1[0]+v2[0])*0.5f;
        float my = (v1[1]+v2[1])*0.5f;
        float mz = (v1[2]+v2[2])*0.5f;
        double len = Math.sqrt(mx*mx + my*my + mz*mz);
        int idx = verts.size();
        verts.add(new float[]{(float)(mx/len),(float)(my/len),(float)(mz/len)});
        cache.put(key, idx);
        return idx;
    }
}
