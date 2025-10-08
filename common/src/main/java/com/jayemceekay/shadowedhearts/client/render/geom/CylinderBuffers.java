package com.jayemceekay.shadowedhearts.client.render.geom;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

/**
 * Small reusable unit cylinder mesh emitter for aura shells (Y-axis, radius=1, height=2).
 *
 * Mirrors SphereBuffers API where reasonable. Generates and caches a side-only cylinder mesh
 * with configurable LOD (segments) and provides draw helpers that emit TRIANGLES into a
 * VertexConsumer using DefaultVertexFormat.PARTICLE-compatible attributes (Position, UV0, Color, Light).
 *
 * Added: helpers to draw with variable height (scales Y) and optional top dome (hemisphere)
 * so you can create a “cylindrical pillar with rounded crown”.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class CylinderBuffers {
    private CylinderBuffers() {}

    // LOD segment counts (0..3)
    private static final int[] LOD_SEGMENTS = new int[]{12, 18, 24, 36};
    // Hemisphere stacks per LOD (vertical rows for the dome)
    private static final int[] LOD_DOME_STACKS = new int[]{4, 5, 6, 8};

    private static Mesh[] LODS = new Mesh[4];           // side mesh per LOD
    private static Mesh[] DOME_LODS = new Mesh[4];      // top hemisphere per LOD
    private static Mesh[] DOME_BOTTOM_LODS = new Mesh[4]; // bottom hemisphere per LOD

    private record Mesh(float[] positions, int[] indices) {}

    /** Returns a mesh for the given LOD (0..3). Clamps out-of-range values. */
    public static Mesh meshForLod(int lod) {
        int clamped = Math.max(0, Math.min(3, lod));
        Mesh cached = LODS[clamped];
        if (cached == null) {
            cached = buildUnitCylinder(LOD_SEGMENTS[clamped]);
            LODS[clamped] = cached;
        }
        return cached;
    }

    /** Returns a top hemisphere (center at y=+1, radius=1) for the given LOD. */
    private static Mesh domeMeshForLod(int lod) {
        int clamped = Math.max(0, Math.min(3, lod));
        Mesh cached = DOME_LODS[clamped];
        if (cached == null) {
            cached = buildUnitHemisphereTop(LOD_SEGMENTS[clamped], LOD_DOME_STACKS[clamped]);
            DOME_LODS[clamped] = cached;
        }
        return cached;
    }

    /** Returns a bottom hemisphere (center at y=-1, radius=1) for the given LOD. */
    private static Mesh domeBottomMeshForLod(int lod) {
        int clamped = Math.max(0, Math.min(3, lod));
        Mesh cached = DOME_BOTTOM_LODS[clamped];
        if (cached == null) {
            cached = buildUnitHemisphereBottom(LOD_SEGMENTS[clamped], LOD_DOME_STACKS[clamped]);
            DOME_BOTTOM_LODS[clamped] = cached;
        }
        return cached;
    }

    /** Convenience: default LOD=2 (~24 segments). */
    private static Mesh mesh() { return meshForLod(2); }

    /** Draws unit cylinder with the given LOD (0=lowest, 3=highest). Side surface only, no caps. */
    public static void drawUnitCylinderLod(VertexConsumer vc, Matrix4f mat, float r, float g, float b, float a, int lod) {
        Mesh m = meshForLod(lod);
        float[] pos = m.positions;
        int[] idx = m.indices;
        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i] * 3;
            int ib = idx[i + 1] * 3;
            int ic = idx[i + 2] * 3;

            float ax = pos[ia];
            float ay = pos[ia + 1];
            float az = pos[ia + 2];
            float au = 0.5f + 0.5f * ax; // simple mapping like SphereBuffers (x,y)
            float av = 0.5f + 0.5f * ay;
            vc.addVertex(mat, ax, ay, az).setUv(au, av).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);

            float bx = pos[ib];
            float by = pos[ib + 1];
            float bz = pos[ib + 2];
            float bu = 0.5f + 0.5f * bx;
            float bv = 0.5f + 0.5f * by;
            vc.addVertex(mat, bx, by, bz).setUv(bu, bv).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);

            float cx = pos[ic];
            float cy = pos[ic + 1];
            float cz = pos[ic + 2];
            float cu = 0.5f + 0.5f * cx;
            float cv = 0.5f + 0.5f * cy;
            vc.addVertex(mat, cx, cy, cz).setUv(cu, cv).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);
        }
    }

    /** Draws the default LOD unit cylinder. */
    public static void drawUnitCylinder(VertexConsumer vc, Matrix4f mat, float r, float g, float b, float a) {
        drawUnitCylinderLod(vc, mat, r, g, b, a, 2);
    }

    /**
     * Draws a cylinder scaled to the given height (world units) using the specified LOD.
     * Height applies along local Y. The base mesh is unit height 2 (-1..+1), so Y scale is height/2.
     */
    public static void drawCylinderLod(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a, int lod) {
        float yScale = Math.max(0.0001f, height * 0.5f);
        Matrix4f side = new Matrix4f(mat).scale(1.0f, yScale, 1.0f);
        drawUnitCylinderLod(vc, side, r, g, b, a, lod);
    }

    /** Convenience: draw cylinder with default LOD (2) and specified height. */
    public static void drawCylinder(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a) {
        drawCylinderLod(vc, mat, height, r, g, b, a, 2);
    }

    /**
     * Draws a unit cylinder plus a top hemisphere dome (radius=1, center at y=+1) with the given LOD.
     * Use together with a Y-scale to control height, or call drawCylinderWithDomeLod which applies height.
     */
    public static void drawUnitCylinderWithDomeLod(VertexConsumer vc, Matrix4f mat, float r, float g, float b, float a, int lod) {
        // Sides
        drawUnitCylinderLod(vc, mat, r, g, b, a, lod);
        // Dome
        Mesh dome = domeMeshForLod(lod);
        float[] pos = dome.positions;
        int[] idx = dome.indices;
        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i] * 3;
            int ib = idx[i + 1] * 3;
            int ic = idx[i + 2] * 3;

            float ax = pos[ia];
            float ay = pos[ia + 1];
            float az = pos[ia + 2];
            float au = 0.5f + 0.5f * ax;
            float av = 0.5f + 0.5f * ay;
            vc.addVertex(mat, ax, ay, az).setUv(au, av).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);

            float bx = pos[ib];
            float by = pos[ib + 1];
            float bz = pos[ib + 2];
            float bu = 0.5f + 0.5f * bx;
            float bv = 0.5f + 0.5f * by;
            vc.addVertex(mat, bx, by, bz).setUv(bu, bv).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);

            float cx = pos[ic];
            float cy = pos[ic + 1];
            float cz = pos[ic + 2];
            float cu = 0.5f + 0.5f * cx;
            float cv = 0.5f + 0.5f * cy;
            vc.addVertex(mat, cx, cy, cz).setUv(cu, cv).setColor(r, g, b, a).setLight(LightTexture.FULL_BRIGHT);
        }
    }

    /** Draws cylinder scaled to height with a top hemisphere dome, using given LOD. */
    public static void drawCylinderWithDomeLod(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a, int lod) {
        float yScale = Math.max(0.0001f, height * 0.5f);
        Matrix4f scaled = new Matrix4f(mat).scale(1.0f, yScale, 1.0f);
        drawUnitCylinderWithDomeLod(vc, scaled, r, g, b, a, lod);
    }

    /** Convenience: default LOD (2) cylinder with dome and specified height. */
    public static void drawCylinderWithDome(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a) {
        drawCylinderWithDomeLod(vc, mat, height, r, g, b, a, 2);
    }

    /** Emits only positions (no UV/Color/Light), in object space with identity matrix. */
    public static void drawPositionOnly(VertexConsumer vc) {
        Mesh m = mesh();
        float[] pos = m.positions;
        int[] idx = m.indices;
        Matrix4f I = new Matrix4f().identity();
        for (int i = 0; i < idx.length; i += 3) {
            int ia = idx[i] * 3, ib = idx[i+1] * 3, ic = idx[i+2] * 3;
            vc.addVertex(I, pos[ia],   pos[ia+1],   pos[ia+2]);
            vc.addVertex(I, pos[ib],   pos[ib+1],   pos[ib+2]);
            vc.addVertex(I, pos[ic],   pos[ic+1],   pos[ic+2]);
        }
    }

    // --- Mesh construction ---

    private static Mesh buildUnitCylinder(int segments) {
        segments = Math.max(3, segments);

        // Side strip vertices: for each segment angle we add bottom and top verts (y=-1 and y=+1)
        int vertCount = segments * 2;
        float[] positions = new float[vertCount * 3];

        double twoPi = Math.PI * 2.0;
        for (int i = 0; i < segments; i++) {
            double t = (double) i / (double) segments;
            double ang = t * twoPi;
            float x = (float) Math.cos(ang);
            float z = (float) Math.sin(ang);

            // bottom
            int vb = i * 6;
            positions[vb]   = x;
            positions[vb+1] = -1f;
            positions[vb+2] = z;
            // top
            positions[vb+3] = x;
            positions[vb+4] = 1f;
            positions[vb+5] = z;
        }

        // Indices: two triangles per segment, wrapping around
        int triCount = segments * 2; // two tris per segment
        int[] indices = new int[triCount * 3];
        int k = 0;
        for (int i = 0; i < segments; i++) {
            int iNext = (i + 1) % segments;
            int ib0 = i * 2;       // bottom i
            int it0 = ib0 + 1;     // top i
            int ib1 = iNext * 2;   // bottom next
            int it1 = ib1 + 1;     // top next

            // Winding CCW when viewed from outside
            // quad (ib0, it0, it1, ib1) -> tris (ib0, it0, it1) and (ib0, it1, ib1)
            indices[k++] = ib0;
            indices[k++] = it0;
            indices[k++] = it1;

            indices[k++] = ib0;
            indices[k++] = it1;
            indices[k++] = ib1;
        }

        return new Mesh(positions, indices);
    }

    /**
     * Builds a unit-radius top hemisphere (center at y=+1, so it sits on top of the cylinder rim at y=+1).
     * segments = angular segments around Y; stacks = vertical rows from rim (0) to apex.
     */
    private static Mesh buildUnitHemisphereTop(int segments, int stacks) {
        segments = Math.max(3, segments);
        stacks = Math.max(2, stacks);

        // Rings: 0..(stacks-1) have "segments" verts each; plus 1 apex vertex.
        int ringVerts = segments * stacks;
        int apexIndex = ringVerts; // last vertex
        float[] positions = new float[(ringVerts + 1) * 3];

        double twoPi = Math.PI * 2.0;
        // s from 0..(stacks-1), phi in [0 .. pi/2)
        for (int s = 0; s < stacks; s++) {
            float t = (float) s / (float) stacks;        // 0..(1-1/stacks)
            double phi = t * (Math.PI * 0.5);            // 0..(pi/2)
            float ringR = (float) Math.cos(phi);         // radius in XZ
            float ringY = 1.0f + (float) Math.sin(phi);  // y position (centered at y=+1)

            for (int i = 0; i < segments; i++) {
                double a = (double) i / (double) segments * twoPi;
                float x = ringR * (float) Math.cos(a);
                float z = ringR * (float) Math.sin(a);
                int vi = (s * segments + i) * 3;
                positions[vi] = x;
                positions[vi + 1] = ringY;
                positions[vi + 2] = z;
            }
        }
        // Apex vertex at (0, 2, 0)
        positions[apexIndex * 3] = 0f;
        positions[apexIndex * 3 + 1] = 2f;
        positions[apexIndex * 3 + 2] = 0f;

        // Indices: connect rings and then top fan to apex
        int quads = (stacks - 1) * segments; // between ring s and s+1
        int trisFromQuads = quads * 2;
        int trisFan = segments; // ring (stacks-1) to apex (single outward-facing winding)
        int[] indices = new int[(trisFromQuads + trisFan) * 3];
        int k = 0;
        for (int s = 0; s < stacks - 1; s++) {
            int base0 = s * segments;
            int base1 = (s + 1) * segments;
            for (int i = 0; i < segments; i++) {
                int inext = (i + 1) % segments;
                int v00 = base0 + i;
                int v01 = base0 + inext;
                int v10 = base1 + i;
                int v11 = base1 + inext;
                // two tris: (v00, v10, v11) and (v00, v11, v01) — CCW outward
                indices[k++] = v00;
                indices[k++] = v10;
                indices[k++] = v11;

                indices[k++] = v00;
                indices[k++] = v11;
                indices[k++] = v01;
            }
        }
        // Fan from last ring to apex (outward-facing winding)
        int baseTop = (stacks - 1) * segments;
        for (int i = 0; i < segments; i++) {
            int inext = (i + 1) % segments;
            int v0 = baseTop + i;
            int v1 = baseTop + inext;
            // Winding chosen so normals face OUTWARD (visible from outside)
            indices[k++] = v1;
            indices[k++] = v0;
            indices[k++] = apexIndex;
        }

        return new Mesh(positions, indices);
    }

    /**
     * Builds a unit-radius bottom hemisphere by mirroring the top hemisphere across Y=0
     * and reversing triangle winding to keep outward-facing orientation. Center at y=-1; apex at y=-2.
     */
    private static Mesh buildUnitHemisphereBottom(int segments, int stacks) {
        Mesh top = buildUnitHemisphereTop(segments, stacks);
        float[] posTop = top.positions;
        int[] idxTop = top.indices;
        float[] positions = new float[posTop.length];
        // Mirror Y
        for (int i = 0; i < posTop.length; i += 3) {
            positions[i] = posTop[i];
            positions[i+1] = -posTop[i+1];
            positions[i+2] = posTop[i+2];
        }
        // Reverse winding for each triangle to compensate for mirroring
        int[] indices = new int[idxTop.length];
        for (int i = 0; i < idxTop.length; i += 3) {
            indices[i] = idxTop[i];
            indices[i+1] = idxTop[i+2];
            indices[i+2] = idxTop[i+1];
        }
        return new Mesh(positions, indices);
    }

    /** Draws a unit cylinder with both top and bottom domes (capsule) at the given LOD. */
    public static void drawUnitCylinderWithDomesLod(VertexConsumer vc, Matrix4f mat, float r, float g, float b, float a, int lod) {
        // Sides
        drawUnitCylinderLod(vc, mat, r, g, b, a, lod);
        // Bottom dome first so top draws over if overlapping
        Mesh domeBottom = domeBottomMeshForLod(lod);
        float[] posB = domeBottom.positions;
        int[] idxB = domeBottom.indices;
        for (int i = 0; i < idxB.length; i += 3) {
            int ia = idxB[i] * 3, ib = idxB[i+1] * 3, ic = idxB[i+2] * 3;
            float ax = posB[ia], ay = posB[ia+1], az = posB[ia+2];
            float au = 0.5f + 0.5f * ax, av = 0.5f + 0.5f * ay;
            vc.addVertex(mat, ax, ay, az).setUv(au, av).setColor(r,g,b,a).setLight(LightTexture.FULL_BRIGHT);
            float bx = posB[ib], by = posB[ib+1], bz = posB[ib+2];
            float bu = 0.5f + 0.5f * bx, bv = 0.5f + 0.5f * by;
            vc.addVertex(mat, bx, by, bz).setUv(bu, bv).setColor(r,g,b,a).setLight(LightTexture.FULL_BRIGHT);
            float cx = posB[ic], cy = posB[ic+1], cz = posB[ic+2];
            float cu = 0.5f + 0.5f * cx, cv = 0.5f + 0.5f * cy;
            vc.addVertex(mat, cx, cy, cz).setUv(cu, cv).setColor(r,g,b,a).setLight(LightTexture.FULL_BRIGHT);
        }
        // Top dome
        Mesh domeTop = domeMeshForLod(lod);
        float[] posT = domeTop.positions;
        int[] idxT = domeTop.indices;
        for (int i = 0; i < idxT.length; i += 3) {
            int ia = idxT[i] * 3, ib = idxT[i+1] * 3, ic = idxT[i+2] * 3;
            float ax = posT[ia], ay = posT[ia+1], az = posT[ia+2];
            float au = 0.5f + 0.5f * ax, av = 0.5f + 0.5f * ay;
            vc.addVertex(mat, ax, ay, az).setUv(au, av).setColor(r,g,b,a).setLight(LightTexture.FULL_BRIGHT);
            float bx = posT[ib], by = posT[ib+1], bz = posT[ib+2];
            float bu = 0.5f + 0.5f * bx, bv = 0.5f + 0.5f * by;
            vc.addVertex(mat, bx, by, bz).setUv(bu, bv).setColor(r,g,b,a).setLight(LightTexture.FULL_BRIGHT);
            float cx = posT[ic], cy = posT[ic+1], cz = posT[ic+2];
            float cu = 0.5f + 0.5f * cx, cv = 0.5f + 0.5f * cy;
            vc.addVertex(mat, cx, cy, cz).setUv(cu, cv).setColor(r,g,b,a).setLight(LightTexture.FULL_BRIGHT);
        }
    }

    /** Draws a cylinder scaled to height with top and bottom domes (capsule). */
    public static void drawCylinderWithDomesLod(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a, int lod) {
        float yScale = Math.max(0.0001f, height * 0.5f);
        Matrix4f scaled = new Matrix4f(mat).scale(1.0f, yScale, 1.0f);
        drawUnitCylinderWithDomesLod(vc, scaled, r, g, b, a, lod);
    }

    /** Convenience aliases for capsule drawing. */
    public static void drawCapsuleLod(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a, int lod) {
        drawCylinderWithDomesLod(vc, mat, height, r, g, b, a, lod);
    }
    public static void drawCapsule(VertexConsumer vc, Matrix4f mat, float height, float r, float g, float b, float a) {
        drawCylinderWithDomesLod(vc, mat, height, r, g, b, a, 2);
    }
}