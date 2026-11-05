package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

record DarkBallVolumeBuildResult(
        Vec3 root,
        Vec3 axis,
        Vec3 side,
        Vec3 up,
        Vec3 rootOffsetFromPokemonCenter,
        float captureLength,
        float bodyRadius,
        float radius,
        float[] signedDistanceField,
        float[] localThicknessField,
        float sdfVoxelX,
        float sdfVoxelYz
) {
}
