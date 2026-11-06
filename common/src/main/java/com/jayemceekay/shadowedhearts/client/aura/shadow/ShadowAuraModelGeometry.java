package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cube classification, deterministic model-space samples, and geometry-based anchor budgets.
 * World and GUI captures share these templates; posed anchors and particle state stay with their owners.
 */
final class ShadowAuraModelGeometry {
    private static final int MIN_BONE_ANCHORS = 160;
    static final int MAX_BONE_ANCHORS = 900;
    private static final float BONE_ANCHOR_VOLUME_STEP = 900.0f;
    private static final float BONE_ANCHOR_SURFACE_AREA_STEP = 420.0f;
    private static final float MIN_AURA_CUBE_AXIS = 0.20f;
    private static final float MIN_AURA_SOLID_VOLUME = 7.0f;
    private static final float MIN_AURA_FLAT_AREA = 7.0f;
    private static final float MIN_BODY_SHELL_VOLUME = 420.0f;
    private static final float MIN_CHAINABLE_CUBE_AXIS = 3.25f;

    // Preserve weak ownership of model cubes across reloads; world/preview resets do not clear this cache.
    private static final Map<ModelPart.Cube, CubeAuraTemplate> CUBE_TEMPLATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ShadowAuraModelGeometry() {
    }

    static int boneAnchorBudget(ModelGeometryStats geometry) {
        float volumeContribution = geometry.volume / BONE_ANCHOR_VOLUME_STEP;
        float surfaceContribution = geometry.surfaceArea / BONE_ANCHOR_SURFACE_AREA_STEP;
        int scaledBudget = MIN_BONE_ANCHORS + (int) Math.ceil(volumeContribution + surfaceContribution);
        return Mth.clamp(scaledBudget, MIN_BONE_ANCHORS, MAX_BONE_ANCHORS);
    }

    static ModelGeometryStats estimateModelGeometry(Bone bone, int depth) {
        if (bone == null || depth > 24) {
            return ModelGeometryStats.EMPTY;
        }

        float volume = 0.0f;
        float surfaceArea = 0.0f;
        if (bone instanceof ModelPart part && !part.cubes.isEmpty()) {
            for (ModelPart.Cube cube : part.cubes) {
                CubeAuraTemplate template = cubeTemplate(cube);
                if (!template.auraAnchor) {
                    continue;
                }

                volume += template.volume;
                surfaceArea += template.surfaceArea;
            }
        }

        Map<String, Bone> children = bone.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Bone child : children.values()) {
                ModelGeometryStats childStats = estimateModelGeometry(child, depth + 1);
                volume += childStats.volume;
                surfaceArea += childStats.surfaceArea;
            }
        }

        return new ModelGeometryStats(volume, surfaceArea);
    }

    static CubeAuraTemplate cubeTemplate(ModelPart.Cube cube) {
        CubeAuraTemplate cached = CUBE_TEMPLATES.get(cube);
        if (cached != null) {
            return cached;
        }

        CubeAuraTemplate created = createCubeTemplate(cube);
        CUBE_TEMPLATES.put(cube, created);
        return created;
    }

    private static CubeAuraTemplate createCubeTemplate(ModelPart.Cube cube) {
        float rawSx = rawCubeAxis(cube.maxX, cube.minX);
        float rawSy = rawCubeAxis(cube.maxY, cube.minY);
        float rawSz = rawCubeAxis(cube.maxZ, cube.minZ);
        float sx = Math.max(0.001f, rawSx);
        float sy = Math.max(0.001f, rawSy);
        float sz = Math.max(0.001f, rawSz);
        float longest = Math.max(sx, Math.max(sy, sz));
        float volume = rawSx * rawSy * rawSz;
        float surfaceArea = 2.0f * (rawSx * rawSy + rawSx * rawSz + rawSy * rawSz);
        boolean auraAnchor = isAuraAnchorCubeRaw(rawSx, rawSy, rawSz);
        boolean chainable = isChainableCubeRaw(rawSx, rawSy, rawSz);
        Vector3f center = new Vector3f(
                (cube.minX + cube.maxX) * 0.5f,
                (cube.minY + cube.maxY) * 0.5f,
                (cube.minZ + cube.maxZ) * 0.5f
        );

        if (!auraAnchor) {
            return new CubeAuraTemplate(false, chainable, AnchorRole.BODY, volume, surfaceArea,
                    0.0f, false, center, null, CubeAuraTemplate.EMPTY_POINTS, CubeAuraTemplate.EMPTY_POINTS);
        }

        AnchorRole role = cubeAnchorRole(sx, sy, sz);

        int maxSamples = role == AnchorRole.SURFACE ? 26 : 18;
        boolean shellCube = role == AnchorRole.BODY && volume >= MIN_BODY_SHELL_VOLUME;
        int sampleCount = Mth.clamp((int) Math.ceil(volume / 1850.0f), 1, maxSamples);
        sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(longest / 12.0f), 1, 8));
        sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(surfaceArea / 560.0f), 1, maxSamples));
        sampleCount = Math.max(sampleCount, compactCubeSampleFloor(role, volume, surfaceArea, longest));
        if (role == AnchorRole.SURFACE) {
            sampleCount = Math.max(sampleCount, Mth.clamp((int) Math.ceil(longest / 6.0f), 3, maxSamples));
        } else if (shellCube) {
            sampleCount = Math.min(sampleCount, 6);
        }

        SurfaceAxes surfaceAxes = role == AnchorRole.SURFACE ? surfaceAxes(cube) : null;
        Vector3f[] samples = new Vector3f[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = role == AnchorRole.SURFACE
                    ? surfaceSample(cube, surfaceAxes, i, sampleCount)
                    : cubeVolumeSample(cube, i, sampleCount);
        }

        Vector3f[] shellPoints = CubeAuraTemplate.EMPTY_POINTS;
        if (shellCube) {
            int pointCount = cubeShellPointCount(cube);
            shellPoints = new Vector3f[pointCount];
            for (int i = 0; i < pointCount; i++) {
                shellPoints[i] = cubeShellPoint(cube, i);
            }
        }

        float sizeWeight = Mth.clamp(0.78f + longest / 68.0f + (role == AnchorRole.SURFACE ? 0.10f : 0.0f), 0.78f, 1.22f);
        return new CubeAuraTemplate(true, chainable, role, volume, surfaceArea, sizeWeight,
                shellCube, center, surfaceAxes, samples, shellPoints);
    }

    private static int compactCubeSampleFloor(AnchorRole role, float volume, float surfaceArea, float longest) {
        return switch (role) {
            case BODY -> volume < MIN_BODY_SHELL_VOLUME
                    ? Mth.clamp((int) Math.ceil(Math.max(surfaceArea / 52.0f, longest / 3.8f)), 2, 5)
                    : 1;
            case APPENDAGE -> longest < 8.0f ? 2 : 1;
            case SURFACE -> 3;
            case JOINT, TIP -> 1;
        };
    }

    private static boolean isAuraAnchorCubeRaw(float sx, float sy, float sz) {
        int solidAxes = 0;
        if (sx >= MIN_AURA_CUBE_AXIS) solidAxes++;
        if (sy >= MIN_AURA_CUBE_AXIS) solidAxes++;
        if (sz >= MIN_AURA_CUBE_AXIS) solidAxes++;

        if (solidAxes >= 3) {
            return sx * sy * sz >= MIN_AURA_SOLID_VOLUME;
        }
        if (solidAxes == 2) {
            float[] axes = sortedCubeAxes(sx, sy, sz);
            return axes[1] * axes[2] >= MIN_AURA_FLAT_AREA;
        }
        return false;
    }

    private static boolean isChainableCubeRaw(float sx, float sy, float sz) {
        return sx >= MIN_CHAINABLE_CUBE_AXIS
                && sy >= MIN_CHAINABLE_CUBE_AXIS
                && sz >= MIN_CHAINABLE_CUBE_AXIS;
    }

    static float rawCubeAxis(float max, float min) {
        return Math.max(0.0f, Math.abs(max - min));
    }

    private static float[] sortedCubeAxes(float sx, float sy, float sz) {
        float a = Math.min(sx, Math.min(sy, sz));
        float c = Math.max(sx, Math.max(sy, sz));
        float b = sx + sy + sz - a - c;
        return new float[] {a, b, c};
    }

    static AnchorRole cubeAnchorRole(float sx, float sy, float sz) {
        float longest = Math.max(sx, Math.max(sy, sz));
        float shortest = Math.max(0.001f, Math.min(sx, Math.min(sy, sz)));
        float middle = sx + sy + sz - longest - shortest;
        float longRatio = longest / shortest;
        float panelRatio = middle / shortest;
        if (longRatio >= 3.2f && panelRatio >= 2.15f) {
            return AnchorRole.SURFACE;
        }
        return longRatio >= 3.2f ? AnchorRole.APPENDAGE : AnchorRole.BODY;
    }

    private static int cubeShellPointCount(ModelPart.Cube cube) {
        float volume = rawCubeAxis(cube.maxX, cube.minX) * rawCubeAxis(cube.maxY, cube.minY) * rawCubeAxis(cube.maxZ, cube.minZ);
        return volume >= 900.0f ? 14 : 8;
    }

    private static Vector3f cubeShellPoint(ModelPart.Cube cube, int index) {
        if (index < 8) {
            return new Vector3f(
                    (index & 1) == 0 ? cube.minX : cube.maxX,
                    (index & 2) == 0 ? cube.minY : cube.maxY,
                    (index & 4) == 0 ? cube.minZ : cube.maxZ
            );
        }

        float cx = (cube.minX + cube.maxX) * 0.5f;
        float cy = (cube.minY + cube.maxY) * 0.5f;
        float cz = (cube.minZ + cube.maxZ) * 0.5f;
        return switch (index - 8) {
            case 0 -> new Vector3f(cube.minX, cy, cz);
            case 1 -> new Vector3f(cube.maxX, cy, cz);
            case 2 -> new Vector3f(cx, cube.minY, cz);
            case 3 -> new Vector3f(cx, cube.maxY, cz);
            case 4 -> new Vector3f(cx, cy, cube.minZ);
            default -> new Vector3f(cx, cy, cube.maxZ);
        };
    }

    private static SurfaceAxes surfaceAxes(ModelPart.Cube cube) {
        float sx = rawCubeAxis(cube.maxX, cube.minX);
        float sy = rawCubeAxis(cube.maxY, cube.minY);
        float sz = rawCubeAxis(cube.maxZ, cube.minZ);
        if (sx <= sy && sx <= sz) {
            return new SurfaceAxes(0, 1, 2);
        }
        if (sy <= sx && sy <= sz) {
            return new SurfaceAxes(1, 0, 2);
        }
        return new SurfaceAxes(2, 0, 1);
    }

    private static Vector3f surfaceSample(ModelPart.Cube cube, SurfaceAxes axes, int index, int sampleCount) {
        float[] min = {cube.minX, cube.minY, cube.minZ};
        float[] max = {cube.maxX, cube.maxY, cube.maxZ};
        float[] pos = {
                (cube.minX + cube.maxX) * 0.5f,
                (cube.minY + cube.maxY) * 0.5f,
                (cube.minZ + cube.maxZ) * 0.5f
        };

        float u;
        float v;
        if (index < 4) {
            u = (index & 1) == 0 ? 0.0f : 1.0f;
            v = (index & 2) == 0 ? 0.0f : 1.0f;
        } else if (index < 8) {
            int edge = index - 4;
            u = switch (edge) {
                case 0 -> 0.5f;
                case 1 -> 0.5f;
                case 2 -> 0.0f;
                default -> 1.0f;
            };
            v = switch (edge) {
                case 0 -> 0.0f;
                case 1 -> 1.0f;
                case 2 -> 0.5f;
                default -> 0.5f;
            };
        } else {
            int sample = index - 7;
            u = halton(sample, 2);
            v = halton(sample, 3);
        }

        pos[axes.uAxis] = Mth.lerp(u, min[axes.uAxis], max[axes.uAxis]);
        pos[axes.vAxis] = Mth.lerp(v, min[axes.vAxis], max[axes.vAxis]);
        pos[axes.normalAxis] = (min[axes.normalAxis] + max[axes.normalAxis]) * 0.5f;
        return new Vector3f(pos[0], pos[1], pos[2]);
    }

    private static Vector3f cubeVolumeSample(ModelPart.Cube cube, int index, int sampleCount) {
        float x = Mth.lerp(halton(index + 1, 2), cube.minX, cube.maxX);
        float y = Mth.lerp(halton(index + 1, 3), cube.minY, cube.maxY);
        float z = Mth.lerp(halton(index + 1, 5), cube.minZ, cube.maxZ);

        if (sampleCount > 3 && (index % 4) == 0) {
            switch ((index / 4) % 6) {
                case 0 -> x = cube.minX;
                case 1 -> x = cube.maxX;
                case 2 -> y = cube.minY;
                case 3 -> y = cube.maxY;
                case 4 -> z = cube.minZ;
                default -> z = cube.maxZ;
            }
        }

        return new Vector3f(x, y, z);
    }

    static float halton(int index, int base) {
        float f = 1.0f;
        float r = 0.0f;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }

    enum AnchorRole {
        BODY(1.00f, 1.00f, 0.10f, 0.13f, 1.00f),
        JOINT(0.96f, 1.00f, 0.20f, 0.12f, 1.00f),
        APPENDAGE(0.94f, 1.10f, 0.34f, 0.12f, 0.82f),
        SURFACE(0.92f, 0.98f, 0.24f, 0.08f, 0.34f),
        TIP(0.86f, 1.16f, 0.42f, 0.09f, 0.72f);

        final float sizeWeight;
        final float spawnWeight;
        final float wispChance;
        final float hazeChance;
        final float sparkWeight;

        AnchorRole(float sizeWeight, float spawnWeight, float wispChance, float hazeChance, float sparkWeight) {
            this.sizeWeight = sizeWeight;
            this.spawnWeight = spawnWeight;
            this.wispChance = wispChance;
            this.hazeChance = hazeChance;
            this.sparkWeight = sparkWeight;
        }
    }

    record SurfaceAxes(int normalAxis, int uAxis, int vAxis) {}

    record CubeAuraTemplate(boolean auraAnchor,
                                    boolean chainable,
                                    AnchorRole role,
                                    float volume,
                                    float surfaceArea,
                                    float sizeWeight,
                                    boolean shellCube,
                                    Vector3f center,
                                    SurfaceAxes surfaceAxes,
                                    Vector3f[] samples,
                                    Vector3f[] shellPoints) {
        static final Vector3f[] EMPTY_POINTS = new Vector3f[0];
    }

    record ModelGeometryStats(float volume, float surfaceArea) {
        static final ModelGeometryStats EMPTY = new ModelGeometryStats(0.0f, 0.0f);
    }
}
