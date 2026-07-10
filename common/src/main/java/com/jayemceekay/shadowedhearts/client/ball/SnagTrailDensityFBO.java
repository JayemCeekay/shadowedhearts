package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;

/**
 * Offscreen framebuffer wrapper for the snag trail orange smoke density pipeline.
 */
public final class SnagTrailDensityFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(2, 0.50f);

    private SnagTrailDensityFBO() {}

    public static boolean beginDensityPass() {
        return PIPELINE.beginDensityPass();
    }

    public static void endDensityPass() {
        PIPELINE.endDensityPass();
    }

    public static int getDensityTextureId() {
        return PIPELINE.getDensityTextureId();
    }

    public static int blur() {
        return PIPELINE.blur(ModShaders.PENUMBRA_BLUR);
    }

    public static boolean composite() {
        return PIPELINE.composite(ModShaders.SNAG_TRAIL_COMPOSITE, null);
    }

    public static void destroy() {
        PIPELINE.destroy();
    }
}
