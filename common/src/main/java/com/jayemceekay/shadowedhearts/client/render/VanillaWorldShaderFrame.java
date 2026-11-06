package com.jayemceekay.shadowedhearts.client.render;

import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteFabulousRenderer;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPortalRenderer;
import com.jayemceekay.shadowedhearts.mixin.RenderTargetAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Shares one pre-combine depth copy and its matched world transforms across late vanilla shader effects. */
public final class VanillaWorldShaderFrame {
    private static final WorldShaderTarget TARGET = new WorldShaderTarget();
    private static Frame pending;
    private static Object resourceLevel;

    private record Frame(Object level, long frameTime, Camera camera, float partialTicks,
                         Matrix4f projection, Matrix4f modelView, int width, int height, int colorTexture) {}

    private VanillaWorldShaderFrame() {}

    public static void beginFrame() {
        pending = null;
        TARGET.invalidate();
        LuminousMoteFabulousRenderer.clear();
    }

    public static void onClientTick(Minecraft minecraft) {
        if (resourceLevel != minecraft.level) {
            clear();
            resourceLevel = minecraft.level;
        }
    }

    public static void capture(Camera camera, float partialTicks, Matrix4f projection, Matrix4f modelView) {
        pending = null;
        TARGET.invalidate();
        Minecraft mc = Minecraft.getInstance();
        onClientTick(mc);
        if (ShadowPokemonAuraSystem.isIrisShaderPackActive() || mc.level == null || camera == null
                || projection == null || modelView == null || !projection.isFinite() || !modelView.isFinite()
                || (!BallEmitters.hasWorldEffects() && !HeldBallSnagGlowRenderer.hasWorldHands()
                && !AuraReaderPulseRenderer.hasActivePulses() && !LuminousMoteFabulousRenderer.hasQueued()
                && !MoonwoundPortalRenderer.hasActivePortals())) return;
        var main = mc.getMainRenderTarget();
        var target = (RenderTargetAccessor) (Object) main;
        if (TARGET.captureDepth(target.getFrameBufferId(), target.getWidth(), target.getHeight())) {
            pending = new Frame(mc.level, mc.getFrameTimeNs(), camera, partialTicks,
                    new Matrix4f(projection), new Matrix4f(modelView),
                    target.getWidth(), target.getHeight(), main.getColorTextureId());
        }
    }

    public static void render() {
        Frame frame = pending;
        pending = null;
        if (frame == null) return;
        Minecraft mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        var target = (RenderTargetAccessor) (Object) main;
        try {
            if (ShadowPokemonAuraSystem.isIrisShaderPackActive() || mc.level != frame.level
                    || mc.getFrameTimeNs() != frame.frameTime || main.getColorTextureId() != frame.colorTexture
                    || target.getWidth() != frame.width || target.getHeight() != frame.height) return;
            try (var output = TARGET.open(frame.colorTexture)) {
                if (output == null) return;
                RenderSystem.setProjectionMatrix(frame.projection, VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.getModelViewMatrix().set(frame.modelView);
                BallEmitters.onRenderFBO(frame.camera, frame.partialTicks, frame.projection, frame.modelView);
                HeldBallSnagGlowRenderer.renderWorldHands(frame.camera, frame.partialTicks);
                LuminousMoteFabulousRenderer.render(frame.camera, frame.partialTicks);
                MoonwoundPortalRenderer.render(frame.camera, frame.partialTicks);
            }
            // The pulse samples saved depth. Keep that texture detached from its
            // output framebuffer to avoid an OpenGL texture feedback loop.
            try (var ignored = DensityFboPipeline.preserveRenderState()) {
                main.bindWrite(true);
                AuraReaderPulseRenderer.onRenderWorldCapturedDepth(frame.camera, frame.projection,
                        frame.modelView, frame.partialTicks, TARGET.depthTextureId());
            }
        } finally {
            TARGET.invalidate();
            LuminousMoteFabulousRenderer.clear();
        }
    }

    public static void clear() {
        pending = null;
        LuminousMoteFabulousRenderer.clear();
        TARGET.close();
        ColorOnlyShaderTarget.destroy();
    }
}
