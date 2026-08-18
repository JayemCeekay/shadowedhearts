package com.jayemceekay.shadowedhearts.client.aura;

import net.minecraft.util.FastColor;

/**
 * Shared visual language for the Aura Reader field HUD.
 *
 * <p>The values intentionally stay independent of any borrowed Cobblemon
 * texture so procedural panels and markers can be replaced or tuned without
 * changing the client-state contract.</p>
 */
public final class AuraReaderHudTheme {
    public static final int CYAN = 0xDD42E8F3;
    public static final int CYAN_BRIGHT = 0xF064F5FF;
    public static final int CYAN_DIM = 0x7A34D4DE;
    public static final int PANEL_BACKGROUND = 0xB20A2630;
    public static final int PANEL_BACKGROUND_SOFT = 0x8010212A;

    public static final int TEXT_PRIMARY = 0xFFEAFBFF;
    public static final int TEXT_SECONDARY = 0xFFBFEAF1;
    public static final int TEXT_WARNING = 0xFFE7C15B;
    public static final int TEXT_DANGER = 0xFFFF6D82;

    public static final int PASSIVE = 0xDD42E8F3;
    public static final int PULSE = 0xDDE4C15B;
    public static final int TRACKED = 0xDDFF6D82;
    public static final int LOCKED = 0xF2FFFFFF;
    public static final int SHADOW_MARKER = 0xE6BD73FF;
    public static final int SHADOW_MARKER_BACKGROUND = 0xA044185F;

    private AuraReaderHudTheme() {
    }

    public static int multiplyAlpha(int color, float multiplier) {
        float clamped = Math.max(0.0f, Math.min(1.0f, multiplier));
        int alpha = Math.round(FastColor.ARGB32.alpha(color) * clamped);
        return FastColor.ARGB32.color(
                alpha,
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color)
        );
    }
}
