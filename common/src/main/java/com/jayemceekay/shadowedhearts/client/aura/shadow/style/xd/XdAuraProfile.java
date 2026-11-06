package com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.BurstTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.EmissionTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.FilamentTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.PuffTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.RenderTuning;

/** Continuous curling ribbons over a light regional haze, retaining legacy burst tuning. */
public final class XdAuraProfile {
    public static final ShadowAuraStyleProfile INSTANCE =
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

    private XdAuraProfile() {
    }
}
