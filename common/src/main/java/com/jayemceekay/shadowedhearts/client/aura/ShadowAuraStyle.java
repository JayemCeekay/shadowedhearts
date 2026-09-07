package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;

import java.util.Locale;
import java.util.Optional;

/**
 * Stable identities for the client-side Shadow aura presentations.
 *
 * <p>The serialized names are deliberately independent of enum names so that
 * config files remain compatible if the Java names are ever reorganized. An
 * explicit Pokemon aspect may select the same identity for that Pokemon only.
 */
public enum ShadowAuraStyle {
    SIGNATURE("signature", SHAspects.AURA_STYLE_SIGNATURE),
    COLOSSEUM("colosseum", SHAspects.AURA_STYLE_COLOSSEUM),
    XD_FAITHFUL("xd_faithful", SHAspects.AURA_STYLE_XD);

    public static final ShadowAuraStyle DEFAULT = SIGNATURE;

    private final String serializedName;
    private final String aspectId;

    ShadowAuraStyle(String serializedName, String aspectId) {
        this.serializedName = serializedName;
        this.aspectId = aspectId;
    }

    public String serializedName() {
        return serializedName;
    }

    public String aspectId() {
        return aspectId;
    }

    /**
     * Parses a config value. Null, blank, and unknown values safely retain the
     * signature style.
     */
    public static ShadowAuraStyle fromSerializedName(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return DEFAULT;
        }
        for (ShadowAuraStyle style : values()) {
            if (style.serializedName.equals(normalized)) {
                return style;
            }
        }
        return DEFAULT;
    }

    /** Alias for callers that treat the serialized config value as a style name. */
    public static ShadowAuraStyle parse(String value) {
        return fromSerializedName(value);
    }

    /** Predicate suitable for {@code ModConfigSpec#define}. */
    public static boolean isValidSerializedName(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        String normalized = normalize(text);
        if (normalized == null) {
            return false;
        }
        for (ShadowAuraStyle style : values()) {
            if (style.serializedName.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the style selected by one canonical aspect, if any. */
    public static Optional<ShadowAuraStyle> fromAspect(String aspect) {
        if (aspect == null || aspect.isBlank()) {
            return Optional.empty();
        }
        String normalized = aspect.trim().toLowerCase(Locale.ROOT);
        for (ShadowAuraStyle style : values()) {
            if (style.aspectId.equals(normalized)) {
                return Optional.of(style);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toLowerCase(Locale.ROOT);
    }
}
