package com.jayemceekay.shadowedhearts.snag;

import net.minecraft.server.level.ServerPlayer;

/**
 * Simplified server-side handler for snag attempts. The actual hook into Cobblemon's
 * battle capture resolution will be added later; this provides the math and API.
 */
public final class SnagCaptureHandler {
    private SnagCaptureHandler() {}

    /** Compute final snag chance using the design multipliers. Values are placeholders for now. */
    public static double computeSnagChance(ServerPlayer player, boolean isShadow, int shadowLevel,
                                           int snagResistanceStage, boolean deviceArmed,
                                           double baseCatchChance, double ballModifier) {
        if (!SnagConfig.ENABLED) return 0.0;
        if (!isShadow) return 0.0;

        double C_base = baseCatchChance; // expected in [0..1]
        double M_ball = ballModifier;    // caller determines based on ball type
        double M_shadow = 1.0 / (0.85 + 0.15 * Math.max(1, shadowLevel));
        double M_resist = Math.pow(0.85, Math.max(0, snagResistanceStage));
        double M_device = deviceArmed ? SnagConfig.DEVICE_ARMED : 1.0;
        double M_zone = 1.0; // zone gating TBD

        double C_final = C_base * M_ball * M_shadow * M_resist * M_device * M_zone;
        if (C_final < 0) C_final = 0;
        if (C_final > 1) C_final = 1;
        return C_final;
    }
}
