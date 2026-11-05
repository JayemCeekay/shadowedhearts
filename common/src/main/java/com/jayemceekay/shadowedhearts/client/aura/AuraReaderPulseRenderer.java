package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.ClientEffects;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.render.ColorOnlyShaderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.architectury.platform.Platform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuraReaderPulseRenderer {
    private static final List<PulseInstance> PULSES = Collections.synchronizedList(new ArrayList<>());
    public static final IrisHandler IRIS_HANDLER = Platform.isModLoaded("iris") ? new IrisHandlerImpl() : null;
    private static final AuraPulseTextureUpload PULSE_TEXTURE = new AuraPulseTextureUpload();
    private static int maxTextureSizeCached = -1;

    public static void spawnPulse(Vec3 origin) {
            spawnPulse(origin, 0.0f, 1.0f, 1.0f, 128.0f);
    }

    public static void spawnPulse(Vec3 origin, float r, float g, float b, float distance) {
        // Ensure pulses are added on the main client thread to avoid concurrent modification
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> addPulse(origin, r, g, b, distance));
            return;
        }
        addPulse(origin, r, g, b, distance);
    }

    private static void addPulse(Vec3 origin, float r, float g, float b, float distance) {
        if (!ClientEffects.scanWave()) return;
        synchronized (PULSES) {
            PULSES.add(new PulseInstance(origin, r, g, b, distance));
        }
    }

    public static void clearPulses() {
        // Clear on the main thread for consistency
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> {
                synchronized (PULSES) {
                    PULSES.clear();
                }
            });
            return;
        }
        synchronized (PULSES) {
            PULSES.clear();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> {
                synchronized (PULSES) {
                    PULSES.removeIf(PulseInstance::tick);
                }
            });
            return;
        }
        synchronized (PULSES) {
            PULSES.removeIf(PulseInstance::tick);
        }
    }

    public static void init() {
        //PostWorldRenderCallback.register(AuraPulseRenderer::onWorldRendered);
    }

    public static boolean hasActivePulses() {
        if (!ClientEffects.scanWave()) {
            clearPulses();
            return false;
        }
        synchronized (PULSES) {
            return !PULSES.isEmpty();
        }
    }

    public static void onWorldRendered(PoseStack matrices, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, Camera camera, float tickDelta) {
        onRenderWorld(camera, projectionMatrix, modelViewMatrix, tickDelta);
    }

    public static void renderIris() {
        if (IRIS_HANDLER != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getCameraEntity() != null && mc.level != null) {
                Camera camera = mc.gameRenderer.getMainCamera();
                Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
                Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
                float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaTicks() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                onRenderWorld(camera, proj, view, partialTick);
            }
        }
    }

    public static void onRenderWorld(Camera camera, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, float partialTicks) {
        if (!hasActivePulses() || camera == null || Minecraft.getInstance().level == null) return;
        if (IRIS_HANDLER != null && IRIS_HANDLER.isShaderPackInUse()) {
            IrisHandler.IrisRenderingSnapshot frame = IRIS_HANDLER.getIrisRenderingSnapshot();
            if (frame == null || IRIS_HANDLER.isShadowRenderActive()
                    || frame.renderWidth <= 0 || frame.renderHeight <= 0) return;
            try (var ignored = DensityFboPipeline.preserveRenderState()) {
                RenderSystem.viewport(0, 0, frame.renderWidth, frame.renderHeight);
                onRenderWorldCapturedDepth(camera, frame.projectionMatrix, frame.modelViewMatrix,
                        partialTicks, frame.depthTexture);
            }
        } else {
            // Immediate callers still own scene depth here. Loader callbacks
            // use the retained pre-combine depth through the explicit overload.
            try (var ignored = DensityFboPipeline.preserveRenderState()) {
                DepthCapture.captureIfNeeded();
            }
            onRenderWorldCapturedDepth(camera, projectionMatrix, modelViewMatrix,
                    partialTicks, DepthCapture.textureId());
        }
    }

    /** Draws into the caller's target using its retained scene depth and matching world matrices. */
    public static void onRenderWorldCapturedDepth(Camera camera, Matrix4f projectionMatrix,
                                                   Matrix4f modelViewMatrix, float partialTicks,
                                                   int depthTexture) {
        if (!hasActivePulses()) return;
        if (camera == null || depthTexture <= 0 || projectionMatrix == null || modelViewMatrix == null
                || !projectionMatrix.isFinite() || !modelViewMatrix.isFinite()) return;
        List<PulseInstance> pulsesSnapshot;
        synchronized (PULSES) {
            if (PULSES.isEmpty()) return;
            pulsesSnapshot = List.copyOf(PULSES);
        }

        try (var output = ColorOnlyShaderTarget.open()) {
            if (output == null) return;
            if (ModShaders.AURA_PULSE == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Matrix4f proj = projectionMatrix;
            Matrix4f view = modelViewMatrix;

            Matrix4f invProj = new Matrix4f(proj).invert();
            Matrix4f invView = new Matrix4f(view).invert();
            if (!invProj.isFinite() || !invView.isFinite()) return;

            ModShaders.AURA_PULSE.setSampler("uDepth", depthTexture);


            Vec3 camPos = camera.getPosition();

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);

            int count = pulsesSnapshot.size();

            // Cache max texture size and clamp width to be safe
            if (maxTextureSizeCached <= 0) {
                maxTextureSizeCached = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
                if (maxTextureSizeCached <= 0) {
                    // Fallback sanity
                    maxTextureSizeCached = 4096;
                }
            }

            // Each pulse is 2 vec4s (origin+radius, color+padding) = 32 bytes
            int width = count * 2;
            if (width > maxTextureSizeCached) {
                // Clamp number of pulses to fit texture width
                int maxPulses = Math.max(0, maxTextureSizeCached / 2);
                count = Math.min(count, maxPulses);
                width = count * 2;
            }

            int uploadCount = count;
            if (!PULSE_TEXTURE.upload(width, maxTextureSizeCached, mapped -> {
                for (int i = 0; i < uploadCount; i++) {
                    PulseInstance pulse = pulsesSnapshot.get(i);
                    mapped.putFloat((float) (pulse.origin.x - camPos.x));
                    mapped.putFloat((float) (pulse.origin.y - camPos.y));
                    mapped.putFloat((float) (pulse.origin.z - camPos.z));
                    mapped.putFloat(pulse.getRadius(partialTicks));
                    mapped.putFloat(pulse.r);
                    mapped.putFloat(pulse.g);
                    mapped.putFloat(pulse.b);
                    mapped.putFloat(pulse.distance);
                }
            })) return;

            ModShaders.AURA_PULSE.setSampler("uPulseTexture", PULSE_TEXTURE.textureId());

            if (ModShaders.AURA_PULSE.safeGetUniform("uThickness") != null) {
                ModShaders.AURA_PULSE.safeGetUniform("uThickness").set(2.0f);
            }
            if (ModShaders.AURA_PULSE.safeGetUniform("uPulseCount") != null) {
                ModShaders.AURA_PULSE.safeGetUniform("uPulseCount").set((float) count);
            }

            // Set matrices
            if (ModShaders.AURA_PULSE.safeGetUniform("uInvProj") != null) {
                ModShaders.AURA_PULSE.safeGetUniform("uInvProj").set(invProj);
            }
            if (ModShaders.AURA_PULSE.safeGetUniform("uInvView") != null) {
                ModShaders.AURA_PULSE.safeGetUniform("uInvView").set(invView);
            }

            // Set projection and modelview matrices directly for the shader core
            // Use identity matrices for full-screen quad in clip space
            if (ModShaders.AURA_PULSE.MODEL_VIEW_MATRIX != null)
                ModShaders.AURA_PULSE.MODEL_VIEW_MATRIX.set(new Matrix4f());
            if (ModShaders.AURA_PULSE.PROJECTION_MATRIX != null)
                ModShaders.AURA_PULSE.PROJECTION_MATRIX.set(new Matrix4f());

            // Upload uniforms before each draw call
            ModShaders.AURA_PULSE.apply();

            RenderSystem.setShader(() -> ModShaders.AURA_PULSE);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            // Render a full-screen quad in clip space
            buf.addVertex(-1.0f, -1.0f, 0.0f).setUv(0.0f, 0.0f);
            buf.addVertex(1.0f, -1.0f, 0.0f).setUv(1.0f, 0.0f);
            buf.addVertex(1.0f, 1.0f, 0.0f).setUv(1.0f, 1.0f);
            buf.addVertex(-1.0f, 1.0f, 0.0f).setUv(0.0f, 1.0f);
            BufferUploader.drawWithShader(buf.buildOrThrow());

            ModShaders.AURA_PULSE.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class PulseInstance {
        final Vec3 origin;
        final float r, g, b, distance;
        int age = 0;
        static final int MAX_AGE = 120; // 3 seconds

        PulseInstance(Vec3 origin, float r, float g, float b, float distance) {
            this.origin = origin;
            this.r = r;
            this.g = g;
            this.b = b;
            this.distance = distance;
        }

        boolean tick() {
            age++;
            return age >= MAX_AGE;
        }

        float getRadius(float partialTicks) {
            return (age + partialTicks) * 1.5f; // Expands at 1.5 blocks per tick
        }
    }

}
