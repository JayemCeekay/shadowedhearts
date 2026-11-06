package com.jayemceekay.shadowedhearts.client.aura.shadow.style;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum.ColosseumAuraProfile;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.signature.SignatureAuraProfile;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd.XdAuraProfile;

import java.util.List;
import java.util.Map;

/**
 * Authored immutable style profiles. Classic regional emission uses broad/mote
 * density scales and the filament enable flag; local geometry, budgets and
 * lifetimes are authored in ShadowPokemonAuraSystem. The remaining burst/puff
 * tuning is retained for the legacy helpers, not as controls for the new curves.
 */
public final class ShadowAuraStyleProfiles {
    public static final ShadowAuraStyleProfile SIGNATURE =
            SignatureAuraProfile.INSTANCE;

    /** Soft violet plumes with pale motes; regional emission uses local posed dimensions. */
    public static final ShadowAuraStyleProfile COLOSSEUM =
            ColosseumAuraProfile.INSTANCE;

    /** Continuous curling ribbons over a light regional haze. Legacy burst tuning is retained for compatibility. */
    public static final ShadowAuraStyleProfile XD_FAITHFUL =
            XdAuraProfile.INSTANCE;

    private static final Map<ShadowAuraStyle, ShadowAuraStyleProfile> BY_STYLE =
            Map.of(
                    ShadowAuraStyle.SIGNATURE, SIGNATURE,
                    ShadowAuraStyle.COLOSSEUM, COLOSSEUM,
                    ShadowAuraStyle.XD_FAITHFUL, XD_FAITHFUL
            );

    private static final List<ShadowAuraStyleProfile> ALL = List.of(
            SIGNATURE,
            COLOSSEUM,
            XD_FAITHFUL
    );

    private ShadowAuraStyleProfiles() {}

    public static ShadowAuraStyleProfile forStyle(ShadowAuraStyle style) {
        return BY_STYLE.getOrDefault(
                style == null ? ShadowAuraStyle.DEFAULT : style,
                SIGNATURE);
    }

    public static List<ShadowAuraStyleProfile> all() {
        return ALL;
    }
}
