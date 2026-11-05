package com.jayemceekay.shadowedhearts.client.voidshadow;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.regex.Pattern;

/** Eye patches in the animated locator's complete local frame, not camera-facing sprites. */
public final class VoidShadowEyeGeometry {
    private static final float HALF_SIZE = 1.5f / 16f;
    private static final float SURFACE_OFFSET = 1f / 512f;
    private static final Pattern EYE_LOCATOR = Pattern.compile("eye(?:[0-9]+|_(?:left|right)[0-9]*)?", Pattern.CASE_INSENSITIVE);

    private VoidShadowEyeGeometry() { }

    public static boolean isEyeLocator(String name) {
        return name != null && EYE_LOCATOR.matcher(name).matches();
    }

    public static EyeQuad quad(Matrix4fc locator) {
        if (locator == null) return null;
        Point bottomLeft = point(locator, -HALF_SIZE, -HALF_SIZE);
        Point bottomRight = point(locator, HALF_SIZE, -HALF_SIZE);
        Point topRight = point(locator, HALF_SIZE, HALF_SIZE);
        Point topLeft = point(locator, -HALF_SIZE, HALF_SIZE);
        if (!bottomLeft.finite() || !bottomRight.finite() || !topRight.finite() || !topLeft.finite()) return null;
        Vector3f right = bottomRight.vector().sub(bottomLeft.vector());
        Vector3f up = topLeft.vector().sub(bottomLeft.vector());
        float areaSquared = right.cross(up).lengthSquared();
        if (!(areaSquared > 0) || !Float.isFinite(areaSquared)) return null;
        return new EyeQuad(bottomLeft, bottomRight, topRight, topLeft);
    }

    private static Point point(Matrix4fc locator, float x, float y) {
        // Cobblemon's eye forward is local -Z. Only the added patch is offset;
        // the copied model keeps its native seam-preserving vertex positions.
        Vector3f position = locator.transformPosition(new Vector3f(x, y, -SURFACE_OFFSET));
        return new Point(position.x, position.y, position.z);
    }

    public record Point(float x, float y, float z) {
        public Vector3f vector() { return new Vector3f(x, y, z); }
        private boolean finite() { return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z); }
    }

    public record EyeQuad(Point bottomLeft, Point bottomRight, Point topRight, Point topLeft) {
        /** Matches the emitted -Z winding even under combined nonuniform bone scales and rotations. */
        public Vector3f normal() {
            return topLeft.vector().sub(bottomLeft.vector())
                    .cross(bottomRight.vector().sub(bottomLeft.vector())).normalize();
        }
    }
}
