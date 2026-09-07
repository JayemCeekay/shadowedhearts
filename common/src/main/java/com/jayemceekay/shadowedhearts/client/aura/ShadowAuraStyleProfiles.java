package com.jayemceekay.shadowedhearts.client.aura;

import java.util.List;
import java.util.Map;

import static com.jayemceekay.shadowedhearts.client.aura.ShadowAuraStyleProfile.*;

/** Authored immutable starting profiles for every supported Shadow aura style. */
public final class ShadowAuraStyleProfiles {
    static final PuffTuning IDENTITY_PUFF =
            new PuffTuning(1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

    public static final ShadowAuraStyleProfile SIGNATURE =
            new ShadowAuraStyleProfile(
                    ShadowAuraStyle.SIGNATURE,
                    new EmissionTuning(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
                    IDENTITY_PUFF,
                    IDENTITY_PUFF,
                    IDENTITY_PUFF,
                    IDENTITY_PUFF,
                    BurstTuning.disabled(),
                    FilamentTuning.disabled(),
                    new RenderTuning(
                            1.0f, 1.0f, 1.0f, 1.0f,
                            1.0f, 1.0f, 1.0f)
            );

    /** Large, slow black-violet clouds punctuated by a restrained mote field. */
    public static final ShadowAuraStyleProfile COLOSSEUM =
            new ShadowAuraStyleProfile(
                    ShadowAuraStyle.COLOSSEUM,
                    new EmissionTuning(0.62f, 0.70f, 0.30f, 0.24f, 0.18f, 0.55f),
                    new PuffTuning(1.35f, 1.65f, 1.65f, 0.80f, 0.42f),
                    new PuffTuning(0.72f, 1.35f, 1.45f, 0.70f, 0.50f),
                    new PuffTuning(0.20f, 1.10f, 1.15f, 0.70f, 0.65f),
                    new PuffTuning(1.70f, 0.95f, 1.35f, 1.15f, 0.70f),
                    BurstTuning.disabled(),
                    FilamentTuning.disabled(),
                    new RenderTuning(
                            1.10f, 0.85f, 0.65f, 1.00f,
                            0.90f, 1.22f, 1.00f)
            );

    /** Sparse isolated bursts with persistent bright plasma filaments. */
    public static final ShadowAuraStyleProfile XD_FAITHFUL =
            new ShadowAuraStyleProfile(
                    ShadowAuraStyle.XD_FAITHFUL,
                    new EmissionTuning(0.42f, 0.48f, 0.0f, 0.0f, 0.0f, 0.32f),
                    new PuffTuning(0.55f, 1.18f, 0.82f, 0.80f, 0.78f),
                    new PuffTuning(0.72f, 1.12f, 0.82f, 0.92f, 0.84f),
                    new PuffTuning(0.32f, 0.88f, 0.72f, 0.96f, 0.96f),
                    new PuffTuning(0.82f, 0.86f, 0.78f, 1.18f, 0.92f),
                    new BurstTuning(
                            true, 0.28f, 4, 8,
                            0.20f, 0.36f, 24, 40, 1, 3),
                    new FilamentTuning(
                            true, 0.92f, 4, 7, 1, 3,
                            0.014f, 3.2f, 1.35f, 0.92f, 0.90f),
                    new RenderTuning(
                            0.76f, 1.24f, 1.06f, 0.58f,
                            0.90f, 0.86f, 1.00f)
            );

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
