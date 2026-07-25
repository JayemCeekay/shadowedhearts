package com.jayemceekay.shadowedhearts.client.ball;

import java.util.Locale;

/**
 * Runtime budgets for the analytical Dark Ball volume.
 *
 * <p>The source SDF remains at capture resolution. These values control the
 * narrow-band shell cadence/width, localized vortex count, and base sampling
 * budget. The preferred body renderer scales that budget for its current grid;
 * the independent siphon renderer consumes it directly.
 */
enum DarkBallVfxQuality {
    LOW(2.0f, 20.0f, 10, 2),
    MEDIUM(3.0f, 30.0f, 16, 3),
    HIGH(4.5f, 45.0f, 24, 4);

    private final float shellWidthVoxels;
    private final float shellHz;
    private final int raymarchSamples;
    private final int vortexCellCount;

    DarkBallVfxQuality(float shellWidthVoxels, float shellHz, int raymarchSamples,
                       int vortexCellCount) {
        this.shellWidthVoxels = shellWidthVoxels;
        this.shellHz = shellHz;
        this.raymarchSamples = raymarchSamples;
        this.vortexCellCount = vortexCellCount;
    }

    float shellWidthVoxels() {
        return shellWidthVoxels;
    }

    float shellStepSeconds() {
        return 1.0f / shellHz;
    }

    int raymarchSamples() {
        return raymarchSamples;
    }

    int vortexCells() {
        return vortexCellCount;
    }

    static DarkBallVfxQuality parse(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MEDIUM;
        }
    }
}
