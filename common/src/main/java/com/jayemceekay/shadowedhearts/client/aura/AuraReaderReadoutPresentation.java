package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Content fitting for the compact tracking readout, independent of the renderer. */
public final class AuraReaderReadoutPresentation {
    private static final String SEPARATOR = " \u2022 ";
    // Legacy protocol labels are recognized here, never used as display text.
    private static final Map<String, UmbrafallTier> LEGACY_UMBRAFALL_TIERS = Arrays.stream(UmbrafallTier.values())
            .collect(Collectors.toUnmodifiableMap(
                    tier -> tier.name() + " Umbrafall",
                    tier -> tier));

    private AuraReaderReadoutPresentation() {
    }

    /** Keep tier identity separate from translated word order. Custom labels stay intact. */
    public static SignalTitle signalTitle(AuraReadingType type, Component label, String fallbackName) {
        if (type == AuraReadingType.UMBRAFALL_SIGNAL && label != null
                && label.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("hud.shadowedhearts.aura_reader.reading.umbrafall")
                && contents.getArgs().length == 1 && contents.getArgs()[0] instanceof Component tier) {
            return new SignalTitle(Component.translatable("hud.shadowedhearts.aura_reader.umbrafall_name").getString(),
                    tier.getString());
        }
        return signalTitle(type, label == null ? null : label.getString(), fallbackName);
    }

    /**
     * Separates the native Umbrafall tier from its name for the signal readout.
     * Supports the exact legacy labels while resolving their display text in
     * the current language. Custom labels remain intact. The tier is unrelated
     * to the distance-dependent strength value.
     */
    public static SignalTitle signalTitle(AuraReadingType type, String label, String fallbackName) {
        String name = label == null || label.isBlank() ? Objects.requireNonNullElse(fallbackName, "") : label;
        if (type == AuraReadingType.UMBRAFALL_SIGNAL && label != null) {
            UmbrafallTier tier = LEGACY_UMBRAFALL_TIERS.get(label);
            if (tier != null) {
                return new SignalTitle(Component.translatable("hud.shadowedhearts.aura_reader.umbrafall_name").getString(),
                        Component.translatable("umbrafall.tier." + tier.name().toLowerCase(Locale.ROOT)).getString());
            }
        }
        return new SignalTitle(name, "");
    }

    public record SignalTitle(String name, String strength) {
    }

    /**
     * Fits whole, already-localized details in vertical, distance, then
     * stability priority. Pass an empty vertical hint for level targets.
     * {@code availableWidth} is the usable width of the details row. If details
     * share a row with the target name, subtract the complete name and its
     * spacing first so details never take space away from the target.
     */
    public static String fitDetails(
            String verticalHint,
            String distanceBand,
            String stability,
            int availableWidth,
            ToIntFunction<String> textWidth) {
        Objects.requireNonNull(textWidth, "textWidth");
        if (availableWidth <= 0) {
            return "";
        }
        String fitted = "";
        for (String detail : new String[]{verticalHint, distanceBand, stability}) {
            if (detail == null || detail.isBlank()) {
                continue;
            }
            String candidate = fitted.isEmpty() ? detail : fitted + SEPARATOR + detail;
            if (textWidth.applyAsInt(candidate) <= availableWidth) {
                fitted = candidate;
            }
        }
        return fitted;
    }
}
