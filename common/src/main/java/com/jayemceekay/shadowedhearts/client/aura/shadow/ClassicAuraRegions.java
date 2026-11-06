package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.classic.ClassicAuraMorphology;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraSurfaceSampler;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraSurfaceSupport;

import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.AnchorKey;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.AnchorSource;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelAnchors.BoneAnchor;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.AnchorRole;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.cubeAnchorRole;
import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.cubeTemplate;

/** Builds anatomical aura regions from captured geometry and anchors for either world or GUI sources. */
final class ClassicAuraRegions {
    private static final int CLASSIC_REGIONS_PER_SOURCE = 96;

    private ClassicAuraRegions() {
    }

    static ClassicPartGeometry describeClassicPart(ModelPart part, Matrix4f pose, Vec3 offset, Object parent) {
        return describeClassicPart(part, pose, offset, parent, false);
    }

    static ClassicPartGeometry describeClassicPart(ModelPart part, Matrix4f pose, Vec3 offset, Object parent,
                                                           boolean hidden) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (ModelPart.Cube cube : hidden ? List.<ModelPart.Cube>of() : part.cubes) {
            if (!cubeTemplate(cube).auraAnchor()) continue;
            minX = Math.min(minX, cube.minX); minY = Math.min(minY, cube.minY); minZ = Math.min(minZ, cube.minZ);
            maxX = Math.max(maxX, cube.maxX); maxY = Math.max(maxY, cube.maxY); maxZ = Math.max(maxZ, cube.maxZ);
        }
        if (!Float.isFinite(minX)) return new ClassicPartGeometry(parent, Vec3.ZERO,
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), 0, 0, 0, false);
        Vector3f x = pose.transformDirection(1, 0, 0, new Vector3f());
        Vector3f y = pose.transformDirection(0, 1, 0, new Vector3f());
        Vector3f z = pose.transformDirection(0, 0, 1, new Vector3f());
        float sx = (maxX - minX) / 16.0f * x.length();
        float sy = (maxY - minY) / 16.0f * y.length();
        float sz = (maxZ - minZ) / 16.0f * z.length();
        Vector3f center = pose.transformPosition((minX + maxX) / 32.0f,
                (minY + maxY) / 32.0f, (minZ + maxZ) / 32.0f, new Vector3f());
        return new ClassicPartGeometry(parent, new Vec3(center.x, center.y, center.z).add(offset),
                new Vec3(x.x, x.y, x.z).normalize(), new Vec3(y.x, y.y, y.z).normalize(), new Vec3(z.x, z.y, z.z).normalize(),
                sx, sy, sz, cubeAnchorRole(sx, sy, sz) == AnchorRole.SURFACE);
    }

    private static Object classicRegionKey(Map<Object, ClassicPartGeometry> geometryByPart, Object part) {
        Object regionKey = part;
        ClassicPartGeometry geometry = geometryByPart.get(part), chosen = geometry;
        for (int depth = 0; geometry != null && geometry.parent != null && depth < 8; depth++) {
            Object parent = geometry.parent;
            geometry = geometryByPart.get(parent);
            if (geometry == null) break;
            if (geometry.empty()) continue;
            if (chosen == null || chosen.empty() || (chosen.volume() < geometry.volume() * 0.18f
                    && chosen.longest() < geometry.envelope() * 1.35f
                    && chosen.envelope() < geometry.envelope() * 0.60f)) {
                regionKey = parent;
                chosen = geometry;
            } else break;
        }
        return regionKey;
    }

    /**
     * Rebuilds the caller's anchor index and region map in place, retaining cursors for surviving keys.
     * Geometry and captured surfaces are read-only inputs; uncapped regions retain capture order.
     */
    static List<ClassicRegion> rebuildClassicRegions(
            Map<Object, ClassicPartGeometry> geometryByPart,
            List<XdAuraSurfaceSampler.Cube> capturedSurfaces,
            Map<Object, ClassicRegion> regionsByKey,
            Map<Object, BoneAnchor> anchorsByKey,
            List<BoneAnchor> anchors,
            ShadowAuraStyle style,
            String species) {
        Map<Object, ClassicRegion> regions = new LinkedHashMap<>();
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (BoneAnchor anchor : anchors) {
            if (anchor.key() == null || !finite(anchor.pos())) continue;
            low = Math.min(low, anchor.pos().y); high = Math.max(high, anchor.pos().y);
            Object part = anchor.key() instanceof AnchorKey key ? key.part() : "unclassified";
            Object key = classicRegionKey(geometryByPart, part);
            regions.computeIfAbsent(key, ignored -> new ClassicRegion(key, geometryByPart.get(key))).anchors.add(anchor);
        }
        // Geometry capture is independent of the legacy cube candidate budget.
        // Regions later in a long model's traversal must not disappear when that
        // raw budget fills before their body cubes are visited.
        for (var entry : geometryByPart.entrySet()) {
            if (entry.getValue().empty()) continue;
            Object key = classicRegionKey(geometryByPart, entry.getKey());
            regions.computeIfAbsent(key, ignored -> new ClassicRegion(key, geometryByPart.get(key)));
        }
        if (regions.size() > CLASSIC_REGIONS_PER_SOURCE) {
            List<ClassicRegion> largest = new ArrayList<>(regions.values());
            largest.sort((a, b) -> Float.compare(b.geometry == null ? 0 : b.geometry.area(),
                    a.geometry == null ? 0 : a.geometry.area()));
            regions.clear();
            for (int i = 0; i < CLASSIC_REGIONS_PER_SOURCE; i++) regions.put(largest.get(i).key, largest.get(i));
        }
        low = Double.POSITIVE_INFINITY;
        high = Double.NEGATIVE_INFINITY;
        float totalArea = 0, largestArea = 0, weightedScale = 0, totalVolume = 0;
        for (ClassicRegion region : regions.values()) {
            if (region.geometry == null || region.geometry.empty()) region.geometry = fallbackClassicGeometry(region.anchors);
            double halfHeight = (Math.abs(region.geometry.axisX.y) * region.geometry.x
                    + Math.abs(region.geometry.axisY.y) * region.geometry.y
                    + Math.abs(region.geometry.axisZ.y) * region.geometry.z) * .5;
            low = Math.min(low, region.geometry.center.y - halfHeight);
            high = Math.max(high, region.geometry.center.y + halfHeight);
            float area = region.geometry.area();
            totalArea += area;
            largestArea = Math.max(largestArea, area);
            if (!region.geometry.surface) {
                float volume = region.geometry.volume();
                weightedScale += volume * region.geometry.envelope() * region.geometry.envelope();
                totalVolume += volume;
            }
        }
        float referenceScale = totalVolume > 0 ? (float) Math.sqrt(weightedScale / totalVolume) : .2f;
        float spread = Mth.clamp((totalArea / Math.max(.01f, largestArea) - 1) / 5, 0, 1);
        boolean xd = style == ShadowAuraStyle.XD_FAITHFUL;
        // Compact XD bodies still need a spanning field, independently of the
        // local thickness that keeps individual folds from becoming giant sheets.
        float envelopeFactor = xd ? .85f + .15f * spread : .32f + .68f * spread;
        float curlFactor = xd ? .82f + .18f * spread : .55f + .45f * spread;
        anchorsByKey.clear();
        for (ClassicRegion region : regions.values()) {
            ClassicRegion old = regionsByKey.get(region.key);
            if (old != null) { region.cursor = old.cursor; region.curveCursor = old.curveCursor; }
            float naturalScale = region.geometry.envelope();
            region.envelopeScale = naturalScale * envelopeFactor;
            region.xdFieldScale = xd ? ClassicAuraMorphology.xdFieldScale(naturalScale, referenceScale) : naturalScale;
            if (xd) region.envelopeScale = region.xdFieldScale * envelopeFactor;
            region.curlFactor = curlFactor;
            float h = (float) ((region.geometry.center.y - low) / Math.max(0.05, high - low));
            region.emphasis = ClassicAuraMorphology.regionWeight(species, h, region.geometry.surface, false, true);
            region.detailEmphasis = ClassicAuraMorphology.regionWeight(species, h, region.geometry.surface, false, false);
            if (xd) {
                region.emphasis *= ClassicAuraMorphology.xdHeightWeight(species, h, true);
                region.detailEmphasis *= ClassicAuraMorphology.xdHeightWeight(species, h, false);
            }
            // Normalize tiny details against a substantial regional thickness, so
            // ten tiny ornaments do not claim ten torso-sized emission quotas.
            region.demand = ClassicAuraMorphology.coverageDemand(region.geometry.x, region.geometry.y,
                    region.geometry.z, Math.max(naturalScale, referenceScale * .70f), region.emphasis);
            if (geometryByPart.containsKey(region.key)) {
                region.anchors.clear();
                region.faceSurfaceSupports = xd && region.geometry.surface;
                int samples = Mth.clamp((int) Math.ceil(region.geometry.area() / Math.max(.025f, naturalScale * naturalScale) * 2), 12, 24);
                for (int i = 0; i < samples; i++) {
                    if (region.faceSurfaceSupports) {
                        XdAuraSurfaceSupport.Sample sample = XdAuraSurfaceSupport.sample(
                                region.geometry.x, region.geometry.y, region.geometry.z, i, samples);
                        Vec3 offset = region.geometry.axisX.scale(sample.offset().x)
                                .add(region.geometry.axisY.scale(sample.offset().y))
                                .add(region.geometry.axisZ.scale(sample.offset().z));
                        Vec3 normal = region.geometry.axisX.scale(sample.normal().x)
                                .add(region.geometry.axisY.scale(sample.normal().y))
                                .add(region.geometry.axisZ.scale(sample.normal().z)).normalize();
                        region.anchors.add(new BoneAnchor(region.geometry.center.add(offset), 2, 1,
                                AnchorRole.SURFACE, AnchorSource.CUBE, normal,
                                new AnchorKey(region.key, "classic-region", i), naturalScale * .65f, normal));
                        continue;
                    }
                    float vertical = 1 - 2 * ((i + .5f) / samples);
                    double ring = Math.sqrt(Math.max(0, 1 - vertical * vertical));
                    double angle = i * 2.399963229728653;
                    Vec3 offset = region.geometry.axisX.scale(Math.cos(angle) * ring * region.geometry.x * .5)
                            .add(region.geometry.axisY.scale(vertical * region.geometry.y * .5))
                            .add(region.geometry.axisZ.scale(Math.sin(angle) * ring * region.geometry.z * .5));
                    Vec3 normal = offset.lengthSqr() > .000001 ? offset.normalize() : region.geometry.axisY;
                    BoneAnchor support = new BoneAnchor(region.geometry.center.add(offset), 2, 1,
                            region.geometry.surface ? AnchorRole.SURFACE : AnchorRole.BODY, AnchorSource.CUBE,
                            normal, new AnchorKey(region.key, "classic-region", i), naturalScale * .65f, normal);
                    region.anchors.add(support);
                }
            }
            for (BoneAnchor anchor : region.anchors) anchorsByKey.put(anchor.key(), anchor);
        }
        if (xd) {
            // Allocation may merge small children into a parent, but their actual
            // rendered faces remain eligible instead of becoming the parent's box.
            for (XdAuraSurfaceSampler.Cube cube : capturedSurfaces) {
                ClassicRegion owner = regions.get(classicRegionKey(geometryByPart, cube.part()));
                if (owner != null) owner.xdSurfaceCubes.add(cube);
            }
        }
        regionsByKey.clear();
        regionsByKey.putAll(regions);
        return new ArrayList<>(regions.values());
    }

    private static ClassicPartGeometry fallbackClassicGeometry(List<BoneAnchor> anchors) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        float thickness = 0.1f;
        for (BoneAnchor anchor : anchors) {
            minX = Math.min(minX, anchor.pos().x); minY = Math.min(minY, anchor.pos().y); minZ = Math.min(minZ, anchor.pos().z);
            maxX = Math.max(maxX, anchor.pos().x); maxY = Math.max(maxY, anchor.pos().y); maxZ = Math.max(maxZ, anchor.pos().z);
            thickness = Math.max(thickness, anchor.localScale());
        }
        return new ClassicPartGeometry(null, new Vec3((minX + maxX) * .5, (minY + maxY) * .5, (minZ + maxZ) * .5),
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Math.max(thickness, (float) (maxX - minX)), Math.max(thickness, (float) (maxY - minY)),
                Math.max(thickness, (float) (maxZ - minZ)), false);
    }

    private static Vec3 classicNormal(BoneAnchor anchor) {
        return anchor.flowNormal().lengthSqr() > 0.0001 ? anchor.flowNormal().normalize() : new Vec3(0, 1, 0);
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    record ClassicPartGeometry(Object parent, Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
                                        float x, float y, float z, boolean surface) {
        boolean empty() { return x + y + z < 0.001f; }
        float volume() { return Math.max(0.025f, x) * Math.max(0.025f, y) * Math.max(0.025f, z); }
        float longest() { return Math.max(x, Math.max(y, z)); }
        float area() { return 2 * (x * y + y * z + x * z); }
        float envelope() { return ClassicAuraMorphology.envelopeScale(x, y, z, surface); }
    }

    /** A posed anatomical support shared by its cubes and small attached details. */
    static final class ClassicRegion {
        final Object key;
        ClassicPartGeometry geometry;
        final List<BoneAnchor> anchors = new ArrayList<>();
        final List<XdAuraSurfaceSampler.Cube> xdSurfaceCubes = new ArrayList<>();
        int cursor, curveCursor;
        float envelopeScale, xdFieldScale, curlFactor, emphasis, detailEmphasis, demand;
        float nearOccupancy, plumeOccupancy, curveOccupancy;
        boolean faceSurfaceSupports;

        ClassicRegion(Object key, ClassicPartGeometry geometry) {
            this.key = key;
            this.geometry = geometry;
        }

        BoneAnchor nextAnchor() { return anchors.get(Math.floorMod(cursor++, anchors.size())); }
        BoneAnchor nextCurveAnchor() { return anchors.get(Math.floorMod(curveCursor++, anchors.size())); }

        BoneAnchor nextCurveAnchor(boolean preferLower) {
            BoneAnchor candidate = nextCurveAnchor();
            // A merged body region still has top and underside supports. Quieting
            // only the region center cannot keep Ledyba's upper shell readable.
            for (int attempts = 1; preferLower && normal(candidate).y > .25 && attempts < anchors.size(); attempts++) {
                candidate = nextCurveAnchor();
            }
            return candidate;
        }

        Vec3 normal(BoneAnchor anchor) {
            if (faceSurfaceSupports) return classicNormal(anchor);
            Vec3 radial = anchor.pos().subtract(geometry.center);
            return radial.lengthSqr() > 0.00001 ? radial.normalize() : classicNormal(anchor);
        }

        Vec3 root(BoneAnchor anchor, float padding) {
            Vec3 direction = normal(anchor);
            if (faceSurfaceSupports) return anchor.pos().add(direction.scale(padding));
            // Intersect the posed box, not an inscribed ellipsoid: corner samples
            // must stay outside solid geometry when their density is rendered.
            double inverseRadius = Math.max(Math.abs(direction.dot(geometry.axisX)) / Math.max(0.0125, geometry.x * 0.5),
                    Math.max(Math.abs(direction.dot(geometry.axisY)) / Math.max(0.0125, geometry.y * 0.5),
                            Math.abs(direction.dot(geometry.axisZ)) / Math.max(0.0125, geometry.z * 0.5)));
            double radius = inverseRadius > 0.00001 ? 1.0 / inverseRadius : envelopeScale * 0.5;
            radius = Math.max(radius, anchor.pos().distanceTo(geometry.center));
            return geometry.center.add(direction.scale(radius + padding));
        }
    }
}
