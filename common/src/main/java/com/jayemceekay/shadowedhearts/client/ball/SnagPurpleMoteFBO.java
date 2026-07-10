package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;

/**
 * Offscreen framebuffer wrapper for snag trail purple motes.
 */
public final class SnagPurpleMoteFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(1, 0.35f);

    private SnagPurpleMoteFBO() {}

    public static boolean beginDensityPass() {
        return PIPELINE.beginDensityPass();
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    public static int blur() {
        return PIPELINE.blur(ModShaders.PENUMBRA_BLUR);
    }

    public static boolean composite() {
        return PIPELINE.composite(ModShaders.SNAG_MOTE_COMPOSITE, null);
    }

    public static void destroy() {
        PIPELINE.destroy();
    }
}
