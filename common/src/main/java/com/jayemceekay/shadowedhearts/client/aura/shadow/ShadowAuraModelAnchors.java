package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.classic.ClassicAuraMorphology;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.classic.ClassicAuraWisp;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.AnchorRole;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.CubeAuraTemplate;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.SurfaceAxes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.cubeTemplate;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.halton;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.rawCubeAxis;

/**
 * Posed model sampling, stable anchor identities, and bounded anchor selection.
 * Capture owners supply poses, budgets, and prior anchors; this class holds no capture or particle state.
 */
final class ShadowAuraModelAnchors {
    private static final float MODEL_ANCHOR_RECENTER_BLEND = 0.62f;
    private static final float MODEL_ANCHOR_RECENTER_MAX_BLOCKS = 0.85f;
    private static final float SMALL_MODEL_COVERAGE_SIZE = 1.18f;
    private static final float BONE_CHAIN_SAMPLE_SPACING = 0.22f;
    private static final float BONE_CHAIN_MIN_LENGTH = 0.16f;
    private static final float BONE_CHAIN_MAX_LENGTH = 1.35f;
    private static final int BONE_CHAIN_MAX_SAMPLES = 10;
    private static final int BONE_CHAIN_MAX_RING_POINTS = 4;

    private ShadowAuraModelAnchors() {
    }

    static List<BoneAnchor> fallbackBoneAnchors(float width, float height) {
        int count = 240;
        float safeWidth = Math.max(0.22f, width);
        float safeHeight = Math.max(0.35f, height);
        Vec3 center = new Vec3(0.0, safeHeight * 0.50, 0.0);
        List<BoneAnchor> anchors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float u = halton(i + 1, 2);
            float v = halton(i + 1, 3);
            float shell = 0.42f + 0.58f * halton(i + 1, 5);
            float vertical = v * 2.0f - 1.0f;
            float ring = (float) Math.sqrt(Math.max(0.0f, 1.0f - vertical * vertical));
            float angle = u * Mth.TWO_PI;
            Vec3 radial = new Vec3(
                    Math.cos(angle) * ring * safeWidth * 0.50f,
                    vertical * safeHeight * 0.46f,
                    Math.sin(angle) * ring * safeWidth * 0.38f
            );
            Vec3 position = center.add(radial.scale(shell));
            Vec3 normal = radial.lengthSqr() <= 0.000001
                    ? new Vec3(0.0, 1.0, 0.0)
                    : radial.normalize();
            AnchorRole role = (i % 7) == 0 ? AnchorRole.SURFACE : AnchorRole.BODY;
            anchors.add(new BoneAnchor(
                    position,
                    2 + i % 4,
                    0.92f + 0.12f * halton(i + 1, 7),
                    role,
                    AnchorSource.CUBE,
                    normal,
                    new AnchorKey("fallback", "fallback", i),
                    ClassicAuraMorphology.localScale(safeWidth, safeHeight, safeWidth * 0.76f, false),
                    normal
            ));
        }
        return List.copyOf(anchors);
    }

    static float smallModelCoverageBoost(float modelSize) {
        return 1.0f - smoothstep(0.66f, SMALL_MODEL_COVERAGE_SIZE, modelSize);
    }

    static RenderedPartSample renderedPartSample(ModelPart part, PoseStack stack, Vec3 poseToWorldOffset) {
        boolean auraRenderable = false;
        boolean chainable = false;
        for (ModelPart.Cube cube : part.cubes) {
            CubeAuraTemplate template = cubeTemplate(cube);
            auraRenderable |= template.auraAnchor();
            chainable |= template.chainable();
        }

        Vec3 originWorld = poseOriginWorld(stack, poseToWorldOffset);
        Vec3 geometryWorld = auraRenderable
                ? partGeometryCenterWorld(part, stack, poseToWorldOffset, chainable)
                : originWorld;
        return new RenderedPartSample(geometryWorld, chainable, hasChainableDescendant(part, 0));
    }

    private static Vec3 poseOriginWorld(PoseStack stack, Vec3 poseToWorldOffset) {
        Vector3f origin = stack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        return new Vec3(origin.x + poseToWorldOffset.x, origin.y + poseToWorldOffset.y, origin.z + poseToWorldOffset.z);
    }

    private static boolean isChainablePart(ModelPart part) {
        for (ModelPart.Cube cube : part.cubes) {
            if (cubeTemplate(cube).chainable()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChainableDescendant(ModelPart part, int depth) {
        if (part == null || depth > 24 || part.children.isEmpty()) {
            return false;
        }

        for (ModelPart child : part.children.values()) {
            if (isChainablePart(child) || hasChainableDescendant(child, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 partGeometryCenterWorld(ModelPart part, PoseStack stack, Vec3 poseToWorldOffset, boolean chainableOnly) {
        Matrix4f pose = stack.last().pose();
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double totalWeight = 0.0;

        for (ModelPart.Cube cube : part.cubes) {
            CubeAuraTemplate template = cubeTemplate(cube);
            if (chainableOnly ? !template.chainable() : !template.auraAnchor()) {
                continue;
            }

            double weight = Math.max(template.volume(), template.surfaceArea() * 0.18);
            Vector3f localCenter = template.center();
            Vector3f center = pose.transformPosition(
                    localCenter.x / 16.0f,
                    localCenter.y / 16.0f,
                    localCenter.z / 16.0f,
                    new Vector3f()
            );
            x += (center.x + poseToWorldOffset.x) * weight;
            y += (center.y + poseToWorldOffset.y) * weight;
            z += (center.z + poseToWorldOffset.z) * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            Vector3f origin = pose.transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
            return new Vec3(origin.x + poseToWorldOffset.x, origin.y + poseToWorldOffset.y, origin.z + poseToWorldOffset.z);
        }

        return new Vec3(x / totalWeight, y / totalWeight, z / totalWeight);
    }

    static void collectCubeAnchors(ModelPart part,
                                           PoseStack stack,
                                           Vec3 poseToWorldOffset,
                                           List<BoneAnchor> anchors,
                                           int depth,
                                           int anchorBudget,
                                           int bodyAnchorBudget,
                                           int[] bodyAnchorCount,
                                           boolean describeClassic) {
        Matrix4f pose = stack.last().pose();
        for (ModelPart.Cube cube : part.cubes) {
            if (anchors.size() >= anchorBudget) {
                return;
            }
            CubeAuraTemplate template = cubeTemplate(cube);
            if (!template.auraAnchor()) {
                continue;
            }

            AnchorRole role = template.role();
            if (role == AnchorRole.BODY && bodyAnchorCount[0] >= bodyAnchorBudget) {
                continue;
            }

            int firstCubeAnchor = anchors.size();
            if (role == AnchorRole.SURFACE) {
                collectSurfaceAnchors(template, stack, poseToWorldOffset, anchors, depth, anchorBudget);
                if (describeClassic) describeCubeAnchors(part, cube, pose, poseToWorldOffset, anchors, firstCubeAnchor);
                continue;
            }

            if (template.shellCube()) {
                collectCubeShellAnchors(template, pose, poseToWorldOffset, anchors, depth, anchorBudget, bodyAnchorBudget, bodyAnchorCount);
            }

            for (Vector3f local : template.samples()) {
                if (anchors.size() >= anchorBudget) {
                    break;
                }
                if (role == AnchorRole.BODY && bodyAnchorCount[0] >= bodyAnchorBudget) {
                    break;
                }

                Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
                anchors.add(new BoneAnchor(new Vec3(p.x + poseToWorldOffset.x, p.y + poseToWorldOffset.y, p.z + poseToWorldOffset.z), depth, template.sizeWeight(), role, AnchorSource.CUBE, Vec3.ZERO));
                if (role == AnchorRole.BODY) {
                    bodyAnchorCount[0]++;
                }
            }
            if (describeClassic) describeCubeAnchors(part, cube, pose, poseToWorldOffset, anchors, firstCubeAnchor);
        }
    }

    private static void describeCubeAnchors(ModelPart part, ModelPart.Cube cube, Matrix4f pose,
                                            Vec3 offset, List<BoneAnchor> anchors, int first) {
        float x = pose.transformDirection(rawCubeAxis(cube.maxX, cube.minX) / 16.0f, 0, 0, new Vector3f()).length();
        float y = pose.transformDirection(0, rawCubeAxis(cube.maxY, cube.minY) / 16.0f, 0, new Vector3f()).length();
        float z = pose.transformDirection(0, 0, rawCubeAxis(cube.maxZ, cube.minZ) / 16.0f, new Vector3f()).length();
        Vector3f center = pose.transformPosition((cube.minX + cube.maxX) / 32.0f,
                (cube.minY + cube.maxY) / 32.0f, (cube.minZ + cube.maxZ) / 32.0f, new Vector3f());
        Vec3 origin = new Vec3(center.x, center.y, center.z).add(offset);
        for (int i = first; i < anchors.size(); i++) {
            BoneAnchor anchor = anchors.get(i);
            Vec3 normal = anchor.normal.lengthSqr() > 0.0001 ? anchor.normal : anchor.pos.subtract(origin).normalize();
            anchors.set(i, new BoneAnchor(anchor.pos, anchor.depth, anchor.sizeWeight, anchor.role,
                    anchor.source, anchor.normal, new AnchorKey(part, cube, i - first),
                    ClassicAuraMorphology.localScale(x, y, z, anchor.role == AnchorRole.SURFACE), normal));
        }
    }

    private static void collectSurfaceAnchors(CubeAuraTemplate template,
                                              PoseStack stack,
                                              Vec3 poseToWorldOffset,
                                              List<BoneAnchor> anchors,
                                              int depth,
                                              int anchorBudget) {
        Matrix4f pose = stack.last().pose();
        SurfaceAxes axes = template.surfaceAxes();
        Vector3f normalA = transformedSurfaceNormal(stack, axes.normalAxis(), 1.0f);
        Vector3f normalB = transformedSurfaceNormal(stack, axes.normalAxis(), -1.0f);

        for (int i = 0; i < template.samples().length && anchors.size() < anchorBudget; i++) {
            Vector3f local = template.samples()[i];
            Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
            Vector3f n = (i & 1) == 0 ? normalA : normalB;
            anchors.add(new BoneAnchor(
                    new Vec3(p.x + poseToWorldOffset.x, p.y + poseToWorldOffset.y, p.z + poseToWorldOffset.z),
                    depth,
                    template.sizeWeight(),
                    AnchorRole.SURFACE,
                    AnchorSource.CUBE,
                    new Vec3(n.x, n.y, n.z)
            ));
        }
    }

    private static void collectCubeShellAnchors(CubeAuraTemplate template,
                                                Matrix4f pose,
                                                Vec3 poseToWorldOffset,
                                                List<BoneAnchor> anchors,
                                                int depth,
                                                int anchorBudget,
                                                int bodyAnchorBudget,
                                                int[] bodyAnchorCount) {
        for (Vector3f local : template.shellPoints()) {
            if (anchors.size() >= anchorBudget || bodyAnchorCount[0] >= bodyAnchorBudget) {
                break;
            }
            Vector3f p = pose.transformPosition(local.x / 16.0f, local.y / 16.0f, local.z / 16.0f, new Vector3f());
            anchors.add(new BoneAnchor(
                    new Vec3(p.x + poseToWorldOffset.x, p.y + poseToWorldOffset.y, p.z + poseToWorldOffset.z),
                    depth,
                    template.sizeWeight() * 1.08f,
                    AnchorRole.BODY,
                    AnchorSource.CUBE,
                    Vec3.ZERO
            ));
            bodyAnchorCount[0]++;
        }
    }

    private static Vector3f transformedSurfaceNormal(PoseStack stack, int axis, float sign) {
        Vector3f normal = switch (axis) {
            case 0 -> new Vector3f(sign, 0.0f, 0.0f);
            case 1 -> new Vector3f(0.0f, sign, 0.0f);
            default -> new Vector3f(0.0f, 0.0f, sign);
        };
        stack.last().normal().transform(normal);
        if (normal.lengthSquared() < 0.0001f) {
            return new Vector3f(0.0f, sign, 0.0f);
        }
        return normal.normalize();
    }

    static void collectRenderedBoneChainAnchors(ModelPart childPart,
                                                        Map<Object, BoneAnchor> previousAnchors,
                                                        Vec3 parentWorld,
                                                        Vec3 childWorld,
                                                        boolean tip,
                                                        List<BoneAnchor> anchors,
                                                        int depth,
                                                        int anchorBudget) {
        if (anchors.size() >= anchorBudget) {
            return;
        }

        Vec3 segment = childWorld.subtract(parentWorld);
        double length = segment.length();
        if (length < BONE_CHAIN_MIN_LENGTH || length > BONE_CHAIN_MAX_LENGTH) {
            return;
        }

        Vec3 dir = segment.scale(1.0 / length);
        AnchorRole role = tip ? AnchorRole.TIP : AnchorRole.APPENDAGE;
        int sampleCount = Mth.clamp((int) Math.ceil(length / BONE_CHAIN_SAMPLE_SPACING) + (tip ? 0 : 1), 1, BONE_CHAIN_MAX_SAMPLES);
        int ringPoints = boneChainRingPoints(length, tip);
        float sizeWeight = Mth.clamp(0.82f + (float) length * 0.24f, 0.88f, tip ? 1.12f : 1.28f);
        float tubeRadius = Mth.clamp((float) length * 0.13f, 0.055f, tip ? 0.14f : 0.28f);

        for (int i = 0; i < sampleCount && anchors.size() < anchorBudget; i++) {
            float t = sampleCount == 1 ? 0.5f : (i + 0.5f) / sampleCount;
            Vec3 center = parentWorld.lerp(childWorld, t);
            for (int ring = 0; ring < ringPoints && anchors.size() < anchorBudget; ring++) {
                Vec3 pos = center.add(radialTubeOffset(dir, i, ring, ringPoints, tubeRadius));
                // Include normalized sample position and ring count in the identity: when
                // adaptive chain subdivision changes, old roots release instead of jumping.
                if (childPart == null) {
                    anchors.add(new BoneAnchor(pos, depth, sizeWeight, role, AnchorSource.CHAIN, Vec3.ZERO));
                } else {
                    int sampleKey = Float.floatToIntBits(t) * 31 + ring * 11 + ringPoints;
                    AnchorKey key = new AnchorKey(childPart, "chain", sampleKey);
                    BoneAnchor previous = previousAnchors.get(key);
                    if (previous != null) {
                        Vec3 radial = ClassicAuraWisp.transportTangent(dir, previous.flowNormal);
                        pos = center.add(radial.scale(tubeRadius * (ringPoints == 1 ? 0.58f : 0.88f)));
                    }
                    anchors.add(new BoneAnchor(pos, depth, sizeWeight, role, AnchorSource.CHAIN, Vec3.ZERO,
                            key, tubeRadius * 2.4f,
                            pos.subtract(center).normalize()));
                }
            }
        }
    }

    private static int boneChainRingPoints(double length, boolean tip) {
        if (tip) {
            return length >= 0.34 ? 2 : 1;
        }
        if (length >= 0.46) {
            return BONE_CHAIN_MAX_RING_POINTS;
        }
        return length >= 0.28 ? 3 : 2;
    }

    private static Vec3 radialTubeOffset(Vec3 dir, int sampleIndex, int ringIndex, int ringPoints, float radius) {
        Vec3 basisA = Math.abs(dir.y) < 0.92 ? dir.cross(new Vec3(0.0, 1.0, 0.0)) : dir.cross(new Vec3(1.0, 0.0, 0.0));
        if (basisA.lengthSqr() < 0.0001) {
            basisA = new Vec3(1.0, 0.0, 0.0);
        } else {
            basisA = basisA.normalize();
        }
        Vec3 basisB = dir.cross(basisA);
        if (basisB.lengthSqr() < 0.0001) {
            basisB = new Vec3(0.0, 0.0, 1.0);
        } else {
            basisB = basisB.normalize();
        }

        float twist = halton(sampleIndex + 1, 2) * 0.37f;
        float angle = ((ringIndex / (float) ringPoints) + twist) * Mth.TWO_PI;
        float r = radius * (ringPoints == 1 ? 0.58f : 0.88f);
        return basisA.scale(Math.cos(angle) * r).add(basisB.scale(Math.sin(angle) * r));
    }

    static List<BoneAnchor> downsampleBoneAnchors(List<BoneAnchor> candidates, int anchorBudget) {
        if (candidates.size() <= anchorBudget) {
            return candidates;
        }

        List<BoneAnchor> anchors = new ArrayList<>(anchorBudget);
        boolean[] selected = new boolean[candidates.size()];

        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.JOINT,
                Math.max(16, Math.round(anchorBudget * 0.075f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.TIP,
                Math.max(22, Math.round(anchorBudget * 0.10f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.SURFACE,
                Math.max(38, Math.round(anchorBudget * 0.18f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.APPENDAGE,
                Math.max(64, Math.round(anchorBudget * 0.28f)));
        addEvenlyByRole(candidates, selected, anchors, anchorBudget, AnchorRole.BODY,
                Math.max(112, Math.round(anchorBudget * 0.38f)));

        List<Integer> remaining = new ArrayList<>(candidates.size() - anchors.size());
        for (int i = 0; i < candidates.size(); i++) {
            if (!selected[i]) {
                remaining.add(i);
            }
        }
        addEvenlyFromIndices(candidates, remaining, selected, anchors, anchorBudget, anchorBudget - anchors.size());

        return anchors;
    }

    static Vec3 modelAnchorRecenterOffset(PokemonEntity entity, List<BoneAnchor> anchors, Vec3 sourceWorld) {
        return modelAnchorRecenterOffset(anchors, sourceWorld, Math.max(entity.getBbWidth(), entity.getBbHeight()));
    }

    static Vec3 modelAnchorRecenterOffset(List<BoneAnchor> anchors,
                                                  Vec3 sourceWorld,
                                                  float modelSize) {
        if (anchors.isEmpty()) {
            return Vec3.ZERO;
        }

        AnchorCenter bodyCenter = anchorCenter(anchors, true);
        AnchorCenter center = bodyCenter.count > Math.max(24, anchors.size() / 5)
                ? bodyCenter
                : anchorCenter(anchors, false);
        if (center.count == 0) {
            return Vec3.ZERO;
        }

        double dx = sourceWorld.x - center.x;
        double dz = sourceWorld.z - center.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.035) {
            return Vec3.ZERO;
        }

        float smallModelBoost = smallModelCoverageBoost(modelSize);
        double maxCorrection = Mth.clamp(modelSize * Mth.lerp(smallModelBoost, 0.16f, 0.24f), 0.18f, MODEL_ANCHOR_RECENTER_MAX_BLOCKS);
        double correction = Math.min(distance, maxCorrection) * Mth.clamp(MODEL_ANCHOR_RECENTER_BLEND + smallModelBoost * 0.20f, 0.0f, 0.88f);
        double scale = correction / distance;
        return new Vec3(dx * scale, 0.0, dz * scale);
    }

    private static AnchorCenter anchorCenter(List<BoneAnchor> anchors, boolean bodyOnly) {
        double sumX = 0.0;
        double sumZ = 0.0;
        double weightSum = 0.0;
        int count = 0;
        for (BoneAnchor anchor : anchors) {
            if (bodyOnly && anchor.role != AnchorRole.BODY && anchor.role != AnchorRole.JOINT) {
                continue;
            }

            double weight = anchorCenterWeight(anchor);
            sumX += anchor.pos.x * weight;
            sumZ += anchor.pos.z * weight;
            weightSum += weight;
            count++;
        }

        if (count == 0 || weightSum <= 0.000001) {
            return AnchorCenter.EMPTY;
        }
        return new AnchorCenter(sumX / weightSum, sumZ / weightSum, count);
    }

    private static double anchorCenterWeight(BoneAnchor anchor) {
        double roleWeight = switch (anchor.role) {
            case BODY -> 1.0;
            case JOINT -> 0.82;
            case APPENDAGE -> 0.42;
            case SURFACE -> 0.28;
            case TIP -> 0.20;
        };
        double sourceWeight = anchor.source == AnchorSource.CHAIN ? 1.12 : 1.0;
        return Math.max(0.05, anchor.sizeWeight * roleWeight * sourceWeight);
    }

    static List<BoneAnchor> shiftedBoneAnchors(List<BoneAnchor> anchors, Vec3 offset) {
        List<BoneAnchor> shifted = new ArrayList<>(anchors.size());
        for (BoneAnchor anchor : anchors) {
            shifted.add(new BoneAnchor(
                    anchor.pos.add(offset),
                    anchor.depth,
                    anchor.sizeWeight,
                    anchor.role,
                    anchor.source,
                    anchor.normal,
                    anchor.key,
                    anchor.localScale,
                    anchor.flowNormal
            ));
        }
        return shifted;
    }

    private static void addEvenlyByRole(List<BoneAnchor> candidates,
                                        boolean[] selected,
                                        List<BoneAnchor> anchors,
                                        int anchorBudget,
                                        AnchorRole role,
                                        int requestedCount) {
        List<Integer> roleIndices = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).role == role) {
                roleIndices.add(i);
            }
        }
        addEvenlyFromIndices(candidates, roleIndices, selected, anchors, anchorBudget, requestedCount);
    }

    private static void addEvenlyFromIndices(List<BoneAnchor> candidates,
                                             List<Integer> indices,
                                             boolean[] selected,
                                             List<BoneAnchor> anchors,
                                             int anchorBudget,
                                             int requestedCount) {
        int count = Math.min(requestedCount, Math.min(indices.size(), anchorBudget - anchors.size()));
        if (count <= 0) {
            return;
        }

        for (int pick = 0; pick < count && anchors.size() < anchorBudget; pick++) {
            int indexPos = Math.min(indices.size() - 1, (int) Math.floor((pick + 0.5f) * indices.size() / count));
            int candidateIndex = indices.get(indexPos);
            if (selected[candidateIndex]) {
                continue;
            }

            selected[candidateIndex] = true;
            anchors.add(candidates.get(candidateIndex));
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    record BoneAnchor(Vec3 pos, int depth, float sizeWeight, AnchorRole role, AnchorSource source,
                              Vec3 normal, Object key, float localScale, Vec3 flowNormal) {
        BoneAnchor(Vec3 pos, int depth, float sizeWeight, AnchorRole role, AnchorSource source, Vec3 normal) {
            this(pos, depth, sizeWeight, role, source, normal, null, 0.20f, normal);
        }
    }

    enum AnchorSource {
        CUBE,
        CHAIN,
        JOINT
    }

    record AnchorKey(Object part, Object geometry, int sample) {}

    record RenderedPartSample(Vec3 geometryWorld, boolean chainable, boolean hasChainableDescendant) {}

    private record AnchorCenter(double x, double z, int count) {
        static final AnchorCenter EMPTY = new AnchorCenter(0.0, 0.0, 0);
    }
}
