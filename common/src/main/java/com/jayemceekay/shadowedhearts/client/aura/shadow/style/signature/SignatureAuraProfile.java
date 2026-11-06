package com.jayemceekay.shadowedhearts.client.aura.shadow.style.signature;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyle;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.BurstTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.EmissionTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.FilamentTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.PuffTuning;
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile.RenderTuning;

/** Immutable authored tuning for the Signature aura. */
public final class SignatureAuraProfile {
    private static final PuffTuning IDENTITY_PUFF =
            new PuffTuning(1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

    public static final ShadowAuraStyleProfile INSTANCE =
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

    private SignatureAuraProfile() {
    }
}
