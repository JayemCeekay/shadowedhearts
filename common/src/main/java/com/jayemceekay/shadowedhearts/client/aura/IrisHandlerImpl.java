package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.mixin.IrisRenderingPipelineAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;

public class IrisHandlerImpl implements IrisHandler {
    @Override
    public boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    @Override
    public IrisRenderingSnapshot getIrisRenderingSnapshot() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof IrisRenderingPipeline irisPipeline) {
            RenderTargets targets = ((IrisRenderingPipelineAccessor) irisPipeline).getRenderTargets();
            int diffuseTexture = -1;
            if (targets.getRenderTargetCount() > 0 && targets.get(0) != null) {
                diffuseTexture = targets.get(0).getMainTexture();
            }
            int depthTexture = targets.getDepthTexture();

            Matrix4f proj = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
            Matrix4f view = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
            
            return new IrisRenderingSnapshot(diffuseTexture, depthTexture, proj, view);
        }
        return null;
    }
}
