package com.jayemceekay.shadowedhearts.common.aura;

import net.minecraft.resources.ResourceLocation;

public record AuraReading(
        String id,
        AuraReadingType type,
        AuraReadingSource source,
        String label,
        String status,
        net.minecraft.resources.ResourceLocation dimension,
        double directionX,
        double directionY,
        double directionZ,
        float bearingUncertainty,
        String distanceBand,
        String verticalHint,
        float strength,
        float confidence,
        float interference,
        int priority,
        long expiryTick,
        boolean selected,
        boolean locked,
        boolean outOfDimension,
        boolean exactPositionAllowed
) {
    public AuraReading {
        id = id == null ? "" : id;
        type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
        source = source == null ? AuraReadingSource.PASSIVE : source;
        label = label == null ? "" : label;
        status = status == null ? "" : status;
        distanceBand = distanceBand == null ? "" : distanceBand;
        verticalHint = verticalHint == null ? "" : verticalHint;
        strength = clamp01(strength);
        confidence = clamp01(confidence);
        interference = clamp01(interference);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
