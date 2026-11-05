package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Keeps the captured Iris world view intact across Snag's auxiliary passes. */
final class SnagRenderContext {
    private static Matrix4f activeModelView;

    private SnagRenderContext() { }

    static Scope install(Matrix4f modelView) {
        Matrix4f previous = activeModelView;
        activeModelView = new Matrix4f(modelView);
        return new Scope(previous);
    }

    static Matrix4f modelView(Camera camera) {
        return activeModelView != null ? new Matrix4f(activeModelView)
                : new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
    }

    static final class Scope implements AutoCloseable {
        private final Matrix4f previous;

        private Scope(Matrix4f previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            activeModelView = previous;
        }
    }
}
