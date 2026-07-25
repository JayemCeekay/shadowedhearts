package com.jayemceekay.shadowedhearts.common.aura;

public enum AuraReaderLockType {
    NONE,
    ENTITY,
    UMBRAFALL_SITE,
    BLOCK;

    public static AuraReaderLockType fromName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }

        try {
            return AuraReaderLockType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
