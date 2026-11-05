package com.jayemceekay.shadowedhearts.client.sound;

/** Short gain ramps follow work-state edges; active search phases keep the same loop and gain. */
public final class ShadowSignalSearchAudioEnvelope {
    public static final int ATTACK_TICKS = 4;
    public static final int RELEASE_TICKS = 2;
    private boolean processing = true;
    private int ticks;
    private float gain, from;

    public void setProcessing(boolean processing) {
        if (this.processing == processing) return;
        this.processing = processing;
        from = gain;
        ticks = 0;
    }

    public float tick() {
        int duration = processing ? ATTACK_TICKS : RELEASE_TICKS;
        ticks = Math.min(duration, ticks + 1);
        float t = ticks / (float) duration;
        float blend = t * t * (3 - 2 * t);
        gain = from + ((processing ? 1 : 0) - from) * blend;
        return gain;
    }

    public boolean finished() { return !processing && gain <= 0; }
}
