package com.jayemceekay.shadowedhearts.client.ball;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Matched world-render transform used by every Dark Ball geometry submission.
 *
 * <p>Vanilla loader callbacks historically supplied camera-relative geometry
 * with an explicitly rebuilt inverse-camera rotation. Iris instead owns a
 * view/projection pair which may include shader-pack camera transforms. Mixing
 * one member of each pair can turn an otherwise bounded surfel footprint into
 * a screen-sized slab. An installed context therefore always supplies both
 * matrices together and is scoped to one serial Dark Ball transaction.</p>
 */
final class DarkBallRenderContext {
    private static Context active;

    private DarkBallRenderContext() {
    }

    static Scope install(Matrix4f modelView, Matrix4f projection) {
        if (!isUsableModelView(modelView)
                || !isUsablePerspective(projection)) {
            throw new IllegalArgumentException(
                    "invalid Dark Ball world-render transform");
        }
        Context previous = active;
        active = new Context(
                new Matrix4f(modelView),
                new Matrix4f(projection));
        return new Scope(previous);
    }

    static Matrix4f modelView(Camera camera) {
        Context current = active;
        if (current != null) {
            return new Matrix4f(current.modelView());
        }
        Quaternionf viewRotation =
                new Quaternionf(camera.rotation()).conjugate();
        return new Matrix4f().rotation(viewRotation);
    }

    static Matrix4f projection() {
        Context current = active;
        return current != null
                ? new Matrix4f(current.projection())
                : new Matrix4f(RenderSystem.getProjectionMatrix());
    }

    static boolean hasInstalledTransform() {
        return active != null;
    }

    static boolean isUsableIrisFrame(Matrix4f modelView,
                                     Matrix4f projection,
                                     int renderWidth,
                                     int renderHeight) {
        return renderWidth > 0
                && renderHeight > 0
                && renderWidth <= 32768
                && renderHeight <= 32768
                && isUsableModelView(modelView)
                && isUsablePerspective(projection)
                && projectionMatchesTargetAspect(
                projection, renderWidth, renderHeight);
    }

    static boolean isUsableModelView(Matrix4f matrix) {
        return isFinite(matrix)
                && finiteNonZeroDeterminant(matrix);
    }

    static boolean isUsablePerspective(Matrix4f matrix) {
        if (!isFinite(matrix)
                || !finiteNonZeroDeterminant(matrix)) {
            return false;
        }
        float horizontalScale = Math.abs(matrix.m00());
        float verticalScale = Math.abs(matrix.m11());
        return horizontalScale >= 0.0001f
                && horizontalScale <= 10000.0f
                && verticalScale >= 0.0001f
                && verticalScale <= 10000.0f
                // A world perspective matrix couples view-space Z into W.
                // Identity/orthographic/stale GUI projections must never be
                // allowed to expand camera-relative surfel footprints.
                && Math.abs(matrix.m23()) >= 0.0001f
                && Math.abs(matrix.m33()) <= 0.01f;
    }

    private static boolean projectionMatchesTargetAspect(
            Matrix4f projection,
            int renderWidth,
            int renderHeight) {
        float projectionAspect = Math.abs(
                projection.m11() / projection.m00());
        float targetAspect = (float) renderWidth / (float) renderHeight;
        float relativeError = Math.abs(projectionAspect - targetAspect)
                / Math.max(targetAspect, 0.0001f);
        // Allow shader-pack jitter and mild asymmetric projection, but reject
        // a stale viewport/projection pairing before it stretches the shared
        // scratch transaction outside the proxy silhouette.
        return Float.isFinite(relativeError) && relativeError <= 0.20f;
    }

    private static boolean finiteNonZeroDeterminant(Matrix4f matrix) {
        float determinant = matrix.determinant();
        return Float.isFinite(determinant)
                && Math.abs(determinant) >= 1.0e-12f;
    }

    private static boolean isFinite(Matrix4f matrix) {
        return matrix != null
                && finite(matrix.m00()) && finite(matrix.m01())
                && finite(matrix.m02()) && finite(matrix.m03())
                && finite(matrix.m10()) && finite(matrix.m11())
                && finite(matrix.m12()) && finite(matrix.m13())
                && finite(matrix.m20()) && finite(matrix.m21())
                && finite(matrix.m22()) && finite(matrix.m23())
                && finite(matrix.m30()) && finite(matrix.m31())
                && finite(matrix.m32()) && finite(matrix.m33());
    }

    private static boolean finite(float value) {
        return Float.isFinite(value) && Math.abs(value) <= 1.0e20f;
    }

    private record Context(Matrix4f modelView, Matrix4f projection) {
    }

    static final class Scope implements AutoCloseable {
        private final Context previous;
        private boolean closed;

        private Scope(Context previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                active = previous;
                closed = true;
            }
        }
    }
}
