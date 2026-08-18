package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallRenderContextTest {
    @Test
    void acceptsFinitePerspectiveWorldFrame() {
        Matrix4f view = new Matrix4f()
                .rotateX(0.15f)
                .rotateY(-0.35f);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                3440.0f / 1440.0f,
                0.05f,
                1024.0f);

        assertTrue(DarkBallRenderContext.isUsableIrisFrame(
                view, projection, 3440, 1440));
    }

    @Test
    void rejectsIdentityAndNonFiniteProjection() {
        Matrix4f view = new Matrix4f();

        assertFalse(DarkBallRenderContext.isUsableIrisFrame(
                view, new Matrix4f(), 1920, 1080));
        assertFalse(DarkBallRenderContext.isUsableIrisFrame(
                view,
                new Matrix4f().perspective(
                        (float) Math.toRadians(70.0),
                        16.0f / 9.0f,
                        0.05f,
                        1024.0f).m00(Float.NaN),
                1920,
                1080));
    }

    @Test
    void rejectsInvalidViewportOrSingularView() {
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                16.0f / 9.0f,
                0.05f,
                1024.0f);

        assertFalse(DarkBallRenderContext.isUsableIrisFrame(
                new Matrix4f(), projection, 0, 1080));
        assertFalse(DarkBallRenderContext.isUsableIrisFrame(
                new Matrix4f().zero(), projection, 1920, 1080));
    }

    @Test
    void rejectsProjectionWhoseAspectGrosslyDisagreesWithTarget() {
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                4.0f / 3.0f,
                0.05f,
                1024.0f);

        assertFalse(DarkBallRenderContext.isUsableIrisFrame(
                new Matrix4f(), projection, 3440, 1440));
    }
}
