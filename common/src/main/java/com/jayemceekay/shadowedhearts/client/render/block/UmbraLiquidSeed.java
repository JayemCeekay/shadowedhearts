package com.jayemceekay.shadowedhearts.client.render.block;

/** Stable visual randomness without consuming or reseeding the world's random source. */
public final class UmbraLiquidSeed {
    private UmbraLiquidSeed() { }

    public static float at(int x, int y, int z) {
        long mixed = 0x9E3779B97F4A7C15L
                + (long) x * 0x632BE59BD9B4E019L
                + (long) y * 0x9E3779B97F4A7C15L
                + (long) z * 0xC6BC279692B5CC83L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        // Exactly representable float values survive the packed UV attribute unchanged.
        return (mixed >>> 40) * 0x1.0p-24F;
    }
}
