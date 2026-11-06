package com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum;

import net.minecraft.util.Mth;

/** Lifetime-dependent smoke opacity and packed age shared by world and preview rendering. */
public final class ColosseumSmokeAppearance {
    private ColosseumSmokeAppearance() {
    }

    /** Low G is per-puff age, while G >= .75 remains reserved for Colosseum preview motes. */
    public static float ageChannel(float ageTicks, float lifetimeTicks) {
        return 0.5f * life(ageTicks, lifetimeTicks);
    }

    /** Color has time to turn purple before the entire puff loses visibility. */
    public static float opacity(float ageTicks, float lifetimeTicks) {
        float life = life(ageTicks, lifetimeTicks);
        return smoothstep(0.0f, 0.18f, life) * (1.0f - smoothstep(0.76f, 1.0f, life));
    }

    private static float life(float ageTicks, float lifetimeTicks) {
        if (!Float.isFinite(ageTicks) || !Float.isFinite(lifetimeTicks) || lifetimeTicks <= 0) return 1;
        return Mth.clamp(ageTicks / lifetimeTicks, 0, 1);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
