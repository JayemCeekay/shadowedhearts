package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.config.AuraPixelationStyle;

/** Presentation defaults without renderer or JVM-property state. */
final class ShadowAuraPresentationPolicy {
    private ShadowAuraPresentationPolicy() {}

    /**
     * Signature keeps its authored pixel grid; the classic styles are smooth.
     * An explicitly supplied JVM property retains the previous global override
     * semantics, including Boolean.parseBoolean's handling of invalid values.
     */
    static boolean pixelated(ShadowAuraStyle style, String override) {
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return style == null || style == ShadowAuraStyle.SIGNATURE;
    }

    static AuraPixelationStyle classicPixelation(AuraPixelationStyle configured, String override) {
        AuraPixelationStyle selected = configured == null ? AuraPixelationStyle.SMOOTH : configured;
        if (override == null) return selected;
        if (!Boolean.parseBoolean(override)) return AuraPixelationStyle.SMOOTH;
        return selected == AuraPixelationStyle.SMOOTH ? AuraPixelationStyle.PIXELATED : selected;
    }
}
