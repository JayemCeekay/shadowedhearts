package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPixelationScale;
import com.jayemceekay.shadowedhearts.config.AuraPixelationStyle;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;

/** Continuous texel sizing and world pixel LOD policy for batched aura fields and individual previews. */
final class AuraPixelationScale {
    private static final double MAXIMUM_PIXELATION_DISTANCE_BLOCKS = 5.0;

    private AuraPixelationScale() { }

    static float desiredWorldPixelLod(
            int maximumPixelSize,
            double cameraDistance) {
        int canonicalMaximum = canonicalMaximumWorldPixelSize(
                maximumPixelSize);
        float maximumLod = maximumWorldPixelLod(canonicalMaximum);
        if (Double.isNaN(cameraDistance) || cameraDistance < 0.0) {
            return maximumLod;
        }
        if (cameraDistance == Double.POSITIVE_INFINITY) {
            return 0.0f;
        }

        double clampedDistance = Math.max(
                MAXIMUM_PIXELATION_DISTANCE_BLOCKS,
                cameraDistance);
        double desiredPixelSize = canonicalMaximum
                * MAXIMUM_PIXELATION_DISTANCE_BLOCKS
                / clampedDistance;
        double lod = Math.log(Math.max(1.0, desiredPixelSize))
                / Math.log(2.0);
        return (float) Math.max(0.0, Math.min(maximumLod, lod));
    }

    static float stabilizedWorldPixelLod(
            float currentPixelLod,
            int maximumPixelSize,
            double cameraDistance) {
        float maximumLod = maximumWorldPixelLod(maximumPixelSize);
        float current = Float.isFinite(currentPixelLod)
                ? Mth.clamp(currentPixelLod, 0.0f, maximumLod)
                : maximumLod;
        if (Double.isNaN(cameraDistance) || cameraDistance < 0.0) {
            return current;
        }
        return desiredWorldPixelLod(maximumPixelSize, cameraDistance);
    }

    static int canonicalMaximumWorldPixelSize(int maximumPixelSize) {
        return Integer.highestOneBit(Math.max(1, maximumPixelSize));
    }

    static float maximumWorldPixelLod(int maximumPixelSize) {
        int canonicalMaximum = canonicalMaximumWorldPixelSize(
                maximumPixelSize);
        return (float) (Math.log(canonicalMaximum) / Math.log(2.0));
    }

    static double worldSize(Matrix4fc projection, double distance, double modelSize,
                            int viewportWidth, int viewportHeight) {
        return MoonwoundPixelationScale.nominalWidth(projection, distance, modelSize,
                viewportWidth, viewportHeight);
    }

    /** World batches share a viewport-centered grid; the finest visible source determines its density. */
    static MoonwoundPixelationScale.Grid worldGrid(AuraPixelationStyle style, double projectedSize,
                                                   int width, int height) {
        return grid(style, projectedSize, width * 0.5, height * 0.5, width, height);
    }

    static MoonwoundPixelationScale.Grid grid(AuraPixelationStyle style, double projectedSize,
                                              double anchorX, double anchorY, int width, int height) {
        return MoonwoundPixelationScale.grid(style == null ? 1 : style.pixelSize(), projectedSize,
                anchorX, anchorY, width, height);
    }
}
