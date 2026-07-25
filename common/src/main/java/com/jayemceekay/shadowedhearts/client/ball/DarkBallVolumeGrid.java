package com.jayemceekay.shadowedhearts.client.ball;

/**
 * Capture-local voxel grid dimensions for the Dark Ball source volume.
 */
public final class DarkBallVolumeGrid {

    /** Number of simulated slices along the capture volume's local X axis. */
    public static final int X_SLICES = 96;
    /** Width and height, in voxels, of each X slice. */
    public static final int SLICE_SIZE = 64;

    private DarkBallVolumeGrid() {
    }
}
