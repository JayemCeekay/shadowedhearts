package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraStyleSelection;

import java.util.Locale;
import java.util.Optional;

/**
 * Stable identities for the client-side Shadow aura presentations.
 *
 * <p>The serialized property names are deliberately independent of enum names;
 * the server config maps its presentation choices to these stable identities. An
 * explicit sh_aura_style property derives the matching render aspect for that Pokemon.
 */
public enum ShadowAuraStyle {
    SIGNATURE(AuraStyleSelection.SIGNATURE),
    COLOSSEUM(AuraStyleSelection.COLOSSEUM),
    XD_FAITHFUL(AuraStyleSelection.XD_FAITHFUL);

    public static final ShadowAuraStyle DEFAULT = SIGNATURE;

    private final String serializedName;
    private final String aspectId;

    ShadowAuraStyle(AuraStyleSelection selection) {
        this.serializedName = selection.serializedName();
        this.aspectId = selection.aspectId().orElseThrow();
    }

    public String serializedName() {
        return serializedName;
    }

    public String aspectId() {
        return aspectId;
    }

    /** Maps common, server-owned style identities without exposing rendering classes to the config. */
    public static ShadowAuraStyle fromSelection(AuraStyleSelection selection) {
        if (selection == null) return DEFAULT;
        return switch (selection) {
            case DEFAULT, SIGNATURE -> SIGNATURE;
            case COLOSSEUM -> COLOSSEUM;
            case XD_FAITHFUL -> XD_FAITHFUL;
        };
    }

    /**
     * Parses a serialized style identity. Null, blank, and unknown values safely retain the
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

    /** Alias for callers that treat the serialized identity as a style name. */
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
