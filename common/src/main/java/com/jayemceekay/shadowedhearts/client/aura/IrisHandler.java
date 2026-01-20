package com.jayemceekay.shadowedhearts.client.aura;

import org.joml.Matrix4f;

public interface IrisHandler {
    boolean isShaderPackInUse();
    
    IrisRenderingSnapshot getIrisRenderingSnapshot();

    class IrisRenderingSnapshot {
        public final int diffuseTexture;
        public final int depthTexture;
        public final Matrix4f projectionMatrix;
        public final Matrix4f modelViewMatrix;

        public IrisRenderingSnapshot(int diffuseTexture, int depthTexture, Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
            this.diffuseTexture = diffuseTexture;
            this.depthTexture = depthTexture;
            this.projectionMatrix = projectionMatrix;
            this.modelViewMatrix = modelViewMatrix;
        }
    }
}
