package com.jayemceekay.shadowedhearts.common.aura;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record AuraReading(
        String id,
        AuraReadingType type,
        AuraReadingSource source,
        Component labelText,
        Component statusText,
        ResourceLocation dimension,
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
        boolean exactPositionAllowed,
        AuraHeartGaugeReadout heartGauge
) {
    public AuraReading(
            String id, AuraReadingType type, AuraReadingSource source, String label,
            String status, ResourceLocation dimension, double directionX, double directionY,
            double directionZ, float bearingUncertainty, String distanceBand, String verticalHint,
            float strength, float confidence, float interference, int priority,
            long expiryTick, boolean selected, boolean locked, boolean outOfDimension,
            boolean exactPositionAllowed, AuraHeartGaugeReadout heartGauge) {
        this(id, type, source, Component.literal(label == null ? "" : label),
                Component.literal(status == null ? "" : status), dimension, directionX, directionY, directionZ,
                bearingUncertainty, distanceBand, verticalHint, strength, confidence, interference, priority,
                expiryTick, selected, locked, outOfDimension, exactPositionAllowed, heartGauge);
    }

    public AuraReading(
            String id, AuraReadingType type, AuraReadingSource source, Component label,
            Component status, ResourceLocation dimension, double directionX, double directionY,
            double directionZ, float bearingUncertainty, String distanceBand, String verticalHint,
            float strength, float confidence, float interference, int priority,
            long expiryTick, boolean selected, boolean locked, boolean outOfDimension,
            boolean exactPositionAllowed) {
        this(id, type, source, label, status, dimension, directionX, directionY, directionZ,
                bearingUncertainty, distanceBand, verticalHint, strength, confidence, interference, priority,
                expiryTick, selected, locked, outOfDimension, exactPositionAllowed, null);
    }

    public AuraReading(
            String id, AuraReadingType type, AuraReadingSource source, String label, String status,
            ResourceLocation dimension, double directionX, double directionY, double directionZ,
            float bearingUncertainty, String distanceBand, String verticalHint,
            float strength, float confidence, float interference, int priority, long expiryTick,
            boolean selected, boolean locked, boolean outOfDimension, boolean exactPositionAllowed) {
        this(id, type, source, label, status, dimension, directionX, directionY, directionZ,
                bearingUncertainty, distanceBand, verticalHint, strength, confidence, interference,
                priority, expiryTick, selected, locked, outOfDimension, exactPositionAllowed, null);
    }

    public String label() { return labelText.getString(); }
    public String status() { return statusText.getString(); }

    public AuraReading {
        id = id == null ? "" : id;
        type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
        source = source == null ? AuraReadingSource.PASSIVE : source;
        labelText = labelText == null ? Component.empty() : labelText.copy();
        statusText = statusText == null ? Component.empty() : statusText.copy();
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
