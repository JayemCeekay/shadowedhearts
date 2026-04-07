package com.jayemceekay.shadowedhearts.common.tracking;

import net.minecraft.core.BlockPos;

/**
 * A waypoint in the world used to guide the Aura Reader trail.
 * Each node carries its position and the type of micro-event the player
 * must complete at that location to reveal the next trail segment.
 */
public record TrailNode(BlockPos pos, NodeEventType eventType) {

    /**
     * Legacy constructor for backward compatibility — defaults to CALIBRATION.
     */
    public TrailNode(BlockPos pos) {
        this(pos, NodeEventType.CALIBRATION);
    }
}
