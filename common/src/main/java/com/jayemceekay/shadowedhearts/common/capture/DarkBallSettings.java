package com.jayemceekay.shadowedhearts.common.capture;

import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.PresetCompatibilityConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

/** Effective server values needed by Dark Ball's tooltip. */
public record DarkBallSettings(int levelBoost, double heartGaugeMultiplier, boolean corruptionEnabled) {
    public DarkBallSettings(int levelBoost, double heartGaugeMultiplier) {
        this(levelBoost, heartGaugeMultiplier, true);
    }
    public DarkBallSettings {
        levelBoost = Math.max(0, levelBoost);
        heartGaugeMultiplier = Double.isFinite(heartGaugeMultiplier)
                ? Math.max(1.0, heartGaugeMultiplier) : 1.0;
    }

    public static DarkBallSettings serverCurrent() {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        return new DarkBallSettings(config.darkBallLevelBoost(), config.darkBallHeartGaugeMultiplier(),
                PresetCompatibilityConfig.INSTANCE.darkBallCorruptionEnabled());
    }
}
