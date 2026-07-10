package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;

/**
 * Offscreen framebuffer wrapper for the snag capture beam density pipeline.
 */
public final class SnagDensityFBO {

    private static final DensityFboPipeline PIPELINE = new DensityFboPipeline(2, 0.50f);

    private SnagDensityFBO() {}

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
        return PIPELINE.composite(ModShaders.SNAG_BEAM_COMPOSITE, null);
    }

    public static void destroy() {
        PIPELINE.destroy();
    }
}
