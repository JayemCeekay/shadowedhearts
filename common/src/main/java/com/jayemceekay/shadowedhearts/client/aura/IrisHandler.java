package com.jayemceekay.shadowedhearts.client.aura;

import org.joml.Matrix4f;

public interface IrisHandler {
    boolean isShaderPackInUse();

    boolean isShadowRenderActive();
    
    IrisRenderingSnapshot getIrisRenderingSnapshot();

    class IrisRenderingSnapshot {
        public final int diffuseTexture;
        public final int depthTexture;
        public final int renderWidth;
        public final int renderHeight;
        public final int diffuseWidth;
        public final int diffuseHeight;
        public final Matrix4f projectionMatrix;
        public final Matrix4f modelViewMatrix;

        public IrisRenderingSnapshot(int diffuseTexture, int depthTexture,
                                     int renderWidth, int renderHeight,
                                     int diffuseWidth, int diffuseHeight,
                                     Matrix4f projectionMatrix,
                                     Matrix4f modelViewMatrix) {
            this.diffuseTexture = diffuseTexture;
            this.depthTexture = depthTexture;
            this.renderWidth = renderWidth;
            this.renderHeight = renderHeight;
            this.diffuseWidth = diffuseWidth;
            this.diffuseHeight = diffuseHeight;
            // CapturedRenderingState owns mutable matrices. A snapshot must
            // remain a coherent pair even if Iris advances its frame state
            // before the consumer submits geometry.
            this.projectionMatrix = new Matrix4f(projectionMatrix);
            this.modelViewMatrix = new Matrix4f(modelViewMatrix);
        }
    }
}
