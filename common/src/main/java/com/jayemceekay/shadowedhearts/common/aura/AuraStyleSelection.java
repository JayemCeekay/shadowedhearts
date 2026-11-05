package com.jayemceekay.shadowedhearts.common.aura;

import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;

import java.util.Locale;
import java.util.Optional;

/** Stored per-Pokemon aura selection; DEFAULT inherits the server's configured style. */
public enum AuraStyleSelection {
    DEFAULT("default", null),
    SIGNATURE("signature", SHAspects.AURA_STYLE_SIGNATURE),
    COLOSSEUM("colosseum", SHAspects.AURA_STYLE_COLOSSEUM),
    XD_FAITHFUL("xd_faithful", SHAspects.AURA_STYLE_XD);

    private final String serializedName;
    private final String aspectId;

    AuraStyleSelection(String serializedName, String aspectId) {
        this.serializedName = serializedName;
        this.aspectId = aspectId;
    }

    public String serializedName() {
        return serializedName;
    }

    public Optional<String> aspectId() {
        return Optional.ofNullable(aspectId);
    }

    /** Accepts the finite property values without turning an invalid value into a selection. */
    public static Optional<AuraStyleSelection> parse(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AuraStyleSelection selection : values()) {
            if (selection.serializedName.equals(normalized)) {
                return Optional.of(selection);
            }
        }
        return Optional.empty();
    }

    public static Optional<AuraStyleSelection> fromAspect(String aspect) {
        if (aspect == null) return Optional.empty();
        String normalized = aspect.trim().toLowerCase(Locale.ROOT);
        for (AuraStyleSelection selection : values()) {
            if (selection.aspectId != null && selection.aspectId.equals(normalized)) {
                return Optional.of(selection);
            }
        }
        return Optional.empty();
    }
}
