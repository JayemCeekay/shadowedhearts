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
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Consumer;

/**
 * Shared configurable-resolution density FBO pipeline for splat -> blur -> composite effects.
 *
 * <p>The caller owns when to draw density splats, but this class owns the
 * framebuffer lifecycle, viewport restoration, depth copy, separable blur, and
 * fullscreen composite draw. Instances are intentionally stateful because each
 * effect pipeline has different blur settings but the same GPU workflow.
 */
public final class DensityFboPipeline {

    private final int blurIterations;
    private final float blurRadius;
    private int resolutionDivisor = 2;

    private RenderTarget densityTarget;
    private RenderTarget blurTempTarget;
    private RenderTarget maskTarget;
    private RenderTarget proxyFrontDepthTarget;
    private RenderTarget proxyBackDepthTarget;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int restoreFramebufferId = -1;
    private int restoreViewportX;
    private int restoreViewportY;
    private int restoreViewportW;
    private int restoreViewportH;

    /**
     * @param blurIterations number of horizontal/vertical blur pairs to run
     * @param blurRadius shader-specific radius multiplier for each blur pass
     */
    public DensityFboPipeline(int blurIterations, float blurRadius) {
        this.blurIterations = blurIterations;
        this.blurRadius = blurRadius;
    }

    /**
     * Selects the target resolution relative to the active viewport. Existing
     * targets are recreated lazily by the next pass when the divisor changes.
     */
    public void setResolutionDivisor(int divisor) {
        resolutionDivisor = Math.max(1, divisor);
    }

    /**
     * Begins a density splat pass, clearing the target and copying main depth.
     *
     * @return {@code true} when callers may draw into the density target
     */
    public boolean beginDensityPass() {
        return beginDensityPass(true, true);
    }

    /**
     * Captures the current framebuffer/viewport, creates or resizes the
     * configured-resolution targets, optionally clears them, optionally copies source
     * depth, then binds the density target for drawing.
     *
     * <p>Every successful or failed call should be paired with
     * {@link #endDensityPass()} once the caller is done with the pass state.
     */
    public boolean beginDensityPass(boolean clearTarget, boolean copyMainDepth) {
        captureCurrentRenderState();

        boolean recreatedTargets = ensureTargets(restoreViewportW, restoreViewportH);
        if (densityTarget == null) return false;

        if (clearTarget || recreatedTargets) {
            clearTargetUnscissored(densityTarget);
        }
        if (copyMainDepth || clearTarget || recreatedTargets) {
            blitSourceDepthToDensity(restoreFramebufferId, restoreViewportW, restoreViewportH);
        }

        densityTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);

        return true;
    }

    /**
     * Restores the framebuffer and viewport that were active before
     * {@link #beginDensityPass()}.
     */
    public void endDensityPass() {
        restoreCapturedRenderState();
    }

    /**
     * @return OpenGL color texture id for the current density target, or {@code 0}
     * if targets have not been created
     */
    public int getDensityTextureId() {
        return densityTarget != null ? densityTarget.getColorTextureId() : 0;
    }

    /**
     * @return width of the current density source in pixels, or {@code 0}
     * before the targets have been created
     */
    public int getTargetWidth() {
        return Math.max(lastWidth, 0);
    }

    /**
     * @return height of the current density source in pixels, or {@code 0}
     * before the targets have been created
     */
    public int getTargetHeight() {
        return Math.max(lastHeight, 0);
    }

    public int getMaskTextureId() {
        return maskTarget != null ? maskTarget.getColorTextureId() : 0;
    }

    public int getProxyFrontTextureId() {
        return proxyFrontDepthTarget != null ? proxyFrontDepthTarget.getColorTextureId() : 0;
    }

    /**
     * Returns the untouched scene-depth copy used by fullscreen bounded volume
     * passes. The color attachment remains available for legacy proxy depth.
     */
    public int getProxyFrontDepthTextureId() {
        return proxyFrontDepthTarget != null ? proxyFrontDepthTarget.getDepthTextureId() : 0;
    }

    public int getProxyBackTextureId() {
        return proxyBackDepthTarget != null ? proxyBackDepthTarget.getColorTextureId() : 0;
    }

    public boolean hasProxyDepthTargets() {
        return proxyFrontDepthTarget != null && proxyBackDepthTarget != null;
    }

    public boolean beginMaskPass(boolean clearTarget, boolean copyMainDepth) {
        return beginAuxiliaryPass(maskTarget, clearTarget, copyMainDepth);
    }

    public boolean beginProxyDepthPass(boolean backFaces, boolean clearTarget, boolean copyMainDepth) {
        return beginAuxiliaryPass(backFaces ? proxyBackDepthTarget : proxyFrontDepthTarget, clearTarget, copyMainDepth);
    }

    public void resumeDensityPass() {
        if (densityTarget == null) {
            return;
        }
        densityTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);
    }

    /**
     * Runs the configured separable blur in-place on the density texture.
     *
     * @return number of blur iterations completed
     */
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
            clearTargetUnscissored(blurTempTarget);
            blurTempTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId());

            if (uDir != null) uDir.set(texelW, 0.0f);
            if (uRad != null) uRad.set(blurRadius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();

            clearTargetUnscissored(densityTarget);
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

    /**
     * Renders the current density material through a fullscreen shader into the
     * reusable temporary target. The input material is bound to texture unit 0;
     * callers may bind additional samplers and set effect-specific uniforms in
     * {@code uniformSetup}.
     *
     * <p>The pass always overwrites the temporary color buffer and disables
     * depth testing/writes. Framebuffer, viewport, matrices, blend/depth, cull,
     * and scissor state are restored even if shader setup or drawing fails.
     *
     * @param shader material postprocess shader that samples density on unit 0
     * @param uniformSetup optional callback for effect-specific uniforms
     * @return {@code true} when a fullscreen draw was submitted
     */
    public boolean processDensityToTemp(ShaderInstance shader,
                                        Consumer<ShaderInstance> uniformSetup) {
        return processDensityToTemp(
                shader, uniformSetup, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * Bounded variant of {@link #processDensityToTemp(ShaderInstance,
     * Consumer)}. Quad UVs remain absolute screen UVs; only fragment coverage
     * is restricted.
     */
    public boolean processDensityToTemp(ShaderInstance shader,
                                        Consumer<ShaderInstance> uniformSetup,
                                        float minimumU, float minimumV,
                                        float maximumU, float maximumV) {
        if (densityTarget == null || blurTempTarget == null || shader == null) {
            return false;
        }

        FullscreenPassState savedState = FullscreenPassState.capture();
        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        boolean drawSubmitted = false;

        try {
            clearTargetUnscissored(blurTempTarget);
            blurTempTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);

            RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId());

            Uniform uScreenSize = shader.getUniform("ScreenSize");
            if (uScreenSize != null) {
                uScreenSize.set((float) lastWidth, (float) lastHeight);
            }
            if (uniformSetup != null) {
                uniformSetup.accept(shader);
            }

            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.setShader(() -> shader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            drawScreenQuad(minimumU, minimumV, maximumU, maximumV);
            drawSubmitted = true;
        } finally {
            shader.clear();
            RenderSystem.getModelViewMatrix().set(savedMV);
            RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
            savedState.restore();
        }

        return drawSubmitted;
    }

    /**
     * Composites the density texture over the current framebuffer.
     *
     * @param shader composite shader that samples the density texture on unit 0
     * @param uniformSetup optional callback for effect-specific uniforms
     * @return {@code true} when a composite draw was submitted
     */
    public boolean composite(ShaderInstance shader, Consumer<ShaderInstance> uniformSetup) {
        return composite(shader, uniformSetup, false);
    }

    /**
     * Composites either the original density material or the most recently
     * processed temporary material over the current framebuffer.
     *
     * @param shader composite shader that samples its source on texture unit 0
     * @param uniformSetup optional callback for effect-specific uniforms
     * @param useProcessedTemp when {@code true}, sample the temporary postprocess
     *                         output instead of the original density target
     * @return {@code true} when a composite draw was submitted
     */
    public boolean composite(ShaderInstance shader, Consumer<ShaderInstance> uniformSetup,
                             boolean useProcessedTemp) {
        return composite(shader, uniformSetup, useProcessedTemp,
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * Bounded variant of {@link #composite(ShaderInstance, Consumer,
     * boolean)}. The positions and UVs use the same normalized rectangle so
     * ray reconstruction and framebuffer sampling retain global coordinates.
     */
    public boolean composite(ShaderInstance shader,
                             Consumer<ShaderInstance> uniformSetup,
                             boolean useProcessedTemp,
                             float minimumU, float minimumV,
                             float maximumU, float maximumV) {
        RenderTarget sourceTarget = useProcessedTemp ? blurTempTarget : densityTarget;
        if (sourceTarget == null || shader == null) return false;

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewportWidth = Math.max(1, viewport.get(2));
        int viewportHeight = Math.max(1, viewport.get(3));

        RenderSystem.setShaderTexture(0, sourceTarget.getColorTextureId());

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

        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            drawScreenQuad(minimumU, minimumV, maximumU, maximumV);
        } finally {
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }

        shader.clear();

        RenderSystem.getModelViewMatrix().set(savedMV);
        RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        return true;
    }

    /**
     * Releases all framebuffer resources owned by this pipeline.
     */
    public void destroy() {
        if (densityTarget != null) {
            densityTarget.destroyBuffers();
            densityTarget = null;
        }
        if (blurTempTarget != null) {
            blurTempTarget.destroyBuffers();
            blurTempTarget = null;
        }
        if (maskTarget != null) {
            maskTarget.destroyBuffers();
            maskTarget = null;
        }
        if (proxyFrontDepthTarget != null) {
            proxyFrontDepthTarget.destroyBuffers();
            proxyFrontDepthTarget = null;
        }
        if (proxyBackDepthTarget != null) {
            proxyBackDepthTarget.destroyBuffers();
            proxyBackDepthTarget = null;
        }
        lastWidth = -1;
        lastHeight = -1;
        restoreFramebufferId = -1;
    }

    private boolean ensureTargets(int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            sourceWidth = ((RenderTargetAccessor) (Object) main).getWidth();
            sourceHeight = ((RenderTargetAccessor) (Object) main).getHeight();
        }

        int mw = Math.max(1, sourceWidth);
        int mh = Math.max(1, sourceHeight);
        int hw = Math.max(1, mw / resolutionDivisor);
        int hh = Math.max(1, mh / resolutionDivisor);

        if (densityTarget == null || lastWidth != hw || lastHeight != hh) {
            if (densityTarget != null) densityTarget.destroyBuffers();
            if (blurTempTarget != null) blurTempTarget.destroyBuffers();
            if (maskTarget != null) maskTarget.destroyBuffers();
            if (proxyFrontDepthTarget != null) proxyFrontDepthTarget.destroyBuffers();
            if (proxyBackDepthTarget != null) proxyBackDepthTarget.destroyBuffers();

            densityTarget = createFloatColorTarget(hw, hh, true, GL11.GL_LINEAR);
            blurTempTarget = createFloatColorTarget(hw, hh, false, GL11.GL_LINEAR);
            maskTarget = createFloatColorTarget(hw, hh, true, GL11.GL_LINEAR);
            proxyFrontDepthTarget = createFloatColorTarget(hw, hh, true, GL11.GL_NEAREST);
            proxyFrontDepthTarget.setClearColor(1f, 0f, 0f, 0f);
            proxyBackDepthTarget = createFloatColorTarget(hw, hh, true, GL11.GL_NEAREST);
            proxyBackDepthTarget.setClearColor(0f, 0f, 0f, 0f);

            lastWidth = hw;
            lastHeight = hh;

            // Preserve the active framebuffer and viewport around the validation
            // binds below; beginPass() will bind the density target explicitly.
            int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

            densityTarget.bindWrite(false);
            blurTempTarget.bindWrite(false);
            maskTarget.bindWrite(false);
            proxyFrontDepthTarget.bindWrite(false);
            proxyBackDepthTarget.bindWrite(false);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            RenderSystem.viewport(prevViewport.get(0), prevViewport.get(1),
                    prevViewport.get(2), prevViewport.get(3));
            return true;
        }
        return false;
    }

    private static TextureTarget createFloatColorTarget(int targetWidth, int targetHeight, boolean useDepth, int filterMode) {
        TextureTarget target = new TextureTarget(targetWidth, targetHeight, useDepth, Minecraft.ON_OSX);
        RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_RGBA16F,
                targetWidth,
                targetHeight,
                0,
                GL11.GL_RGBA,
                GL11.GL_FLOAT,
                (ByteBuffer) null
        );
        target.setClearColor(0f, 0f, 0f, 0f);
        target.setFilterMode(filterMode);
        return target;
    }

    /**
     * OpenGL clears honor the active scissor rectangle. Density targets must
     * be cleared in full before a moving bounded quad is drawn or material from
     * an older rectangle can survive into the new frame.
     */
    private static void clearTargetUnscissored(RenderTarget target) {
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            target.clear(Minecraft.ON_OSX);
        } finally {
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private boolean beginAuxiliaryPass(RenderTarget target, boolean clearTarget, boolean copyMainDepth) {
        if (target == null) {
            return false;
        }
        if (clearTarget) {
            clearTargetUnscissored(target);
        }
        if (copyMainDepth || clearTarget) {
            blitSourceDepthToTarget(restoreFramebufferId, restoreViewportW, restoreViewportH, target);
        }

        target.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);
        return true;
    }

    private void blitSourceDepthToDensity(int sourceFbo, int sourceWidth, int sourceHeight) {
        blitSourceDepthToTarget(sourceFbo, sourceWidth, sourceHeight, densityTarget);
    }

    private void blitSourceDepthToTarget(int sourceFbo, int sourceWidth, int sourceHeight, RenderTarget target) {
        if (target == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (sourceFbo <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            RenderTarget main = mc.getMainRenderTarget();
            sourceFbo = ((RenderTargetAccessor) (Object) main).getFrameBufferId();
            sourceWidth = ((RenderTargetAccessor) (Object) main).getWidth();
            sourceHeight = ((RenderTargetAccessor) (Object) main).getHeight();
        }

        int targetFbo = ((RenderTargetAccessor) (Object) target).getFrameBufferId();

        // Preserve world occlusion by copying the full-resolution depth buffer
        // into the configured offscreen target before hidden passes draw.
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);
            GL30.glBlitFramebuffer(
                    0, 0, Math.max(1, sourceWidth),
                    Math.max(1, sourceHeight),
                    0, 0, lastWidth, lastHeight,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
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
        drawScreenQuad(0.0f, 0.0f, 1.0f, 1.0f);
    }

    private static void drawScreenQuad(float minimumU, float minimumV,
                                       float maximumU, float maximumV) {
        // Invalid caller input must degrade to the established full-screen
        // behavior, never to a clipped or inverted effect.
        if (!Float.isFinite(minimumU) || !Float.isFinite(minimumV)
                || !Float.isFinite(maximumU) || !Float.isFinite(maximumV)
                || maximumU <= minimumU || maximumV <= minimumV) {
            minimumU = 0.0f;
            minimumV = 0.0f;
            maximumU = 1.0f;
            maximumV = 1.0f;
        } else {
            minimumU = Math.max(0.0f, Math.min(1.0f, minimumU));
            minimumV = Math.max(0.0f, Math.min(1.0f, minimumV));
            maximumU = Math.max(0.0f, Math.min(1.0f, maximumU));
            maximumV = Math.max(0.0f, Math.min(1.0f, maximumV));
            if (maximumU <= minimumU || maximumV <= minimumV) {
                minimumU = 0.0f;
                minimumV = 0.0f;
                maximumU = 1.0f;
                maximumV = 1.0f;
            }
        }

        float minimumX = minimumU * 2.0f - 1.0f;
        float minimumY = minimumV * 2.0f - 1.0f;
        float maximumX = maximumU * 2.0f - 1.0f;
        float maximumY = maximumV * 2.0f - 1.0f;
        var tess = Tesselator.getInstance();
        var buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(minimumX, minimumY, 0f).setUv(minimumU, minimumV);
        buf.addVertex(maximumX, minimumY, 0f).setUv(maximumU, minimumV);
        buf.addVertex(maximumX, maximumY, 0f).setUv(maximumU, maximumV);
        buf.addVertex(minimumX, maximumY, 0f).setUv(minimumU, maximumV);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private record FullscreenPassState(int drawFramebuffer, int readFramebuffer,
                                       int viewportX, int viewportY,
                                       int viewportWidth, int viewportHeight,
                                       boolean blendEnabled, boolean depthTestEnabled,
                                       boolean cullEnabled, boolean scissorEnabled,
                                       boolean depthMask,
                                       int blendSourceRgb, int blendDestinationRgb,
                                       int blendSourceAlpha, int blendDestinationAlpha,
                                       int blendEquationRgb, int blendEquationAlpha) {
        static FullscreenPassState capture() {
            IntBuffer viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return new FullscreenPassState(
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)
            );
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);

            GlStateManager._blendFuncSeparate(
                    blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthTestEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            RenderSystem.depthMask(depthMask);
        }
    }
}
