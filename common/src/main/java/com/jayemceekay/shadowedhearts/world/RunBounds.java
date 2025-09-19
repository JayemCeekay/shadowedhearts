package com.jayemceekay.shadowedhearts.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/** Per-run reserved bounds within the Missions dimension. */
public record RunBounds(BlockPos origin, Vec3i size) {}
