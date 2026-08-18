package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.mixin.IrisRenderingPipelineAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;

public class IrisHandlerImpl implements IrisHandler {
    @Override
    public boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    @Override
    public boolean isShadowRenderActive() {
        return ShadowRenderer.ACTIVE;
    }

    @Override
    public IrisRenderingSnapshot getIrisRenderingSnapshot() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof IrisRenderingPipeline irisPipeline) {
            RenderTargets targets = ((IrisRenderingPipelineAccessor) irisPipeline).getRenderTargets();
            RenderTarget diffuseTarget = targets.getRenderTargetCount() > 0
                    ? targets.get(0)
                    : null;
            int diffuseTexture = diffuseTarget != null
                    ? diffuseTarget.getMainTexture()
                    : -1;
            int depthTexture = targets.getDepthTexture();
            int diffuseWidth = diffuseTarget != null
                    ? diffuseTarget.getWidth()
                    : 0;
            int diffuseHeight = diffuseTarget != null
                    ? diffuseTarget.getHeight()
                    : 0;
            // These are Iris' projection/depth viewport dimensions. Target 0
            // may have a shader-pack-specific scale override and therefore
            // must not define the geometry viewport.
            int renderWidth = targets.getCurrentWidth();
            int renderHeight = targets.getCurrentHeight();
            if ((renderWidth <= 0 || renderHeight <= 0)
                    && diffuseTarget != null) {
                renderWidth = diffuseTarget.getWidth();
                renderHeight = diffuseTarget.getHeight();
            }

            Matrix4f proj = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
            Matrix4f view = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
            
            return new IrisRenderingSnapshot(
                    diffuseTexture, depthTexture,
                    renderWidth, renderHeight,
                    diffuseWidth, diffuseHeight,
                    proj, view);
        }
        return null;
    }
}
