package com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.BurstTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.EmissionTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.FilamentTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.PuffTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.RenderTuning;

/** Soft violet plumes with pale motes; regional emission uses local posed dimensions. */
public final class ColosseumAuraProfile {
    public static final ShadowAuraStyleProfile INSTANCE =
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

    private ColosseumAuraProfile() {
    }
}
