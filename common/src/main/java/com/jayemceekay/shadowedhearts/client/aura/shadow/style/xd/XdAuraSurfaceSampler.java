package com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Samples the exposed rendered cube faces, independently of regional allocation bounds. */
public final class XdAuraSurfaceSampler {
    static final int MAX_ATTEMPTS = 12;
    private static final double EPSILON = 1.0e-6;
    private static final Map<ModelPart.Cube, List<Face>> LOCAL_FACES = new WeakHashMap<>();

    private XdAuraSurfaceSampler() {}

    public record Sample(Vec3 position, Vec3 normal) {}

    public record Face(Vec3 origin, Vec3 u, Vec3 v, Vec3 normal, double area) {
        Vec3 point(double a, double b) { return origin.add(u.scale(a)).add(v.scale(b)); }

        /** Exact quad distance, including a sheared pose's nonorthogonal edge basis. */
        double distanceTo(Vec3 point) {
            Vec3 delta = point.subtract(origin);
            double uu = u.dot(u), uv = u.dot(v), vv = v.dot(v);
            double du = delta.dot(u), dv = delta.dot(v), determinant = uu * vv - uv * uv;
            if (determinant > 1.0e-20) {
                double a = (du * vv - dv * uv) / determinant;
                double b = (dv * uu - du * uv) / determinant;
                if (a >= 0 && a <= 1 && b >= 0 && b <= 1)
                    return Math.abs(delta.dot(normal));
            }
            return Math.min(Math.min(segmentDistance(point, origin, u), segmentDistance(point, origin, v)),
                    Math.min(segmentDistance(point, origin.add(v), u), segmentDistance(point, origin.add(u), v)));
        }
    }

    public record Cube(Object part, Object geometry, List<Face> faces, boolean emitter, boolean solid, double area) {
        double distanceToSurface(Vec3 point) {
            double distance = Double.POSITIVE_INFINITY;
            for (Face face : faces) distance = Math.min(distance, face.distanceTo(point));
            return distance;
        }

        public boolean contains(Vec3 point) {
            if (!solid) return false;
            for (Face face : faces)
                if (point.subtract(face.origin).dot(face.normal) >= -EPSILON) return false;
            return true;
        }
    }

    /** Cube.compile preserves inflated geometry and omitted faces without exposing renderer internals. */
    public static List<Cube> capture(ModelPart part, Matrix4f pose, Vec3 offset,
                              Predicate<ModelPart.Cube> eligible) {
        if (!part.visible || part.skipDraw || !pose.isFinite()) return List.of();
        Matrix3f normalPose = new Matrix3f(pose);
        if (Math.abs(normalPose.determinant()) <= 1.0e-12f) return List.of();
        normalPose.invert().transpose();
        List<Cube> result = new ArrayList<>();
        for (ModelPart.Cube cube : part.cubes) {
            List<Face> local = LOCAL_FACES.computeIfAbsent(cube, XdAuraSurfaceSampler::localFaces);
            List<Face> faces = new ArrayList<>(local.size());
            double area = 0;
            for (Face face : local) {
                Vector3f point = pose.transformPosition((float) face.origin.x, (float) face.origin.y,
                        (float) face.origin.z, new Vector3f());
                Vec3 origin = new Vec3(point.x, point.y, point.z).add(offset);
                Vec3 u = direction(pose, face.u), v = direction(pose, face.v);
                Vector3f normal = normalPose.transform((float) face.normal.x, (float) face.normal.y,
                        (float) face.normal.z, new Vector3f()).normalize();
                double faceArea = u.cross(v).length();
                if (!Float.isFinite(normal.x) || !Double.isFinite(faceArea) || faceArea <= 1.0e-12) continue;
                faces.add(new Face(origin, u, v, new Vec3(normal.x, normal.y, normal.z).normalize(), faceArea));
                area += faceArea;
            }
            if (!faces.isEmpty()) {
                // Open geometry and two-sided planes have no closed opaque interior.
                boolean solid = faces.size() == 6;
                result.add(new Cube(part, cube, List.copyOf(faces), eligible.test(cube), solid, area));
            }
        }
        return result;
    }

    /** Returns null on bounded failure; callers may keep a sparse ambient-volume fallback. */
    public static Sample sample(List<Cube> candidates, List<Cube> blockers, RandomSource random,
                         float localScale, boolean lowerBias) {
        double area = 0;
        for (Cube cube : candidates) if (cube.emitter) area += cube.area;
        if (area <= 1.0e-12) return null;
        double clearance = Math.max(.002, Math.min(.025, localScale * .025));
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double choice = random.nextDouble() * area;
            Cube selected = null;
            Face face = null;
            for (Cube cube : candidates) {
                if (!cube.emitter) continue;
                if (choice >= cube.area) { choice -= cube.area; continue; }
                selected = cube;
                for (Face candidate : cube.faces) {
                    face = candidate;
                    if (choice < candidate.area) break;
                    choice -= candidate.area;
                }
                break;
            }
            if (face == null) continue;
            Vec3 skin = face.point(random.nextDouble(), random.nextDouble());
            Vec3 position = skin.add(face.normal.scale(clearance));
            if (lowerBias && face.normal.y > .25 && attempt < MAX_ATTEMPTS / 2) continue;
            if (exposed(selected, face, skin, position, blockers)) return new Sample(position, face.normal);
        }
        return null;
    }

    private static boolean exposed(Cube selected, Face face, Vec3 skin, Vec3 position, List<Cube> blockers) {
        // Test close to the actual face too: padding must not jump across a thin neighbour.
        Vec3 near = skin.add(face.normal.scale(EPSILON * 4));
        boolean precedesSelected = true;
        for (Cube other : blockers) {
            if (other == selected) { precedesSelected = false; continue; }
            if (other.contains(near) || other.contains(position)) return false;
            // Give coincident outward faces one stable owner. Opposite sides of a
            // zero-thickness plane are distinct and remain available.
            if (precedesSelected) for (Face otherFace : other.faces) {
                if (otherFace.normal.dot(face.normal) > .9999 && otherFace.distanceTo(skin) < EPSILON)
                    return false;
            }
        }
        return true;
    }

    private static List<Face> localFaces(ModelPart.Cube cube) {
        FaceCollector collector = new FaceCollector();
        cube.compile(new PoseStack().last(), collector, 0, 0, -1);
        return List.copyOf(collector.faces);
    }

    private static Vec3 direction(Matrix4f pose, Vec3 direction) {
        Vector3f value = pose.transformDirection((float) direction.x, (float) direction.y,
                (float) direction.z, new Vector3f());
        return new Vec3(value.x, value.y, value.z);
    }

    private static double segmentDistance(Vec3 point, Vec3 origin, Vec3 edge) {
        double length = edge.lengthSqr();
        double along = length > 0 ? Math.max(0, Math.min(1, point.subtract(origin).dot(edge) / length)) : 0;
        return point.distanceTo(origin.add(edge.scale(along)));
    }

    private static final class FaceCollector implements VertexConsumer {
        final List<Face> faces = new ArrayList<>(6);
        final Vec3[] vertices = new Vec3[4];
        int count;

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            vertices[count++ % 4] = new Vec3(x, y, z);
            return this;
        }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            if (count % 4 == 0) {
                Vec3 u = vertices[1].subtract(vertices[0]), v = vertices[3].subtract(vertices[0]);
                double area = u.cross(v).length();
                if (area > 1.0e-12) faces.add(new Face(vertices[0], u, v, new Vec3(x, y, z).normalize(), area));
            }
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
    }
}
