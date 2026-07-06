package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/**
 * Manages offscreen framebuffers for the penumbra trail metaball density pipeline.
 * <p>
 * Pipeline:
 * <ol>
 *   <li>{@link #beginDensityPass()} — bind half-res density FBO, clear it</li>
 *   <li>Caller renders particles as density splats with additive blending (GL_ONE, GL_ONE)</li>
 *   <li>{@link #endDensityPass()} — restore main FBO</li>
 *   <li>{@link #blur()} — 2-pass separable Kawase blur (density → temp, temp → density)</li>
 *   <li>{@link #composite()} — full-screen quad mapping blurred density to shadow aura colors</li>
 * </ol>
 */
public final class PenumbraDensityFBO {

    /** Number of Kawase blur iterations (each iteration = H pass + V pass). */
    private static final int BLUR_ITERATIONS = 1;

    private static RenderTarget densityTarget;
    private static RenderTarget blurTempTarget;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static int restoreFramebufferId = -1;
    private static int restoreViewportX;
    private static int restoreViewportY;
    private static int restoreViewportW;
    private static int restoreViewportH;

    private PenumbraDensityFBO() {}

    /**
     * Ensures both FBOs exist and match half the main framebuffer resolution.
     */
    private static void ensureTargets() {
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
            RenderSystem.viewport(prevViewport.get(0), prevViewport.get(1), prevViewport.get(2), prevViewport.get(3));
        }
    }

    /**
     * Binds the density FBO for the density splat pass. Clears it and sets the viewport.
     */
    public static boolean beginDensityPass() {
        ensureTargets();
        if (densityTarget == null) {
            return false;
        }

        captureCurrentRenderState();

        // Clear color+depth first, then blit the main depth buffer on top.
        // clear() wipes both channels, so the blit must come after to preserve the depth data.
        densityTarget.clear(Minecraft.ON_OSX);

        // Blit the main framebuffer's depth buffer to the density FBO at half-res.
        // This allows density splats to be depth-tested against world geometry,
        // so particles behind terrain/blocks are correctly occluded.
        blitMainDepthToDensity();

        densityTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);

        return true;
    }

    /**
     * Restores the main framebuffer and viewport after the density splat pass.
     */
    public static void endDensityPass() {
        restoreCapturedRenderState();
    }

    /**
     * Runs multi-iteration Kawase blur on the density texture.
     * Each iteration: density → blurTemp (horizontal), blurTemp → density (vertical).
     */
    public static int blur() {
        ShaderInstance blurShader = ModShaders.PENUMBRA_BLUR;
        if (blurShader == null || densityTarget == null || blurTempTarget == null) return 0;

        int prevFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        int prevViewportX = prevViewport.get(0);
        int prevViewportY = prevViewport.get(1);
        int prevViewportW = prevViewport.get(2);
        int prevViewportH = prevViewport.get(3);

        // Save current RenderSystem matrices and set identity for full-screen quad rendering.
        // BufferUploader.drawWithShader() reads from RenderSystem and overwrites shader uniforms,
        // so we must set matrices on RenderSystem directly.
        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        // Replace mode — not additive, just overwrite
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        float texelW = 1.0f / lastWidth;
        float texelH = 1.0f / lastHeight;

        int iterationsRan = 0;

        for (int i = 0; i < BLUR_ITERATIONS; i++) {
            float radius = 0.45f;  // balanced radius: smooths jitter while preserving wispy edges

            // --- Horizontal pass: density → blurTemp ---
            blurTempTarget.clear(Minecraft.ON_OSX);
            blurTempTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId());

            Uniform uDir = blurShader.getUniform("Direction");
            Uniform uRad = blurShader.getUniform("BlurRadius");
            if (uDir != null) uDir.set(texelW, 0.0f);
            if (uRad != null) uRad.set(radius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();

            // --- Vertical pass: blurTemp → density ---
            densityTarget.clear(Minecraft.ON_OSX);
            densityTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, blurTempTarget.getColorTextureId());

            if (uDir != null) uDir.set(0.0f, texelH);
            if (uRad != null) uRad.set(radius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();

            iterationsRan++;
        }

        blurShader.clear();

        // Restore the previously bound framebuffer and viewport.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFramebufferId);
        RenderSystem.viewport(prevViewportX, prevViewportY, prevViewportW, prevViewportH);

        // Restore original RenderSystem matrices
        RenderSystem.getModelViewMatrix().set(savedMV);
        RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        return iterationsRan;
    }

    /**
     * Composites the blurred density buffer onto the main framebuffer.
     */
    public static boolean composite() {
        if (densityTarget == null) return false;

        Minecraft mc = Minecraft.getInstance();
        ShaderInstance shader = ModShaders.PENUMBRA_COMPOSITE;
        if (shader == null) return false;

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewportWidth = Math.max(1, viewport.get(2));
        int viewportHeight = Math.max(1, viewport.get(3));

        // Bind blurred density texture
        RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId());

        // Set time uniform for animated noise in composite shader
        float gameTime = 0f;
        if (mc.level != null) {
            gameTime = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        }

        Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) uGameTime.set(gameTime / 1200.0f);
        Uniform uScreenSize = shader.getUniform("ScreenSize");
        if (uScreenSize != null) uScreenSize.set((float) viewportWidth, (float) viewportHeight);

        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        // Set identity matrices on RenderSystem directly for clip-space full-screen quad.
        // BufferUploader.drawWithShader() reads from RenderSystem and overwrites shader uniforms.
        Matrix4f savedMV = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);

        drawFullScreenQuad();

        shader.clear();

        // Restore original RenderSystem matrices and state
        RenderSystem.getModelViewMatrix().set(savedMV);
        RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        return true;
    }

    /**
     * Draws a clip-space (-1 to 1) full-screen quad.
     */
    private static void drawFullScreenQuad() {
        var tess = Tesselator.getInstance();
        var buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        buf.addVertex( 1f, -1f, 0f).setUv(1f, 0f);
        buf.addVertex( 1f,  1f, 0f).setUv(1f, 1f);
        buf.addVertex(-1f,  1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    /**
     * Copies the main framebuffer's depth buffer into the density FBO's depth buffer
     * so that density splats are occluded by world geometry (terrain, blocks, etc.).
     * Uses GL_NEAREST filter since depth values should not be interpolated.
     */
    private static void blitMainDepthToDensity() {
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

    private static void captureCurrentRenderState() {
        restoreFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        restoreViewportX = viewport.get(0);
        restoreViewportY = viewport.get(1);
        restoreViewportW = viewport.get(2);
        restoreViewportH = viewport.get(3);
    }

    private static void restoreCapturedRenderState() {
        if (restoreFramebufferId >= 0) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, restoreFramebufferId);
            RenderSystem.viewport(restoreViewportX, restoreViewportY, restoreViewportW, restoreViewportH);
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

    /**
     * Destroys all FBO resources. Called on shutdown or resource reload.
     */
    public static void destroy() {
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
}
