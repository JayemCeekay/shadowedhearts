package com.jayemceekay.shadowedhearts.common.aura;

public enum AuraReaderMode {
    PASSIVE_AURA,
    PULSE_SCAN,
    SIGNAL_TRACKING,
    ENTITY_ANALYSIS,
    ANOMALY_SCAN;

    public static AuraReaderMode fromName(String name) {
        if (name == null || name.isBlank()) {
            return PASSIVE_AURA;
        }

        try {
            return AuraReaderMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return PASSIVE_AURA;
        }
    }
}
