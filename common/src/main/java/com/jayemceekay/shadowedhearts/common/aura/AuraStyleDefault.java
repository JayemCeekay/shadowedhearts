package com.jayemceekay.shadowedhearts.common.aura;

/** Server-wide policy for Pokemon that have no explicit aura style selection. */
public enum AuraStyleDefault {
    SIGNATURE,
    COLOSSEUM,
    XD,
    RANDOM;

    /** RANDOM uses the Pokemon's synchronized UUID-derived roll; missing identity falls back to Signature. */
    public AuraStyleSelection resolve(AuraStyleSelection rolledStyle) {
        return switch (this) {
            case SIGNATURE -> AuraStyleSelection.SIGNATURE;
            case COLOSSEUM -> AuraStyleSelection.COLOSSEUM;
            case XD -> AuraStyleSelection.XD_FAITHFUL;
            case RANDOM -> rolledStyle == null || rolledStyle == AuraStyleSelection.DEFAULT
                    ? AuraStyleSelection.SIGNATURE : rolledStyle;
        };
    }
}
