package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPixelationScale;
import com.jayemceekay.shadowedhearts.config.AuraPixelationStyle;
import org.joml.Matrix4fc;

/** Moonwound's continuous texel sizing, adapted to batched aura fields and individual previews. */
final class AuraPixelationScale {
    private AuraPixelationScale() { }

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
