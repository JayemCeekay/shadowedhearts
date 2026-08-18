package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.util.Mth;

/**
 * Shared deformation clock and developer diagnostics for the surfel and
 * composite paths.
 *
 * <p>These values are owned by the surviving surfel/composite pipeline rather
 * than by any particular renderer, so capture-relative animation stays stable
 * across resource reloads and direct-draw recovery.</p>
 */
final class DarkBallDeformationSettings {
    static final int SPIKE_INDENT_AMPLITUDE_DEBUG_MODE = 13;
    private static final float DEFORMATION_TIME_SCALE = 3.2f;
    private static final int SYSTEM_PROPERTY_DEBUG_MODE = Mth.clamp(
            Integer.getInteger(
                    "shadowedhearts.darkBallDeformationDebug",
                    0),
            0,
            SPIKE_INDENT_AMPLITUDE_DEBUG_MODE);

    private DarkBallDeformationSettings() {
    }

    static int debugMode() {
        if (ShadowedHeartsConfigs.getInstance()
                .getClientConfig()
                .darkBallVisualizeSpikeIndentAmplitude()) {
            return SPIKE_INDENT_AMPLITUDE_DEBUG_MODE;
        }
        return SYSTEM_PROPERTY_DEBUG_MODE;
    }

    static boolean spikeIndentAmplitudeDebugEnabled() {
        return debugMode() == SPIKE_INDENT_AMPLITUDE_DEBUG_MODE;
    }

    static float timeAt(float captureAge) {
        return Math.max(captureAge, 0.0f)
                * DEFORMATION_TIME_SCALE;
    }
}
