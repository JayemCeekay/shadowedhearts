package com.jayemceekay.shadowedhearts.world.umbrafall;

import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModPoiTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;

/** Server-world queries for active Umbrafall corruption sources. */
public final class UmbrafallProximity {
    private UmbrafallProximity() {}

    public static boolean isNearActiveCore(ServerLevel level, BlockPos pos, int radius, int verticalRadius) {
        // Ensure search radius covers the furthest possible point in the sphere/cylinder
        double searchRadius = Math.max(radius, verticalRadius);

        return level.getPoiManager().getInRange(
                holder -> holder.value() == ModPoiTypes.SHADOWFALL_METEOROID.get()
                        || holder.value() == ModPoiTypes.UMBRAFALL_CORE.get(),
                pos,
                (int) Math.ceil(searchRadius),
                PoiManager.Occupancy.ANY
        ).anyMatch(poiRecord -> {
            BlockPos p = poiRecord.getPos();
            // A saved legacy POI may outlive its migrated block until vanilla refreshes the section.
            // Never re-enable corruption around inert shell, and never load a chunk just for this check.
            if (!level.hasChunkAt(p)) return false;
            var material = level.getBlockState(p);
            if (!material.is(ModBlocks.SHADOWFALL_METEOROID.get())
                    && !material.is(ModBlocks.UMBRAFALL_CORE.get())) return false;
            // Use squared distance for efficiency
            double dx = p.getX() - pos.getX();
            double dy = p.getY() - pos.getY();
            double dz = p.getZ() - pos.getZ();

            // Spherical check: x^2 + y^2 + z^2 <= radius^2
            // If radius and verticalRadius differ, we use an ellipsoidal check
            if (radius == verticalRadius) {
                return (dx * dx + dy * dy + dz * dz) <= (double) radius * radius;
            } else {
                // Ellipsoidal check to handle vertical variance if configured differently
                return (dx * dx + dz * dz) / (double) (radius * radius) + (dy * dy) / (double) (verticalRadius * verticalRadius) <= 1.0;
            }
        });
    }
}
