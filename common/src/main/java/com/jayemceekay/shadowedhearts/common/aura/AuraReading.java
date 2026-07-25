package com.jayemceekay.shadowedhearts.common.aura;

import net.minecraft.resources.ResourceLocation;

public record AuraReading(
        String id,
        AuraReadingType type,
        String label,
        String status,
        ResourceLocation dimension,
        double directionX,
        double directionY,
        double directionZ,
        String distanceBand,
        String verticalHint,
        float strength,
        float confidence,
        boolean exactPositionAllowed
) {
    public AuraReading {
        id = id == null ? "" : id;
        type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
        label = label == null ? "" : label;
        status = status == null ? "" : status;
        distanceBand = distanceBand == null ? "" : distanceBand;
        verticalHint = verticalHint == null ? "" : verticalHint;
        strength = clamp01(strength);
        confidence = clamp01(confidence);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
