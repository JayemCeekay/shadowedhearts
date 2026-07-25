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
        float[] coreField,
        float[] envelopeField,
        float[] surfaceDepthField,
        float[] signedDistanceField,
        float[] cohesiveSignedDistanceField,
        float[] sdfGradientX,
        float[] sdfGradientY,
        float[] sdfGradientZ,
        float[] localThicknessField,
        float sdfVoxelX,
        float sdfVoxelYz
) {
}
