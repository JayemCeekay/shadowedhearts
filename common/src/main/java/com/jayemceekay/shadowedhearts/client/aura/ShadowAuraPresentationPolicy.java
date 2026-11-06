package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.config.AuraPixelationStyle;

/** Presentation defaults without renderer or JVM-property state. */
final class ShadowAuraPresentationPolicy {
    private ShadowAuraPresentationPolicy() {}

    /**
     * Missing settings keep Signature's authored pixel grid and smooth classic styles.
     * An explicitly supplied JVM property retains the previous global override
     * semantics, including Boolean.parseBoolean's handling of invalid values.
     */
    static AuraPixelationStyle pixelation(ShadowAuraStyle style,
                                         AuraPixelationStyle configured, String override) {
        AuraPixelationStyle fallback = style == null || style == ShadowAuraStyle.SIGNATURE
                ? AuraPixelationStyle.PIXELATED : AuraPixelationStyle.SMOOTH;
        AuraPixelationStyle selected = configured == null ? fallback : configured;
        if (override == null) return selected;
        if (!Boolean.parseBoolean(override)) return AuraPixelationStyle.SMOOTH;
        return selected == AuraPixelationStyle.SMOOTH ? AuraPixelationStyle.PIXELATED : selected;
    }
}
