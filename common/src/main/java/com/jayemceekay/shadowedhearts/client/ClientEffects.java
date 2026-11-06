package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.config.AuraRenderQuality;
import com.jayemceekay.shadowedhearts.config.ClientEffectPolicy;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

import static com.jayemceekay.shadowedhearts.config.ClientEffectPolicy.*;

/** Runtime access shared by native and shader-pack render paths. */
public final class ClientEffects {
    private ClientEffects() { }

    public static Level level(Family family) {
        var config = ShadowedHeartsConfigs.getInstance().getClientConfig();
        EffectOverride override = switch (family) {
            case SCAN_PULSE -> config.scanPulseEffects();
            case DARK_BALL -> config.darkBallEffects();
            case MOONWOUND -> config.moonwoundEffects();
            case AURA -> config.auraEffects();
            case AMBIENT -> config.ambientEffects();
        };
        return ClientEffectPolicy.resolve(config.accessibilityPreset(), family, override);
    }

    public static boolean auraVisible() {
        return ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()
                && level(Family.AURA) != Level.OFF;
    }

    public static float auraQuality() { return ClientEffectPolicy.auraQuality(level(Family.AURA)); }
    public static boolean auraPerformanceMode() {
        return ShadowedHeartsConfigs.getInstance().getClientConfig().auraRenderQuality()
                == AuraRenderQuality.PERFORMANCE;
    }

    public static float ambientDensity() { return ClientEffectPolicy.ambientDensity(level(Family.AMBIENT)); }
    public static boolean darkBallReplacement() { return ClientEffectPolicy.useDarkBallReplacement(level(Family.DARK_BALL)); }
    public static boolean scanWave() { return ClientEffectPolicy.drawScanWave(level(Family.SCAN_PULSE)); }
    public static boolean moonwoundMiasma() { return ClientEffectPolicy.moonwoundMiasma(level(Family.MOONWOUND)); }
}
