package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.aura.ShadowAuraStyle;

/** Style-specific emitter ownership; particle tails retain their own short lifetime. */
final class LuminousMoteLifecycle {
    private static final long OBSERVATION_GRACE_TICKS = 12L;
    private final ShadowAuraStyle style;
    private final long startedTick;
    private final long signatureEndTick;
    private long lastObservedTick;

    LuminousMoteLifecycle(ShadowAuraStyle style, long now, boolean shadowShard) {
        this.style = style;
        this.startedTick = now;
        this.lastObservedTick = now;
        this.signatureEndTick = now + (shadowShard ? Integer.MAX_VALUE / 2L : 60L);
    }

    ShadowAuraStyle style() { return style; }

    void observe(long now) {
        if (style == ShadowAuraStyle.COLOSSEUM) lastObservedTick = now;
    }

    boolean expired(long now, ShadowAuraStyle currentStyle) {
        if (currentStyle != style || style == ShadowAuraStyle.XD_FAITHFUL || now < startedTick) return true;
        return style == ShadowAuraStyle.COLOSSEUM
                ? now - lastObservedTick > OBSERVATION_GRACE_TICKS : now >= signatureEndTick;
    }
}
