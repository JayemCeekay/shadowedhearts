package com.jayemceekay.shadowedhearts.common.aura;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The same range-limited sight ray is used for server analysis and client focus. */
public final class AuraReaderTargeting {
    private AuraReaderTargeting() {
    }

    public static Entity pickEntity(Level level, Entity viewer, float partialTick, double range) {
        if (level == null || viewer == null || !Double.isFinite(range) || range <= 0.0) {
            return null;
        }
        Vec3 eye = viewer.getEyePosition(partialTick);
        // AABB.clip excludes the end of the segment; reach is enforced separately below.
        Vec3 end = eye.add(viewer.getViewVector(partialTick).scale(range + 0.001));
        HitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        double closestDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY : eye.distanceToSqr(blockHit.getLocation());
        Entity closest = null;
        for (Entity entity : level.getEntities(viewer, new AABB(eye, end).inflate(1.0),
                candidate -> candidate.isAlive() && !candidate.isSpectator() && candidate.isPickable())) {
            // Match the rendered entity position, without widening its hitbox into an aim cone.
            Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
            double distance = hitDistanceSqr(entity.getBoundingBox().move(offset), eye, end);
            if (isWithinReach(distance, range, closestDistance)) {
                closest = entity;
                closestDistance = distance;
            }
        }
        return closest;
    }

    public static double hitDistanceSqr(AABB bounds, Vec3 eye, Vec3 end) {
        if (bounds.contains(eye)) {
            return 0.0;
        }
        return bounds.clip(eye, end).map(eye::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
    }

    /** Navigation range is measured to the hitbox, matching the surface a sight ray can reach. */
    public static double distanceToHitboxSqr(Entity viewer, Entity target) {
        Vec3 eye = viewer.getEyePosition(1.0f);
        AABB bounds = target.getBoundingBox();
        double x = Math.max(bounds.minX - eye.x, Math.max(0.0, eye.x - bounds.maxX));
        double y = Math.max(bounds.minY - eye.y, Math.max(0.0, eye.y - bounds.maxY));
        double z = Math.max(bounds.minZ - eye.z, Math.max(0.0, eye.z - bounds.maxZ));
        return x * x + y * y + z * z;
    }

    public static boolean isWithinReach(double hitDistanceSqr, double range, double obstructionDistanceSqr) {
        return Double.isFinite(range) && range > 0.0
                && Double.isFinite(hitDistanceSqr) && hitDistanceSqr >= 0.0
                && hitDistanceSqr <= range * range && hitDistanceSqr < obstructionDistanceSqr;
    }
}
