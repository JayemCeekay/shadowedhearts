package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;
import java.util.function.Consumer;

/**
 * Shared half-resolution density FBO pipeline for splat -> blur -> composite effects.
 */
public final class DensityFboPipeline {

    private final int blurIterations;
    private final float blurRadius;

    private RenderTarget densityTarget;
    private RenderTarget blurTempTarget;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int restoreFramebufferId = -1;
    private int restoreViewportX;
    private int restoreViewportY;
    private int restoreViewportW;
    private int restoreViewportH;

    public DensityFboPipeline(int blurIterations, float blurRadius) {
        this.blurIterations = blurIterations;
        this.blurRadius = blurRadius;
    }

    public boolean beginDensityPass() {
        return beginDensityPass(true, true);
    }

    public boolean beginDensityPass(boolean clearTarget, boolean copyMainDepth) {
        boolean recreatedTargets = ensureTargets();
        if (densityTarget == null) return false;

        captureCurrentRenderState();

        if (clearTarget || recreatedTargets) {
            densityTarget.clear(Minecraft.ON_OSX);
        }
        if (copyMainDepth || clearTarget || recreatedTargets) {
            blitMainDepthToDensity();
        }

        densityTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);

        return true;
    }

    public void endDensityPass() {
        restoreCapturedRenderState();
    }

    public int getDensityTextureId() {
        return densityTarget != null ? densityTarget.getColorTextureId() : 0;
    }

    public int blur(ShaderInstance blurShader) {
        if (blurShader == null || densityTarget == null || blurTempTarget == null) return 0;

        int prevFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        int prevViewportX = prevViewport.get(0);
        int prevViewportY = prevViewport.get(1);
        int prevViewportW = prevViewport.get(2);
        int prevViewportH = prevViewport.get(3);

        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        float texelW = 1.0f / lastWidth;
        float texelH = 1.0f / lastHeight;

        Uniform uDir = blurShader.getUniform("Direction");
        Uniform uRad = blurShader.getUniform("BlurRadius");

        int iterationsRan = 0;
        for (int i = 0; i < blurIterations; i++) {
            blurTempTarget.clear(Minecraft.ON_OSX);
            blurTempTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId());

            if (uDir != null) uDir.set(texelW, 0.0f);
            if (uRad != null) uRad.set(blurRadius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();

            densityTarget.clear(Minecraft.ON_OSX);
            densityTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, blurTempTarget.getColorTextureId());

            if (uDir != null) uDir.set(0.0f, texelH);
            if (uRad != null) uRad.set(blurRadius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();

            iterationsRan++;
        }

        blurShader.clear();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFramebufferId);
        RenderSystem.viewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);

        RenderSystem.getModelViewMatrix().set(savedMV);
        RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        return iterationsRan;
    }

    public boolean composite(ShaderInstance shader, Consumer<ShaderInstance> uniformSetup) {
        if (densityTarget == null || shader == null) return false;

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewportWidth = Math.max(1, viewport.get(2));
        int viewportHeight = Math.max(1, viewport.get(3));

        RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId());

        Uniform uScreenSize = shader.getUniform("ScreenSize");
        if (uScreenSize != null) uScreenSize.set((float) viewportWidth, (float) viewportHeight);
        if (uniformSetup != null) {
            uniformSetup.accept(shader);
        }

        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

        drawFullScreenQuad();

        shader.clear();

        RenderSystem.getModelViewMatrix().set(savedMV);
        RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        return true;
    }

    public void destroy() {
        if (densityTarget != null) {
            densityTarget.destroyBuffers();
            densityTarget = null;
        }
        if (blurTempTarget != null) {
            blurTempTarget.destroyBuffers();
            blurTempTarget = null;
        }
        lastWidth = -1;
        lastHeight = -1;
        restoreFramebufferId = -1;
    }

    private boolean ensureTargets() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int mw = ((RenderTargetAccessor) (Object) main).getWidth();
        int mh = ((RenderTargetAccessor) (Object) main).getHeight();
        int hw = Math.max(1, mw / 2);
        int hh = Math.max(1, mh / 2);

        if (densityTarget == null || lastWidth != hw || lastHeight != hh) {
            if (densityTarget != null) densityTarget.destroyBuffers();
            if (blurTempTarget != null) blurTempTarget.destroyBuffers();

            densityTarget = new TextureTarget(hw, hh, true, Minecraft.ON_OSX);
            densityTarget.setClearColor(0f, 0f, 0f, 0f);

            blurTempTarget = new TextureTarget(hw, hh, false, Minecraft.ON_OSX);
            blurTempTarget.setClearColor(0f, 0f, 0f, 0f);

            lastWidth = hw;
            lastHeight = hh;

            int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

            densityTarget.bindWrite(false);
            blurTempTarget.bindWrite(false);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            RenderSystem.viewport(prevViewport.get(0), prevViewport.get(1),
                    prevViewport.get(2), prevViewport.get(3));
            return true;
        }
        return false;
    }

    private void blitMainDepthToDensity() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int mainFbo = ((RenderTargetAccessor) (Object) main).getFrameBufferId();
        int densityFbo = ((RenderTargetAccessor) (Object) densityTarget).getFrameBufferId();
        int mainW = ((RenderTargetAccessor) (Object) main).getWidth();
        int mainH = ((RenderTargetAccessor) (Object) main).getHeight();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, densityFbo);
        GL30.glBlitFramebuffer(
                0, 0, mainW, mainH,
                0, 0, lastWidth, lastHeight,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );
    }

    private void captureCurrentRenderState() {
        restoreFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        restoreViewportX = viewport.get(0);
        restoreViewportY = viewport.get(1);
        restoreViewportW = viewport.get(2);
        restoreViewportH = viewport.get(3);
    }

    private void restoreCapturedRenderState() {
        if (restoreFramebufferId >= 0) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFramebufferId);
            RenderSystem.viewport(restoreViewportX, restoreViewportY,
                    restoreViewportW, restoreViewportH);
        } else {
            Minecraft mc = Minecraft.getInstance();
            mc.getMainRenderTarget().bindWrite(false);
            RenderTarget main = mc.getMainRenderTarget();
            int mw = ((RenderTargetAccessor) (Object) main).getWidth();
            int mh = ((RenderTargetAccessor) (Object) main).getHeight();
            RenderSystem.viewport(0, 0, mw, mh);
        }

        restoreFramebufferId = -1;
        restoreViewportX = 0;
        restoreViewportY = 0;
        restoreViewportW = 0;
        restoreViewportH = 0;
    }

    private static void drawFullScreenQuad() {
        var tess = Tesselator.getInstance();
        var buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        buf.addVertex( 1f, -1f, 0f).setUv(1f, 0f);
        buf.addVertex( 1f,  1f, 0f).setUv(1f, 1f);
        buf.addVertex(-1f,  1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}
