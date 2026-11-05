package com.jayemceekay.shadowedhearts.common.aura;

import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stable per-Pokemon random style, projected separately from an explicit aura property. */
public final class AuraStyleRoll {
    public static final String PERSISTENT_KEY = "shadowedhearts:aura_style_roll";

    private static final List<AuraStyleSelection> STYLES = List.of(
            AuraStyleSelection.SIGNATURE, AuraStyleSelection.COLOSSEUM, AuraStyleSelection.XD_FAITHFUL);

    private AuraStyleRoll() {}

    /** Keep this mapping stable: the same saved Pokemon must keep its roll across sessions. */
    public static AuraStyleSelection forPokemon(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        long mixed = (uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 29))
                + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return STYLES.get((int) Long.remainderUnsigned(mixed, STYLES.size()));
    }

    public static String aspectId(AuraStyleSelection style) {
        return switch (Objects.requireNonNull(style, "style")) {
            case SIGNATURE -> SHAspects.AURA_ROLL_SIGNATURE;
            case COLOSSEUM -> SHAspects.AURA_ROLL_COLOSSEUM;
            case XD_FAITHFUL -> SHAspects.AURA_ROLL_XD;
            case DEFAULT -> throw new IllegalArgumentException("An inherited selection is not a rolled style");
        };
    }

    /** Internal saved values use existing stable style IDs; DEFAULT is not a roll. */
    public static Optional<AuraStyleSelection> storedStyle(String value) {
        return AuraStyleSelection.parse(value).filter(style -> style != AuraStyleSelection.DEFAULT);
    }

    public static Optional<AuraStyleSelection> fromAspect(String aspect) {
        if (aspect == null) return Optional.empty();
        String normalized = aspect.trim().toLowerCase(Locale.ROOT);
        for (AuraStyleSelection style : STYLES) {
            if (aspectId(style).equals(normalized)) return Optional.of(style);
        }
        return Optional.empty();
    }

    /** A malformed snapshot with multiple roll flags uses deterministic XD, Colosseum, Signature priority. */
    public static Optional<AuraStyleSelection> fromAspects(Iterable<String> aspects) {
        if (aspects == null) return Optional.empty();
        Set<AuraStyleSelection> styles = new HashSet<>();
        for (String aspect : aspects) fromAspect(aspect).ifPresent(styles::add);
        for (int index = STYLES.size() - 1; index >= 0; index--) {
            if (styles.contains(STYLES.get(index))) return Optional.of(STYLES.get(index));
        }
        return Optional.empty();
    }
}
