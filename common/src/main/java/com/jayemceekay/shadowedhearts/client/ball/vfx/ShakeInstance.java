package com.jayemceekay.shadowedhearts.client.ball.vfx;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity.CaptureState;

import java.util.ArrayList;
import java.util.List;

/** Per-ball tracking state for the shake VFX phase. */
public final class ShakeInstance {
    public final int ballId;
    public boolean lastShakeValue;
    public CaptureState lastCaptureState = CaptureState.SHAKE;
    public float seamGlowStrength = 0f;
    public float resultAge = -1f;  // >= 0 once CAPTURED or BROKEN_FREE fires
    public boolean isSuccess = false;
    public boolean spawnedResultBurst = false;
    public final List<ShakeSpark> sparks = new ArrayList<>();
    public final List<GroundPulse> pulses = new ArrayList<>();

    public ShakeInstance(int ballId) {
        this.ballId = ballId;
    }
}
