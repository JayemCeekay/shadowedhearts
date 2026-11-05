package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DepthCopyFormat;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
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

import java.nio.IntBuffer;

/**
 * Half-resolution bloom for snag VFX, using the caller's scene target and
 * viewport. Iris can own both independently of Minecraft's main render target.
 * The world pass retains the caller's matched projection/model-view matrices;
 * only the fullscreen blur and composite temporarily install identity matrices.
 */
public final class SnagBloomFBO {
    private static final int BLUR_ITERATIONS = 2;

    private static RenderTarget bloomTarget;
    private static RenderTarget blurTempTarget;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static DensityFboPipeline.RenderStateScope bloomPassState;
    private static SceneTarget sceneTarget;
    private static boolean bloomReady;

    private SnagBloomFBO() {}

    private static void ensureTargets(int sourceWidth, int sourceHeight) {
        int width = Math.max(1, sourceWidth / 2);
        int height = Math.max(1, sourceHeight / 2);
        if (bloomTarget != null && blurTempTarget != null
                && lastWidth == width && lastHeight == height) {
            return;
        }

        destroyTargets();
        bloomTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        bloomTarget.setClearColor(0f, 0f, 0f, 0f);
        blurTempTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        blurTempTarget.setClearColor(0f, 0f, 0f, 0f);
        lastWidth = width;
        lastHeight = height;
    }

    /**
     * Captures the caller before target allocation or clearing, then copies the
     * active scene depth. A failed copy skips bloom so it cannot glow through
     * world geometry. Pair every successful begin with end in a finally block.
     */
    public static boolean beginBloomPass() {
        if (bloomPassState != null) {
            return false;
        }
        bloomReady = false;
        sceneTarget = null;
        if (ModShaders.SNAG_BLOOM_BLUR == null
                || ModShaders.SNAG_BLOOM_COMPOSITE == null) {
            return false;
        }

        bloomPassState = DensityFboPipeline.preserveRenderState();
        try {
            sceneTarget = SceneTarget.capture();
            if (sceneTarget.width() <= 0 || sceneTarget.height() <= 0) {
                endBloomPass();
                return false;
            }
            ensureTargets(sceneTarget.width(), sceneTarget.height());
            RenderSystem.colorMask(true, true, true, true);
            GL11.glColorMask(true, true, true, true);
            clearTargetUnscissored(bloomTarget);
            if (!blitSceneDepthToBloom()) {
                endBloomPass();
                return false;
            }

            bloomTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, lastWidth, lastHeight);
            bloomReady = true;
            return true;
        } catch (RuntimeException | Error failure) {
            endBloomPass();
            throw failure;
        }
    }

    /** Restores the exact caller state, including independent read/draw targets. */
    public static void endBloomPass() {
        DensityFboPipeline.RenderStateScope savedState = bloomPassState;
        bloomPassState = null;
        if (savedState != null) {
            savedState.close();
        }
    }

    /** Blurs the last successful pass and additively presents it into its scene. */
    public static void blurAndComposite() {
        if (!bloomReady || bloomPassState != null || sceneTarget == null
                || bloomTarget == null || blurTempTarget == null) {
            return;
        }
        bloomReady = false;
        ShaderInstance blurShader = ModShaders.SNAG_BLOOM_BLUR;
        ShaderInstance compositeShader = ModShaders.SNAG_BLOOM_COMPOSITE;
        if (blurShader == null || compositeShader == null) {
            return;
        }

        try (var ignored = DensityFboPipeline.preserveRenderState()) {
            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
            prepareFullscreenState();

            float texelWidth = 1.0f / lastWidth;
            float texelHeight = 1.0f / lastHeight;
            Uniform direction = blurShader.getUniform("Direction");
            Uniform blurRadius = blurShader.getUniform("BlurRadius");
            try {
                for (int i = 0; i < BLUR_ITERATIONS; i++) {
                    float radius = 1.0f + i * 0.5f;
                    clearTargetUnscissored(blurTempTarget);
                    blurTempTarget.bindWrite(false);
                    RenderSystem.viewport(0, 0, lastWidth, lastHeight);
                    RenderSystem.setShaderTexture(0, bloomTarget.getColorTextureId());
                    if (direction != null) direction.set(texelWidth, 0.0f);
                    if (blurRadius != null) blurRadius.set(radius);
                    RenderSystem.setShader(() -> blurShader);
                    drawFullScreenQuad();

                    clearTargetUnscissored(bloomTarget);
                    bloomTarget.bindWrite(false);
                    RenderSystem.viewport(0, 0, lastWidth, lastHeight);
                    RenderSystem.setShaderTexture(0, blurTempTarget.getColorTextureId());
                    if (direction != null) direction.set(0.0f, texelHeight);
                    if (blurRadius != null) blurRadius.set(radius);
                    RenderSystem.setShader(() -> blurShader);
                    drawFullScreenQuad();
                }
            } finally {
                blurShader.clear();
            }

            // Restore only the draw target here; the surrounding scope preserves
            // the independent read target and the rest of Iris' render state.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneTarget.framebuffer());
            RenderSystem.viewport(sceneTarget.x(), sceneTarget.y(),
                    sceneTarget.width(), sceneTarget.height());
            RenderSystem.setShaderTexture(0, bloomTarget.getColorTextureId());
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

            float intensity = com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                    .getInstance().getClientConfig().snagBloomIntensity();
            Uniform bloomIntensity = compositeShader.getUniform("BloomIntensity");
            if (bloomIntensity != null) bloomIntensity.set(intensity);
            try {
                RenderSystem.setShader(() -> compositeShader);
                drawFullScreenQuad();
            } finally {
                compositeShader.clear();
            }
        }
    }

    public static boolean shouldRun() {
        return !SnagCaptureVfx.getActiveInstances().isEmpty();
    }

    private static void prepareFullscreenState() {
        RenderSystem.colorMask(true, true, true, true);
        GL11.glColorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        GL11.glDepthMask(false);
        RenderSystem.disableDepthTest();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
    }

    private static void clearTargetUnscissored(RenderTarget target) {
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        RenderSystem.depthMask(true);
        GL11.glDepthMask(true);
        try {
            target.clear(Minecraft.ON_OSX);
        } finally {
            RenderSystem.depthMask(depthMask);
            GL11.glDepthMask(depthMask);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static boolean blitSceneDepthToBloom() {
        if (!DepthCopyFormat.prepareDepthTexture(sceneTarget.framebuffer(),
                bloomTarget.getDepthTextureId(), lastWidth, lastHeight)) {
            return false;
        }
        int bloomFramebuffer = ((RenderTargetAccessor) (Object) bloomTarget).getFrameBufferId();
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sceneTarget.framebuffer());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bloomFramebuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                    || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return false;
            }
            for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
                // Discard preceding errors so the copy reports its own result.
            }
            GL30.glBlitFramebuffer(sceneTarget.x(), sceneTarget.y(),
                    sceneTarget.x() + sceneTarget.width(), sceneTarget.y() + sceneTarget.height(),
                    0, 0, lastWidth, lastHeight, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void drawFullScreenQuad() {
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        buffer.addVertex( 1f, -1f, 0f).setUv(1f, 0f);
        buffer.addVertex( 1f,  1f, 0f).setUv(1f, 1f);
        buffer.addVertex(-1f,  1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private record SceneTarget(int framebuffer, int x, int y, int width, int height) {
        static SceneTarget capture() {
            IntBuffer viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return new SceneTarget(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        }
    }

    private static void destroyTargets() {
        if (bloomTarget != null) {
            bloomTarget.destroyBuffers();
            bloomTarget = null;
        }
        if (blurTempTarget != null) {
            blurTempTarget.destroyBuffers();
            blurTempTarget = null;
        }
        lastWidth = -1;
        lastHeight = -1;
    }

    /** Releases resources without carrying a pending pass into the next reload. */
    public static void destroy() {
        endBloomPass();
        bloomReady = false;
        sceneTarget = null;
        destroyTargets();
    }
}