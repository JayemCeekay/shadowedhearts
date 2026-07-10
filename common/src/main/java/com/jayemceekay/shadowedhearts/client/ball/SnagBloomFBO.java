package com.jayemceekay.shadowedhearts.client.ball;

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
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/**
 * Manages offscreen framebuffers for the snag ball bloom/glow pipeline.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link #beginBloomPass()} — bind half-res bloom FBO, clear it</li>
 *   <li>Caller renders bright VFX (beams, sparks, flashes, orb) into this FBO</li>
 *   <li>{@link #endBloomPass()} — restore main FBO</li>
 *   <li>{@link #blurAndComposite()} — two-pass Gaussian blur, then additive composite over scene</li>
 * </ol>
 *
 * <p>The bloom FBO runs at half resolution for performance. Two blur iterations
 * (H+V each) produce a soft glow that is then additively blended onto the
 * main framebuffer.
 */
public final class SnagBloomFBO {

    private static final int BLUR_ITERATIONS = 2;
    private static final float BLOOM_INTENSITY = 0.8f;

    private static RenderTarget bloomTarget;
    private static RenderTarget blurTempTarget;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    // State capture for begin/end pass
    private static int restoreFramebufferId = -1;
    private static int restoreViewportX;
    private static int restoreViewportY;
    private static int restoreViewportW;
    private static int restoreViewportH;

    // Matrix state capture for begin/end pass
    private static Matrix4f savedModelView;
    private static Matrix4f savedProjection;

    private SnagBloomFBO() {}

    /**
     * Ensures both FBOs exist and match half the main framebuffer resolution.
     */
    private static void ensureTargets() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int mw = ((RenderTargetAccessor) (Object) main).getWidth();
        int mh = ((RenderTargetAccessor) (Object) main).getHeight();
        int hw = Math.max(1, mw / 2);
        int hh = Math.max(1, mh / 2);

        if (bloomTarget == null || lastWidth != hw || lastHeight != hh) {
            if (bloomTarget != null) bloomTarget.destroyBuffers();
            if (blurTempTarget != null) blurTempTarget.destroyBuffers();

            bloomTarget = new TextureTarget(hw, hh, true, Minecraft.ON_OSX);
            bloomTarget.setClearColor(0f, 0f, 0f, 0f);

            blurTempTarget = new TextureTarget(hw, hh, false, Minecraft.ON_OSX);
            blurTempTarget.setClearColor(0f, 0f, 0f, 0f);

            lastWidth = hw;
            lastHeight = hh;

            // Briefly bind each to initialize, then restore previous state
            int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

            bloomTarget.bindWrite(false);
            blurTempTarget.bindWrite(false);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            RenderSystem.viewport(prevViewport.get(0), prevViewport.get(1),
                    prevViewport.get(2), prevViewport.get(3));
        }
    }

    /**
     * Binds the bloom FBO for the bright VFX pass. Clears it and sets the viewport.
     *
     * @return true if the bloom FBO is ready; false if setup failed
     */
    public static boolean beginBloomPass() {
        ShaderInstance blur = ModShaders.SNAG_BLOOM_BLUR;
        ShaderInstance comp = ModShaders.SNAG_BLOOM_COMPOSITE;
        if (blur == null || comp == null) return false;

        ensureTargets();
        if (bloomTarget == null) return false;

        captureCurrentRenderState();

        // Blit main depth buffer to bloom FBO so VFX are depth-tested against world
        blitMainDepthToBloom();

        bloomTarget.clear(Minecraft.ON_OSX);

        // Re-blit depth after clear (clear wipes depth too)
        blitMainDepthToBloom();

        bloomTarget.bindWrite(false);
        RenderSystem.viewport(0, 0, lastWidth, lastHeight);

        // Save current matrices and set up proper 3D view matrix so that
        // billboard VFX (diffraction spikes, convergence rings, lens flares)
        // are correctly positioned even when rendering at a late stage (END /
        // AFTER_PARTICLES) where the level renderer may have reset the GL state.
        savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Quaternionf viewRot = new org.joml.Quaternionf(camera.rotation()).conjugate();
        RenderSystem.getModelViewMatrix().set(new Matrix4f().rotation(viewRot));

        return true;
    }

    /**
     * Restores the main framebuffer and matrices after the bright VFX pass.
     */
    public static void endBloomPass() {
        // Restore matrices saved in beginBloomPass()
        if (savedModelView != null) {
            RenderSystem.getModelViewMatrix().set(savedModelView);
            savedModelView = null;
        }
        if (savedProjection != null) {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            savedProjection = null;
        }
        restoreCapturedRenderState();
    }

    /**
     * Runs two-pass Gaussian blur on the bloom texture, then composites it
     * additively onto the main framebuffer.
     */
    public static void blurAndComposite() {
        if (bloomTarget == null || blurTempTarget == null) return;

        ShaderInstance blurShader = ModShaders.SNAG_BLOOM_BLUR;
        ShaderInstance compShader = ModShaders.SNAG_BLOOM_COMPOSITE;
        if (blurShader == null || compShader == null) return;

        // Save current state
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        IntBuffer prevViewport = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        int pvX = prevViewport.get(0), pvY = prevViewport.get(1);
        int pvW = prevViewport.get(2), pvH = prevViewport.get(3);

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

        // ── Blur passes ──────────────────────────────────────────────────
        for (int i = 0; i < BLUR_ITERATIONS; i++) {
            float radius = 1.0f + i * 0.5f;

            // Horizontal: bloom → blurTemp
            blurTempTarget.clear(Minecraft.ON_OSX);
            blurTempTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, bloomTarget.getColorTextureId());

            Uniform uDir = blurShader.getUniform("Direction");
            Uniform uRad = blurShader.getUniform("BlurRadius");
            if (uDir != null) uDir.set(texelW, 0.0f);
            if (uRad != null) uRad.set(radius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();

            // Vertical: blurTemp → bloom
            bloomTarget.clear(Minecraft.ON_OSX);
            bloomTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            RenderSystem.setShaderTexture(0, blurTempTarget.getColorTextureId());

            if (uDir != null) uDir.set(0.0f, texelH);
            if (uRad != null) uRad.set(radius);

            RenderSystem.setShader(() -> blurShader);
            drawFullScreenQuad();
        }

        blurShader.clear();

        // ── Composite: additive blend bloom onto main framebuffer ─────
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        RenderSystem.viewport(pvX, pvY, pvW, pvH);

        RenderSystem.setShaderTexture(0, bloomTarget.getColorTextureId());
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        float intensity = com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                .getInstance().getClientConfig().snagBloomIntensity();
        Uniform uIntensity = compShader.getUniform("BloomIntensity");
        if (uIntensity != null) uIntensity.set(intensity);

        RenderSystem.setShader(() -> compShader);
        drawFullScreenQuad();

        compShader.clear();

        // ── Restore state ────────────────────────────────────────────────
        RenderSystem.getModelViewMatrix().set(savedMV);
        RenderSystem.setProjectionMatrix(savedProj, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * @return true if there are active snag VFX that benefit from bloom
     */
    public static boolean shouldRun() {
        return !SnagCaptureVfx.getActiveInstances().isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static void drawFullScreenQuad() {
        var tess = Tesselator.getInstance();
        var buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        buf.addVertex( 1f, -1f, 0f).setUv(1f, 0f);
        buf.addVertex( 1f,  1f, 0f).setUv(1f, 1f);
        buf.addVertex(-1f,  1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private static void blitMainDepthToBloom() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int mainFbo = ((RenderTargetAccessor) (Object) main).getFrameBufferId();
        int bloomFbo = ((RenderTargetAccessor) (Object) bloomTarget).getFrameBufferId();
        int mainW = ((RenderTargetAccessor) (Object) main).getWidth();
        int mainH = ((RenderTargetAccessor) (Object) main).getHeight();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bloomFbo);
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
    }

    /**
     * Releases all FBO resources. Call on client shutdown or resource reload.
     */
    public static void destroy() {
        if (bloomTarget != null) { bloomTarget.destroyBuffers(); bloomTarget = null; }
        if (blurTempTarget != null) { blurTempTarget.destroyBuffers(); blurTempTarget = null; }
        lastWidth = -1;
        lastHeight = -1;
    }
}
