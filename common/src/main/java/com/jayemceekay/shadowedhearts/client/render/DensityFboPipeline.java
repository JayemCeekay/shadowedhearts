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
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

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
    private boolean reducedCompositeEnabled;
    private boolean reducedCompositeSupported = true;
    private boolean splatDiagnosticEnabled;
    private boolean surfaceSplatFrontDepthEnabled;
    private boolean silhouetteDistanceEnabled;

    private RenderTarget densityTarget;
    private RenderTarget blurTempTarget;
    private RenderTarget reducedCompositeTarget;
    private RenderTarget splatDiagnosticTarget;
    private RenderTarget maskTarget;
    private RenderTarget proxyFrontDepthTarget;
    private RenderTarget surfaceSplatFrontDepthTarget;
    private RenderTarget silhouetteDistanceTargetA;
    private RenderTarget silhouetteDistanceTargetB;
    private RenderTarget silhouetteDistanceResultTarget;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int reducedCompositeWidth = -1;
    private int reducedCompositeHeight = -1;
    private int silhouetteDistanceWidth = -1;
    private int silhouetteDistanceHeight = -1;
    private long targetGeneration;
    private int restoreDrawFramebufferId = -1;
    private int restoreReadFramebufferId = -1;
    private boolean renderStateCaptured;
    private int restoreViewportX;
    private int restoreViewportY;
    private int restoreViewportW;
    private int restoreViewportH;
    private RenderTransactionState restoreRenderState;

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
     * Controls use of the optional compact styled target for the current
     * transaction. Once allocated, the target remains resident until the
     * pipeline is resized or destroyed. Multiple Dark Ball captures can make
     * different per-capture quality decisions, and tearing the compact target
     * down on every disabled capture would force the entire shared target
     * suite to be recreated when the next capture enables it.
     */
    public void setReducedCompositeEnabled(boolean enabled) {
        boolean effectiveEnabled = enabled && reducedCompositeSupported;
        if (reducedCompositeEnabled == effectiveEnabled) {
            return;
        }
        reducedCompositeEnabled = effectiveEnabled;
    }

    /**
     * Permanently disables the optional reduced target until the pipeline is
     * explicitly destroyed/recreated. A failed compact pass must not retry an
     * allocation or draw every frame on a driver that rejected it.
     */
    public void disableReducedCompositeAfterFailure() {
        reducedCompositeSupported = false;
        reducedCompositeEnabled = false;
        if (reducedCompositeTarget != null) {
            reducedCompositeTarget.destroyBuffers();
            reducedCompositeTarget = null;
            targetGeneration++;
        }
        reducedCompositeWidth = -1;
        reducedCompositeHeight = -1;
    }

    /**
     * Controls the optional color-only target used to inspect individual
     * surface-splat footprints before the production accumulation field is
     * resolved. It is deliberately separate from every presentation target.
     */
    public void setSplatDiagnosticEnabled(boolean enabled) {
        if (splatDiagnosticEnabled == enabled) {
            return;
        }
        splatDiagnosticEnabled = enabled;
        if (!enabled && splatDiagnosticTarget != null) {
            splatDiagnosticTarget.destroyBuffers();
            splatDiagnosticTarget = null;
            targetGeneration++;
        }
    }

    /**
     * Enables the Dark Ball-only scalar front-depth attachment. The generic
     * pipeline also backs aura and trail effects, which must not pay for this
     * target.
     */
    public void setSurfaceSplatFrontDepthEnabled(boolean enabled) {
        if (surfaceSplatFrontDepthEnabled == enabled) {
            return;
        }
        surfaceSplatFrontDepthEnabled = enabled;
        if (!enabled && surfaceSplatFrontDepthTarget != null) {
            surfaceSplatFrontDepthTarget.destroyBuffers();
            surfaceSplatFrontDepthTarget = null;
            targetGeneration++;
        }
    }

    /**
     * Enables the compact, screen-space nearest-silhouette-coordinate targets.
     * The generic pipeline must opt in because only Dark Ball styling consumes
     * this field.
     */
    public void setSilhouetteDistanceEnabled(boolean enabled) {
        if (silhouetteDistanceEnabled == enabled) {
            return;
        }
        silhouetteDistanceEnabled = enabled;
        if (!enabled) {
            if (silhouetteDistanceTargetA != null) {
                silhouetteDistanceTargetA.destroyBuffers();
                silhouetteDistanceTargetA = null;
            }
            if (silhouetteDistanceTargetB != null) {
                silhouetteDistanceTargetB.destroyBuffers();
                silhouetteDistanceTargetB = null;
            }
            silhouetteDistanceResultTarget = null;
            silhouetteDistanceWidth = -1;
            silhouetteDistanceHeight = -1;
            targetGeneration++;
        }
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
        try {
            boolean recreatedTargets = ensureTargets(
                    restoreViewportW, restoreViewportH);
            if (densityTarget == null) {
                restoreCapturedRenderState();
                return false;
            }

            if (clearTarget || recreatedTargets) {
                clearTargetUnscissored(densityTarget);
            }
            if (copyMainDepth || clearTarget || recreatedTargets) {
                if (!blitSourceDepthToDensity()) {
                    restoreCapturedRenderState();
                    return false;
                }
            }

            densityTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);

            return true;
        } catch (RuntimeException | Error beginFailure) {
            restoreCapturedRenderState();
            throw beginFailure;
        }
    }

    /**
     * Restores the framebuffer and viewport that were active before
     * {@link #beginDensityPass()}.
     */
    public void endDensityPass() {
        restoreCapturedRenderState();
    }

    /**
     * Replaces the density target's working depth buffer with the scene depth
     * captured at {@link #beginDensityPass(boolean, boolean)} without touching
     * its color attachment.
     *
     * <p>Conventional mesh draws need depth writes for self-occlusion. The
     * downstream edge/composite passes, however, still sample this attachment
     * as pristine scene depth. Call this once after the last mesh draw and
     * before ending the density pass.
     *
     * @return {@code true} when the scene-depth copy was restored
     */
    public boolean restoreSceneDepthAfterDirectDraw() {
        if (densityTarget == null
                || !renderStateCaptured
                || restoreViewportW <= 0
                || restoreViewportH <= 0) {
            return false;
        }

        if (!blitSourceDepthToDensity()) {
            return false;
        }
        densityTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);
        return true;
    }

    /**
     * Discards any partially written direct material and restores pristine
     * scene depth after a failed surfel or siphon submission.
     *
     * <p>This is intentionally separate from
     * {@link #restoreSceneDepthAfterDirectDraw()}: a successful direct draw
     * keeps its color attachment, while a failed body/siphon transaction must
     * leave a clean target for the exact-mask safety presentation.</p>
     *
     * @return {@code true} when the density target is clean and ready
     */
    public boolean resetDensityPassAfterFailedDirectDraw() {
        if (densityTarget == null
                || !renderStateCaptured
                || restoreViewportW <= 0
                || restoreViewportH <= 0) {
            return false;
        }

        clearTargetUnscissored(densityTarget);
        if (!blitSourceDepthToDensity()) {
            return false;
        }
        densityTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);
        return true;
    }

    /**
     * @return OpenGL color texture id for the current density target, or {@code 0}
     * if targets have not been created
     */
    public int getDensityTextureId() {
        return densityTarget != null ? densityTarget.getColorTextureId() : 0;
    }

    /**
     * Returns the depth attachment associated with the density target. It is a
     * pristine scene-depth copy for fullscreen draws. Mesh draws may use it as
     * working depth, provided they call
     * {@link #restoreSceneDepthAfterDirectDraw()} before post-processing.
     */
    public int getDensityDepthTextureId() {
        return densityTarget != null ? densityTarget.getDepthTextureId() : 0;
    }

    /**
     * @return OpenGL color texture id for the most recent processed material,
     * or {@code 0} if targets have not been created
     */
    public int getProcessedTextureId() {
        return blurTempTarget != null ? blurTempTarget.getColorTextureId() : 0;
    }

    /**
     * @return OpenGL color texture id for the optional reduced-resolution
     * styled composite, or {@code 0} if targets have not been created
     */
    public int getReducedCompositeTextureId() {
        return reducedCompositeTarget != null
                ? reducedCompositeTarget.getColorTextureId()
                : 0;
    }

    public boolean isReducedCompositeReady() {
        return reducedCompositeEnabled
                && reducedCompositeTarget != null
                && reducedCompositeWidth > 0
                && reducedCompositeHeight > 0;
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

    public int getSurfaceSplatFrontDepthTextureId() {
        return surfaceSplatFrontDepthTarget != null
                ? surfaceSplatFrontDepthTarget.getColorTextureId()
                : 0;
    }

    public int getSplatDiagnosticTextureId() {
        return splatDiagnosticTarget != null
                ? splatDiagnosticTarget.getColorTextureId()
                : 0;
    }

    public int getSilhouetteDistanceTextureId() {
        return silhouetteDistanceResultTarget != null
                ? silhouetteDistanceResultTarget.getColorTextureId()
                : 0;
    }

    /**
     * Changes whenever framebuffer-backed texture ids can have been replaced.
     * Debug consumers use this to reject snapshots cached across a resize,
     * quality change, or explicit destroy.
     */
    public long getTargetGeneration() {
        return targetGeneration;
    }

    public boolean beginMaskPass(boolean clearTarget, boolean copyMainDepth) {
        return beginAuxiliaryPass(maskTarget, clearTarget, copyMainDepth);
    }

    public boolean beginProxyDepthPass(boolean clearTarget, boolean copyMainDepth) {
        return beginAuxiliaryPass(proxyFrontDepthTarget, clearTarget, copyMainDepth);
    }

    /**
     * Binds the single-channel frontmost-surface target with a pristine copy of
     * scene depth. The splat renderer enables depth writes for this pass, so
     * fused coverage and surface ordering can remain independent authorities.
     */
    public boolean beginSurfaceSplatFrontDepthPass(
            boolean clearTarget,
            boolean copyMainDepth) {
        return beginAuxiliaryPass(
                surfaceSplatFrontDepthTarget,
                clearTarget,
                copyMainDepth);
    }

    /**
     * Binds the color-only pre-merge splat inspection target. No scene depth is
     * copied: splats must remain mutually visible so footprint overlap can be
     * diagnosed instead of self-occluding.
     */
    public boolean beginSplatDiagnosticPass(boolean clearTarget) {
        if (splatDiagnosticTarget == null) {
            return false;
        }
        if (clearTarget) {
            clearTargetUnscissored(splatDiagnosticTarget);
        }
        splatDiagnosticTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);
        return true;
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
        return blur(blurShader, null);
    }

    /**
     * Runs the configured separable blur in-place on the density texture.
     *
     * @param uniformSetup optional callback for effect-specific uniforms (e.g.
     *     pixelization block size)
     * @return number of blur iterations completed
     */
    public int blur(ShaderInstance blurShader, Consumer<ShaderInstance> uniformSetup) {
        if (blurIterations <= 0 || blurShader == null || densityTarget == null || blurTempTarget == null) return 0;

        int prevDrawFramebufferId =
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFramebufferId =
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
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
        Uniform uScreenSize = blurShader.getUniform("ScreenSize");

        if (blurShader.getUniform("Sampler1") != null) {
            int densityDepthTexture = densityTarget.getDepthTextureId();
            RenderSystem.setShaderTexture(1, densityDepthTexture);
            blurShader.setSampler("Sampler1", densityDepthTexture);
        }
        if (uScreenSize != null) {
            uScreenSize.set((float) lastWidth, (float) lastHeight);
        }
        if (uniformSetup != null) {
            uniformSetup.accept(blurShader);
        }

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

        GL30.glBindFramebuffer(
                GL30.GL_DRAW_FRAMEBUFFER, prevDrawFramebufferId);
        GL30.glBindFramebuffer(
                GL30.GL_READ_FRAMEBUFFER, prevReadFramebufferId);
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
            if (shader.getUniform("Sampler1") != null) {
                int depthTexture = densityTarget.getDepthTextureId();
                RenderSystem.setShaderTexture(1, depthTexture);
                shader.setSampler("Sampler1", depthTexture);
            }

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
            GL20.glBlendEquationSeparate(
                    GL14.GL_FUNC_ADD,
                    GL14.GL_FUNC_ADD);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            drawScreenQuad(minimumU, minimumV, maximumU, maximumV);
            drawSubmitted = true;
        } finally {
            try {
                shader.clear();
            } finally {
                RenderSystem.getModelViewMatrix().set(savedMV);
                RenderSystem.setProjectionMatrix(
                        savedProj,
                        VertexSorting.DISTANCE_TO_ORIGIN);
                savedState.restore();
            }
        }

        return drawSubmitted;
    }

    /**
     * Resolves the reusable temporary texture back into the density color
     * attachment without clearing or modifying its scene-depth attachment.
     *
     * <p>This is the second half of topology-independent splat-field
     * reconstruction: the first bounded pass filters accumulation into the
     * temporary target; this pass filters the other axis and decodes final
     * material/depth channels. Keeping it here avoids sampling from and
     * rendering to the same texture.</p>
     */
    public boolean processTempToDensity(
            ShaderInstance shader,
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
            densityTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            clearColorAttachmentUnscissored();

            RenderSystem.setShaderTexture(
                    0,
                    blurTempTarget.getColorTextureId());
            Uniform uScreenSize = shader.getUniform("ScreenSize");
            if (uScreenSize != null) {
                uScreenSize.set((float) lastWidth, (float) lastHeight);
            }
            if (uniformSetup != null) {
                uniformSetup.accept(shader);
            }

            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f(),
                    VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.setShader(() -> shader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GL20.glBlendEquationSeparate(
                    GL14.GL_FUNC_ADD,
                    GL14.GL_FUNC_ADD);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            drawScreenQuad(
                    minimumU,
                    minimumV,
                    maximumU,
                    maximumV);
            drawSubmitted = true;
        } finally {
            try {
                shader.clear();
            } finally {
                RenderSystem.getModelViewMatrix().set(savedMV);
                RenderSystem.setProjectionMatrix(
                        savedProj,
                        VertexSorting.DISTANCE_TO_ORIGIN);
                savedState.restore();
            }
        }

        return drawSubmitted;
    }

    /**
     * Builds a compact nearest-boundary coordinate field from the processed
     * material in {@link #blurTempTarget}. The seed pass writes projected union
     * boundary coordinates; bounded jump-flood passes propagate the nearest
     * coordinate far enough to cover the caller's maximum presentation band.
     *
     * <p>Coordinates use normalized screen UVs in an RG32F attachment. At half
     * output resolution this costs the same eight bytes per pixel as RGBA16F
     * while avoiding half-float UV quantization on ultrawide framebuffers.</p>
     */
    public boolean buildSilhouetteDistanceField(
            ShaderInstance seedShader,
            ShaderInstance jumpShader,
            float maximumOutputDistancePixels,
            float minimumU, float minimumV,
            float maximumU, float maximumV) {
        if (blurTempTarget == null
                || silhouetteDistanceTargetA == null
                || silhouetteDistanceTargetB == null
                || seedShader == null
                || jumpShader == null
                || silhouetteDistanceWidth <= 0
                || silhouetteDistanceHeight <= 0) {
            silhouetteDistanceResultTarget = null;
            return false;
        }

        FullscreenPassState savedState = FullscreenPassState.capture();
        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        boolean completed = false;

        try {
            clearTargetUnscissored(silhouetteDistanceTargetA);
            clearTargetUnscissored(silhouetteDistanceTargetB);
            silhouetteDistanceTargetA.bindWrite(false);
            RenderSystem.viewport(
                    0, 0,
                    silhouetteDistanceWidth,
                    silhouetteDistanceHeight);
            RenderSystem.setShaderTexture(
                    0, blurTempTarget.getColorTextureId());
            Uniform targetSize = seedShader.getUniform("TargetSize");
            if (targetSize != null) {
                targetSize.set(
                        (float) silhouetteDistanceWidth,
                        (float) silhouetteDistanceHeight);
            }

            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShader(() -> seedShader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GL20.glBlendEquationSeparate(
                    GL14.GL_FUNC_ADD,
                    GL14.GL_FUNC_ADD);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            drawScreenQuad(minimumU, minimumV, maximumU, maximumV);
            seedShader.clear();

            float outputToDistanceScale = silhouetteDistanceWidth
                    / (float) Math.max(savedState.viewportWidth(), 1);
            int requiredDistancePixels = Math.max(1,
                    (int) Math.ceil(Math.max(
                            maximumOutputDistancePixels, 1.0f)
                            * outputToDistanceScale));
            int jumpStep = 1;
            while (jumpStep < requiredDistancePixels && jumpStep < 16384) {
                jumpStep <<= 1;
            }

            RenderTarget source = silhouetteDistanceTargetA;
            RenderTarget destination = silhouetteDistanceTargetB;
            while (jumpStep >= 1) {
                clearTargetUnscissored(destination);
                destination.bindWrite(false);
                RenderSystem.viewport(
                        0, 0,
                        silhouetteDistanceWidth,
                        silhouetteDistanceHeight);
                RenderSystem.setShaderTexture(
                        0, source.getColorTextureId());
                Uniform jumpStepUniform = jumpShader.getUniform("JumpStep");
                if (jumpStepUniform != null) {
                    jumpStepUniform.set((float) jumpStep);
                }
                RenderSystem.setShader(() -> jumpShader);
                drawScreenQuad(minimumU, minimumV, maximumU, maximumV);
                jumpShader.clear();

                RenderTarget swap = source;
                source = destination;
                destination = swap;
                jumpStep >>= 1;
            }

            silhouetteDistanceResultTarget = source;
            completed = true;
        } finally {
            if (!completed) {
                silhouetteDistanceResultTarget = null;
            }
            RenderSystem.getModelViewMatrix().set(savedMV);
            RenderSystem.setProjectionMatrix(
                    savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
            savedState.restore();
        }

        return completed;
    }

    /**
     * Resolves the processed material into a compact color target at the
     * pipeline resolution. The shader still receives the currently active
     * output viewport as {@code ScreenSize}, allowing pixel-sized material
     * bands to retain their final-output thickness when this target is
     * half-resolution.
     *
     * <p>The processed material remains untouched for diagnostics and as the
     * depth/coverage guide used by the later upsample.</p>
     */
    public boolean processProcessedToReducedComposite(
            ShaderInstance shader,
            Consumer<ShaderInstance> uniformSetup,
            float minimumU, float minimumV,
            float maximumU, float maximumV) {
        if (blurTempTarget == null
                || reducedCompositeTarget == null
                || shader == null) {
            return false;
        }

        FullscreenPassState savedState = FullscreenPassState.capture();
        Matrix4f savedMV =
                new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj =
                new Matrix4f(RenderSystem.getProjectionMatrix());
        boolean drawSubmitted = false;

        try {
            clearTargetUnscissored(reducedCompositeTarget);
            reducedCompositeTarget.bindWrite(false);
            RenderSystem.viewport(
                    0, 0,
                    reducedCompositeWidth,
                    reducedCompositeHeight);

            RenderSystem.setShaderTexture(
                    0, blurTempTarget.getColorTextureId());

            Uniform screenSize = shader.getUniform("ScreenSize");
            if (screenSize != null) {
                screenSize.set(
                        (float) Math.max(savedState.viewportWidth(), 1),
                        (float) Math.max(savedState.viewportHeight(), 1));
            }
            if (uniformSetup != null) {
                uniformSetup.accept(shader);
            }

            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.setShader(() -> shader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            drawScreenQuad(
                    minimumU, minimumV, maximumU, maximumV);
            drawSubmitted = true;
        } finally {
            try {
                shader.clear();
            } finally {
                RenderSystem.getModelViewMatrix().set(savedMV);
                RenderSystem.setProjectionMatrix(
                        savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
                savedState.restore();
            }
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
        return compositeFromTarget(
                sourceTarget,
                shader,
                uniformSetup,
                minimumU,
                minimumV,
                maximumU,
                maximumV);
    }

    /**
     * Upsamples and composites the optional compact styled target over the
     * active framebuffer.
     */
    public boolean compositeReduced(
            ShaderInstance shader,
            Consumer<ShaderInstance> uniformSetup,
            float minimumU, float minimumV,
            float maximumU, float maximumV) {
        return compositeFromTarget(
                reducedCompositeTarget,
                shader,
                uniformSetup,
                minimumU,
                minimumV,
                maximumU,
                maximumV);
    }

    private boolean compositeFromTarget(
            RenderTarget sourceTarget,
            ShaderInstance shader,
            Consumer<ShaderInstance> uniformSetup,
            float minimumU, float minimumV,
            float maximumU, float maximumV) {
        if (sourceTarget == null || shader == null) return false;

        RenderTransactionState savedState =
                RenderTransactionState.capture();
        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        boolean drawSubmitted = false;
        try {
            int viewportWidth = Math.max(
                    1, savedState.baseState().viewportWidth());
            int viewportHeight = Math.max(
                    1, savedState.baseState().viewportHeight());

            RenderSystem.setShaderTexture(
                    0, sourceTarget.getColorTextureId());

            Uniform screenSize = shader.getUniform("ScreenSize");
            if (screenSize != null) {
                screenSize.set(
                        (float) viewportWidth,
                        (float) viewportHeight);
            }
            if (uniformSetup != null) {
                uniformSetup.accept(shader);
            }

            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.setShader(() -> shader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            drawScreenQuad(minimumU, minimumV, maximumU, maximumV);
            drawSubmitted = true;
        } finally {
            try {
                shader.clear();
            } finally {
                RenderSystem.getModelViewMatrix().set(savedMV);
                RenderSystem.setProjectionMatrix(
                        savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
                savedState.restore();
            }
        }

        return drawSubmitted;
    }

    /**
     * Releases all framebuffer resources owned by this pipeline.
     */
    public void destroy() {
        // A teardown may be triggered while a pass is partially initialized.
        // Restore the caller's Iris/vanilla target before deleting attachments.
        restoreCapturedRenderState();
        if (densityTarget != null) {
            densityTarget.destroyBuffers();
            densityTarget = null;
        }
        if (blurTempTarget != null) {
            blurTempTarget.destroyBuffers();
            blurTempTarget = null;
        }
        if (reducedCompositeTarget != null) {
            reducedCompositeTarget.destroyBuffers();
            reducedCompositeTarget = null;
        }
        if (splatDiagnosticTarget != null) {
            splatDiagnosticTarget.destroyBuffers();
            splatDiagnosticTarget = null;
        }
        if (maskTarget != null) {
            maskTarget.destroyBuffers();
            maskTarget = null;
        }
        if (proxyFrontDepthTarget != null) {
            proxyFrontDepthTarget.destroyBuffers();
            proxyFrontDepthTarget = null;
        }
        if (surfaceSplatFrontDepthTarget != null) {
            surfaceSplatFrontDepthTarget.destroyBuffers();
            surfaceSplatFrontDepthTarget = null;
        }
        if (silhouetteDistanceTargetA != null) {
            silhouetteDistanceTargetA.destroyBuffers();
            silhouetteDistanceTargetA = null;
        }
        if (silhouetteDistanceTargetB != null) {
            silhouetteDistanceTargetB.destroyBuffers();
            silhouetteDistanceTargetB = null;
        }
        silhouetteDistanceResultTarget = null;
        lastWidth = -1;
        lastHeight = -1;
        reducedCompositeWidth = -1;
        reducedCompositeHeight = -1;
        silhouetteDistanceWidth = -1;
        silhouetteDistanceHeight = -1;
        reducedCompositeEnabled = false;
        reducedCompositeSupported = true;
        splatDiagnosticEnabled = false;
        surfaceSplatFrontDepthEnabled = false;
        silhouetteDistanceEnabled = false;
        restoreDrawFramebufferId = -1;
        restoreReadFramebufferId = -1;
        renderStateCaptured = false;
        restoreRenderState = null;
        targetGeneration++;
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
        // Styling is always resolved at half of the output viewport. LOW
        // already stores raw material at this size; large MEDIUM effects keep
        // their full-resolution material/depth but use the same compact style
        // target to reduce the expensive morphology pass.
        int reducedWidth = Math.max(1, mw / 2);
        int reducedHeight = Math.max(1, mh / 2);
        int distanceWidth = Math.max(1, mw / 2);
        int distanceHeight = Math.max(1, mh / 2);

        if (densityTarget == null
                || blurTempTarget == null
                || (reducedCompositeEnabled
                && (reducedCompositeTarget == null
                || reducedCompositeWidth != reducedWidth
                || reducedCompositeHeight != reducedHeight))
                || (splatDiagnosticEnabled
                && splatDiagnosticTarget == null)
                || maskTarget == null
                || proxyFrontDepthTarget == null
                || (surfaceSplatFrontDepthEnabled
                && surfaceSplatFrontDepthTarget == null)
                || (silhouetteDistanceEnabled
                && (silhouetteDistanceTargetA == null
                || silhouetteDistanceTargetB == null
                || silhouetteDistanceWidth != distanceWidth
                || silhouetteDistanceHeight != distanceHeight))
                || lastWidth != hw
                || lastHeight != hh) {
            if (densityTarget != null) densityTarget.destroyBuffers();
            if (blurTempTarget != null) blurTempTarget.destroyBuffers();
            if (reducedCompositeTarget != null) {
                reducedCompositeTarget.destroyBuffers();
            }
            if (splatDiagnosticTarget != null) {
                splatDiagnosticTarget.destroyBuffers();
            }
            if (maskTarget != null) maskTarget.destroyBuffers();
            if (proxyFrontDepthTarget != null) proxyFrontDepthTarget.destroyBuffers();
            if (surfaceSplatFrontDepthTarget != null) {
                surfaceSplatFrontDepthTarget.destroyBuffers();
            }
            if (silhouetteDistanceTargetA != null) {
                silhouetteDistanceTargetA.destroyBuffers();
            }
            if (silhouetteDistanceTargetB != null) {
                silhouetteDistanceTargetB.destroyBuffers();
            }
            silhouetteDistanceResultTarget = null;

            densityTarget = createFloatColorTarget(hw, hh, true, GL11.GL_LINEAR);
            blurTempTarget = createFloatColorTarget(hw, hh, false, GL11.GL_LINEAR);
            reducedCompositeTarget = reducedCompositeEnabled
                    ? createValidatedCompactColorTarget(
                    reducedWidth, reducedHeight, GL11.GL_LINEAR)
                    : null;
            splatDiagnosticTarget = splatDiagnosticEnabled
                    ? createValidatedCompactColorTarget(
                    hw, hh, GL11.GL_NEAREST)
                    : null;
            if (reducedCompositeEnabled
                    && reducedCompositeTarget == null) {
                reducedCompositeSupported = false;
                reducedCompositeEnabled = false;
            }
            reducedCompositeWidth = reducedCompositeTarget != null
                    ? reducedWidth : -1;
            reducedCompositeHeight = reducedCompositeTarget != null
                    ? reducedHeight : -1;
            maskTarget = createFloatColorTarget(hw, hh, true, GL11.GL_LINEAR);
            proxyFrontDepthTarget = createFloatColorTarget(hw, hh, true, GL11.GL_NEAREST);
            proxyFrontDepthTarget.setClearColor(1f, 0f, 0f, 0f);
            surfaceSplatFrontDepthTarget =
                    surfaceSplatFrontDepthEnabled
                            ? createSingleChannelFloatTarget(
                            hw, hh, true, GL11.GL_NEAREST)
                            : null;
            silhouetteDistanceTargetA = silhouetteDistanceEnabled
                    ? createTwoChannelCoordinateTarget(
                    distanceWidth, distanceHeight)
                    : null;
            silhouetteDistanceTargetB = silhouetteDistanceEnabled
                    ? createTwoChannelCoordinateTarget(
                    distanceWidth, distanceHeight)
                    : null;
            silhouetteDistanceWidth = silhouetteDistanceTargetA != null
                    && silhouetteDistanceTargetB != null
                    ? distanceWidth : -1;
            silhouetteDistanceHeight = silhouetteDistanceTargetA != null
                    && silhouetteDistanceTargetB != null
                    ? distanceHeight : -1;
            targetGeneration++;

            lastWidth = hw;
            lastHeight = hh;

            // Preserve the active framebuffer and viewport around the validation
            // binds below; beginPass() will bind the density target explicitly.
            int prevDrawFbo =
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int prevReadFbo =
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

            densityTarget.bindWrite(false);
            blurTempTarget.bindWrite(false);
            if (reducedCompositeTarget != null) {
                reducedCompositeTarget.bindWrite(false);
            }
            if (splatDiagnosticTarget != null) {
                splatDiagnosticTarget.bindWrite(false);
            }
            maskTarget.bindWrite(false);
            proxyFrontDepthTarget.bindWrite(false);
            if (surfaceSplatFrontDepthTarget != null) {
                surfaceSplatFrontDepthTarget.bindWrite(false);
            }
            if (silhouetteDistanceTargetA != null) {
                silhouetteDistanceTargetA.bindWrite(false);
            }
            if (silhouetteDistanceTargetB != null) {
                silhouetteDistanceTargetB.bindWrite(false);
            }

            GL30.glBindFramebuffer(
                    GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
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
     * Creates an R16F attachment for scalar linear surface distance. This keeps
     * the front-depth pass at one quarter of the color bandwidth of the RGBA16F
     * material targets while retaining a hardware depth attachment for
     * frontmost-splat selection.
     */
    private static TextureTarget createSingleChannelFloatTarget(
            int targetWidth,
            int targetHeight,
            boolean useDepth,
            int filterMode) {
        TextureTarget target = new TextureTarget(
                targetWidth,
                targetHeight,
                useDepth,
                Minecraft.ON_OSX);
        RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_R16F,
                targetWidth,
                targetHeight,
                0,
                GL11.GL_RED,
                GL11.GL_FLOAT,
                (ByteBuffer) null
        );
        target.setClearColor(0f, 0f, 0f, 0f);
        target.setFilterMode(filterMode);
        return target;
    }

    private static TextureTarget createTwoChannelCoordinateTarget(
            int targetWidth,
            int targetHeight) {
        TextureTarget target = new TextureTarget(
                targetWidth,
                targetHeight,
                false,
                Minecraft.ON_OSX);
        RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_RG32F,
                targetWidth,
                targetHeight,
                0,
                GL30.GL_RG,
                GL11.GL_FLOAT,
                (ByteBuffer) null);
        // Negative coordinates are the invalid/unreached sentinel.
        target.setClearColor(-1.0f, -1.0f, 0.0f, 0.0f);
        target.setFilterMode(GL11.GL_NEAREST);
        return target;
    }

    private static TextureTarget createValidatedCompactColorTarget(
            int targetWidth, int targetHeight, int filterMode) {
        int previousDrawFramebuffer =
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer =
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        TextureTarget target = null;
        try {
            target = new TextureTarget(
                    targetWidth,
                    targetHeight,
                    false,
                    Minecraft.ON_OSX);
            RenderSystem.bindTexture(target.getColorTextureId());
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA8,
                    targetWidth,
                    targetHeight,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    (ByteBuffer) null
            );
            target.setClearColor(0f, 0f, 0f, 0f);
            target.setFilterMode(filterMode);

            int targetFramebuffer =
                    ((RenderTargetAccessor) (Object) target)
                            .getFrameBufferId();
            GL30.glBindFramebuffer(
                    GL30.GL_FRAMEBUFFER, targetFramebuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                target.destroyBuffers();
                target = null;
            }
            return target;
        } catch (RuntimeException compactTargetFailure) {
            if (target != null) {
                target.destroyBuffers();
            }
            return null;
        } finally {
            GL30.glBindFramebuffer(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    previousDrawFramebuffer);
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER,
                    previousReadFramebuffer);
        }
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

    private static void clearColorAttachmentUnscissored() {
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL30.glClearBufferfv(
                    GL11.GL_COLOR,
                    0,
                    stack.floats(0.0f, 0.0f, 0.0f, 0.0f));
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
            if (!blitSourceDepthToTarget(target)) {
                return false;
            }
        }

        target.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);
        return true;
    }

    private boolean blitSourceDepthToDensity() {
        return blitSourceDepthToTarget(densityTarget);
    }

    private boolean blitSourceDepthToTarget(RenderTarget target) {
        if (target == null) {
            return false;
        }
        int sourceFbo;
        int sourceX;
        int sourceY;
        int sourceWidth;
        int sourceHeight;
        if (renderStateCaptured) {
            // The draw target owns the depth that corresponds to the active
            // viewport. Framebuffer zero is a valid source (not a sentinel).
            sourceFbo = restoreDrawFramebufferId;
            sourceX = restoreViewportX;
            sourceY = restoreViewportY;
            sourceWidth = restoreViewportW;
            sourceHeight = restoreViewportH;
        } else {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            sourceFbo = ((RenderTargetAccessor) (Object) main).getFrameBufferId();
            sourceX = 0;
            sourceY = 0;
            sourceWidth = ((RenderTargetAccessor) (Object) main).getWidth();
            sourceHeight = ((RenderTargetAccessor) (Object) main).getHeight();
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return false;
        }

        int targetFbo = ((RenderTargetAccessor) (Object) target).getFrameBufferId();
        int previousDrawFramebuffer =
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer =
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        // Preserve world occlusion by copying the exact active viewport from
        // the captured draw target into the configured offscreen attachment.
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        boolean copied = false;
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);
            boolean complete = GL30.glCheckFramebufferStatus(
                    GL30.GL_READ_FRAMEBUFFER)
                    == GL30.GL_FRAMEBUFFER_COMPLETE
                    && GL30.glCheckFramebufferStatus(
                    GL30.GL_DRAW_FRAMEBUFFER)
                    == GL30.GL_FRAMEBUFFER_COMPLETE;
            clearGlErrors();
            if (complete) {
                GL30.glBlitFramebuffer(
                        sourceX, sourceY,
                        sourceX + sourceWidth, sourceY + sourceHeight,
                        0, 0, lastWidth, lastHeight,
                        GL11.GL_DEPTH_BUFFER_BIT,
                        GL11.GL_NEAREST
                );
                copied = GL11.glGetError() == GL11.GL_NO_ERROR;
            }
        } finally {
            GL30.glBindFramebuffer(
                    GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(
                    GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
        return copied;
    }

    private static void clearGlErrors() {
        for (int i = 0;
             i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR;
             i++) {
            // Drain stale errors so the copy reports only its own result.
        }
    }

    private void captureCurrentRenderState() {
        if (renderStateCaptured) {
            restoreCapturedRenderState();
        }
        restoreRenderState = RenderTransactionState.capture();
        FullscreenPassState baseState = restoreRenderState.baseState();
        restoreDrawFramebufferId = baseState.drawFramebuffer();
        restoreReadFramebufferId = baseState.readFramebuffer();
        restoreViewportX = baseState.viewportX();
        restoreViewportY = baseState.viewportY();
        restoreViewportW = baseState.viewportWidth();
        restoreViewportH = baseState.viewportHeight();
        renderStateCaptured = true;
    }

    private void restoreCapturedRenderState() {
        if (!renderStateCaptured) {
            return;
        }
        try {
            if (restoreRenderState != null) {
                restoreRenderState.restore();
            } else {
                GL30.glBindFramebuffer(
                        GL30.GL_DRAW_FRAMEBUFFER, restoreDrawFramebufferId);
                GL30.glBindFramebuffer(
                        GL30.GL_READ_FRAMEBUFFER, restoreReadFramebufferId);
                RenderSystem.viewport(restoreViewportX, restoreViewportY,
                        restoreViewportW, restoreViewportH);
            }
        } finally {
            restoreDrawFramebufferId = -1;
            restoreReadFramebufferId = -1;
            renderStateCaptured = false;
            restoreViewportX = 0;
            restoreViewportY = 0;
            restoreViewportW = 0;
            restoreViewportH = 0;
            restoreRenderState = null;
        }
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

    /**
     * State that crosses an externally visible density/composite transaction.
     * Iris owns raw GL programs and sampler bindings outside Minecraft's
     * RenderSystem cache, so those values must be restored explicitly as well
     * as the ordinary fixed-function draw state.
     */
    private record RenderTransactionState(
            FullscreenPassState baseState,
            boolean colorMaskRed,
            boolean colorMaskGreen,
            boolean colorMaskBlue,
            boolean colorMaskAlpha,
            int activeTexture,
            int[] shaderTextures,
            int[] textureBindings,
            int currentProgram,
            ShaderInstance shader,
            float shaderRed,
            float shaderGreen,
            float shaderBlue,
            float shaderAlpha) {
        private static final int PRESERVED_TEXTURE_UNITS = 5;

        static RenderTransactionState capture() {
            ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int[] shaderTextures = new int[PRESERVED_TEXTURE_UNITS];
            int[] textureBindings = new int[PRESERVED_TEXTURE_UNITS];
            for (int unit = 0; unit < textureBindings.length; unit++) {
                shaderTextures[unit] = RenderSystem.getShaderTexture(unit);
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                textureBindings[unit] = GL11.glGetInteger(
                        GL11.GL_TEXTURE_BINDING_2D);
            }
            GL13.glActiveTexture(activeTexture);
            float[] shaderColor = RenderSystem.getShaderColor();
            return new RenderTransactionState(
                    FullscreenPassState.capture(),
                    colorMask.get(0) != 0,
                    colorMask.get(1) != 0,
                    colorMask.get(2) != 0,
                    colorMask.get(3) != 0,
                    activeTexture,
                    shaderTextures,
                    textureBindings,
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    RenderSystem.getShader(),
                    shaderColor[0], shaderColor[1],
                    shaderColor[2], shaderColor[3]);
        }

        void restore() {
            baseState.restore();
            RenderSystem.colorMask(
                    colorMaskRed, colorMaskGreen,
                    colorMaskBlue, colorMaskAlpha);
            for (int unit = 0; unit < textureBindings.length; unit++) {
                // Restore both RenderSystem's logical sampler cache and the
                // raw binding Iris left on the unit. Restoring only one lets
                // the next shader apply overwrite the other with a Dark Ball
                // scratch attachment.
                RenderSystem.setShaderTexture(unit, shaderTextures[unit]);
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                RenderSystem.bindTexture(textureBindings[unit]);
                GL11.glBindTexture(
                        GL11.GL_TEXTURE_2D, textureBindings[unit]);
            }
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.setShaderColor(
                    shaderRed, shaderGreen, shaderBlue, shaderAlpha);
            RenderSystem.setShader(() -> shader);
            GL20.glUseProgram(currentProgram);
        }
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
