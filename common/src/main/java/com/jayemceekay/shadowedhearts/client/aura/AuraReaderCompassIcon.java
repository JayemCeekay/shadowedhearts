package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;

/** Native texture regions shared by the compass's category markers. */
public enum AuraReaderCompassIcon {
    SHADOW_POKEMON("textures/gui/shadow_type_small.png", 18),
    METEORITE("textures/item/shadow_shard.png", 16);

    private final String path;
    private final int size;

    AuraReaderCompassIcon(String path, int size) {
        this.path = path;
        this.size = size;
    }

    public String path() { return path; }
    public int size() { return size; }

    public static AuraReaderCompassIcon forType(AuraReadingType type) {
        if (type == null) return null;
        return switch (type) {
            case SHADOW_POKEMON -> SHADOW_POKEMON;
            case UMBRAFALL_SIGNAL, UMBRAFALL_CORE, LEGACY_METEOROID -> METEORITE;
            default -> null;
        };
    }
}
