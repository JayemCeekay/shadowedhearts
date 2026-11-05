package com.jayemceekay.shadowedhearts.client.moonwound;

import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;

/** Conservative homogeneous clipping, without dividing off-screen vertices by camera depth. */
public final class MoonwoundFrustum {
    private final FrustumIntersection frustum;

    public MoonwoundFrustum(Matrix4fc clipFromCoordinates) {
        frustum = clipFromCoordinates != null && clipFromCoordinates.isFinite()
                && Math.abs(clipFromCoordinates.determinant()) > 1.0e-12f
                ? new FrustumIntersection(clipFromCoordinates) : null;
    }

    /** Bounds must use the same coordinate space as the constructor's matrix. Unknown bounds stay visible. */
    public boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (frustum == null || !finite(minX) || !finite(minY) || !finite(minZ)
                || !finite(maxX) || !finite(maxY) || !finite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) return true;
        // Small outward padding keeps rounding from removing a quad touching a clip plane.
        return frustum.testAab((float) (minX - 0.01), (float) (minY - 0.01), (float) (minZ - 0.01),
                (float) (maxX + 0.01), (float) (maxY + 0.01), (float) (maxZ + 0.01));
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && Math.abs(value) < Float.MAX_VALUE;
    }
}
