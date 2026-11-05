package com.jayemceekay.shadowedhearts.client.moonwound;

import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/** Keeps the effect's apparent texel density tied to the portal as its projected size changes. */
public final class MoonwoundPixelationScale {
    public static final int REFERENCE_WIDTH = 256;
    private static final double MAX_EXACT_CELL_INDEX = 0x1.0p52 - 1;

    private MoonwoundPixelationScale() { }

    /**
     * Returns a continuous screen-pixel span for the original portal's projected width.
     * Preset four retains 64 logical texels across that width. Smooth and unsafe
     * projections use one pixel so callers can fall back to the full-resolution effect.
     */
    public static float pixelSize(int presetSize, double projectedWidthPixels) {
        return (float) continuousSpan(presetSize, projectedWidthPixels);
    }

    /** First-cell boundaries are viewport-local; the phase remains anchored to the portal. */
    public record Grid(float cellSize, float originX, float originY, int width, int height, float blend) { }

    /** Null means full-resolution rendering is preferable or the projection cannot produce a safe grid. */
    public static Grid grid(int presetSize, double projectedWidthPixels, double anchorX, double anchorY,
                            int viewportWidth, int viewportHeight) {
        double span = continuousSpan(presetSize, projectedWidthPixels);
        if (span <= 1 || !Double.isFinite(anchorX) || !Double.isFinite(anchorY)
                || viewportWidth <= 0 || viewportHeight <= 0
                || viewportWidth > Integer.MAX_VALUE - 4 || viewportHeight > Integer.MAX_VALUE - 4) return null;
        double firstX = Math.floor(-anchorX / span) - 1;
        double firstY = Math.floor(-anchorY / span) - 1;
        if (!Double.isFinite(firstX) || !Double.isFinite(firstY)
                || Math.abs(firstX) > MAX_EXACT_CELL_INDEX || Math.abs(firstY) > MAX_EXACT_CELL_INDEX) return null;
        double originX = anchorX + firstX * span, originY = anchorY + firstY * span;
        double width = Math.ceil((viewportWidth - anchorX) / span) - firstX + 1;
        double height = Math.ceil((viewportHeight - anchorY) / span) - firstY + 1;
        if (!Double.isFinite(width) || !Double.isFinite(height)
                || width < 1 || height < 1 || width > viewportWidth + 4.0 || height > viewportHeight + 4.0)
            return null;
        float cellSize = (float) span, x = (float) originX, y = (float) originY;
        if (!Float.isFinite(cellSize) || cellSize <= 1 || !Float.isFinite(x) || !Float.isFinite(y)) return null;
        double t = Math.max(0, Math.min(1, span - 1));
        return new Grid(cellSize, x, y, (int) width, (int) height, (float) (t * t * (3 - 2 * t)));
    }

    private static double continuousSpan(int presetSize, double projectedWidthPixels) {
        if (presetSize <= 1 || !Double.isFinite(projectedWidthPixels) || projectedWidthPixels <= 0) return 1;
        double span = presetSize * (projectedWidthPixels / REFERENCE_WIDTH);
        if (!Double.isFinite(span) || span > Float.MAX_VALUE || span < Float.MIN_VALUE) return 1;
        return span;
    }

    /** Projects the portal anchor into viewport-local pixels, or null when it is behind or invalid. */
    public static Vector2f projectedCenter(Matrix4fc clipFromCoordinates, Vector3fc center,
                                          int viewportWidth, int viewportHeight) {
        if (clipFromCoordinates == null || center == null || !clipFromCoordinates.isFinite() || !center.isFinite()
                || viewportWidth <= 0 || viewportHeight <= 0) return null;
        Vector4f clip = new Vector4f(center, 1);
        clipFromCoordinates.transform(clip);
        if (!clip.isFinite() || clip.w <= 1.0e-5) return null;
        double x = (clip.x / (double) clip.w + 1) * viewportWidth * 0.5;
        double y = (clip.y / (double) clip.w + 1) * viewportHeight * 0.5;
        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > Float.MAX_VALUE || Math.abs(y) > Float.MAX_VALUE)
            return null;
        return new Vector2f((float) x, (float) y);
    }

    /**
     * Nominal on-axis width for texel sizing, independent of camera yaw and portal screen position.
     * Pass only the projection matrix and the Euclidean camera-to-portal distance. Horizontal
     * projection scale times viewport width supplies focal pixels; ordinary aspect-correct
     * projections give the same density on both axes. Orthographic width does not use distance.
     */
    public static double nominalWidth(Matrix4fc projection, double cameraDistance, double worldWidth,
                                      int viewportWidth, int viewportHeight) {
        if (projection == null || !projection.isFinite() || !Double.isFinite(cameraDistance)
                || cameraDistance <= 0 || !Double.isFinite(worldWidth) || worldWidth <= 0
                || viewportWidth <= 0 || viewportHeight <= 0
                || projection.m00() == 0 || projection.m11() == 0
                || Math.abs(projection.m03()) > 1.0e-6 || Math.abs(projection.m13()) > 1.0e-6) return 0;
        double perspectiveW = Math.abs(projection.m23()), constantW = Math.abs(projection.m33());
        double width;
        if (perspectiveW > 1.0e-6 && constantW <= 1.0e-6) {
            double focalPixels = Math.abs(projection.m00() / perspectiveW) * viewportWidth * 0.5;
            width = focalPixels * (worldWidth / cameraDistance);
        } else if (perspectiveW <= 1.0e-6 && constantW > 1.0e-6) {
            width = Math.abs(projection.m00() / constantW) * viewportWidth * 0.5 * worldWidth;
        } else {
            return 0;
        }
        return Double.isFinite(width) && width > 0 ? width : 0;
    }

    /**
     * Projects the original portal's horizontal endpoints using the caller's coordinate space.
     * A span crossing the camera plane is conservatively treated as infinitely near.
     */
    public static double projectedWidth(Matrix4fc clipFromCoordinates, Vector3fc center, Vector3fc right,
                                        double worldWidth, int viewportWidth, int viewportHeight) {
        if (clipFromCoordinates == null || center == null || right == null
                || !clipFromCoordinates.isFinite() || !center.isFinite() || !right.isFinite()
                || !Double.isFinite(worldWidth) || worldWidth <= 0 || viewportWidth <= 0 || viewportHeight <= 0)
            return 0;
        double halfWidth = worldWidth * 0.5;
        Vector4f left = new Vector4f((float) (center.x() - right.x() * halfWidth),
                (float) (center.y() - right.y() * halfWidth), (float) (center.z() - right.z() * halfWidth), 1);
        Vector4f rightEdge = new Vector4f((float) (center.x() + right.x() * halfWidth),
                (float) (center.y() + right.y() * halfWidth), (float) (center.z() + right.z() * halfWidth), 1);
        if (!left.isFinite() || !rightEdge.isFinite()) return 0;
        clipFromCoordinates.transform(left);
        clipFromCoordinates.transform(rightEdge);
        if (!left.isFinite() || !rightEdge.isFinite()) return 0;
        boolean leftBehind = left.w <= 1.0e-5, rightBehind = rightEdge.w <= 1.0e-5;
        if (leftBehind && rightBehind) return 0;
        if (leftBehind || rightBehind) return Double.POSITIVE_INFINITY;
        double dx = (rightEdge.x / (double) rightEdge.w - left.x / (double) left.w) * viewportWidth * 0.5;
        double dy = (rightEdge.y / (double) rightEdge.w - left.y / (double) left.w) * viewportHeight * 0.5;
        double width = Math.hypot(dx, dy);
        return Double.isFinite(width) ? width : 0;
    }
}
