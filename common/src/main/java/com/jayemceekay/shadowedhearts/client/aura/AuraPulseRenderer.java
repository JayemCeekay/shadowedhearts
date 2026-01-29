package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.architectury.platform.Platform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AuraPulseRenderer {
    private static final List<PulseInstance> PULSES = new ArrayList<>();
    public static final IrisHandler IRIS_HANDLER = Platform.isModLoaded("iris") ? new IrisHandlerImpl() : null;
    private static int ssboId = -1;

    public static void spawnPulse(Vec3 origin) {
        spawnPulse(origin, 0.0f, 1.0f, 1.0f, 128.0f);
    }

    public static void spawnPulse(Vec3 origin, float r, float g, float b, float distance) {
        PULSES.add(new PulseInstance(origin, r, g, b, distance));
    }

    public static void clearPulses() {
        PULSES.clear();
    }

    public static void tick() {
        PULSES.removeIf(PulseInstance::tick);
    }

    public static void init() {
        //PostWorldRenderCallback.register(AuraPulseRenderer::onWorldRendered);
    }

    public static void onWorldRendered(PoseStack matrices, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, Camera camera, float tickDelta) {
        onRenderWorld(camera, projectionMatrix, modelViewMatrix, tickDelta);
    }

    public static void renderIris() {
        if (IRIS_HANDLER != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer != null && mc.getCameraEntity() != null) {
                Camera camera = mc.gameRenderer.getMainCamera();
                Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
                Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
                float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaTicks() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                onRenderWorld(camera, proj, view, partialTick);
            }
        }
    }

    public static void onRenderWorld(Camera camera, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, float partialTicks) {
        if (PULSES.isEmpty()) return;
        if (ModShaders.AURA_PULSE == null) return;
        Minecraft mc = Minecraft.getInstance();
         DepthCapture.captureIfNeeded();

        Matrix4f proj = projectionMatrix;
        Matrix4f view = modelViewMatrix;

        int diffuseTexture = mc.getMainRenderTarget().getColorTextureId();
        int depthTexture = DepthCapture.textureId();

        if (IRIS_HANDLER != null && IRIS_HANDLER.isShaderPackInUse()) {
            IrisHandler.IrisRenderingSnapshot snapshot = IRIS_HANDLER.getIrisRenderingSnapshot();
            if (snapshot != null) {
                if (snapshot.diffuseTexture != -1) {
                    diffuseTexture = snapshot.diffuseTexture;
                }
                depthTexture = snapshot.depthTexture;
                proj = snapshot.projectionMatrix;
                view = snapshot.modelViewMatrix;
            }
        }

        Matrix4f invProj = new Matrix4f(proj).invert();
        Matrix4f invView = new Matrix4f(view).invert();

        ModShaders.AURA_PULSE.setSampler("DiffuseSampler", diffuseTexture);
        ModShaders.AURA_PULSE.setSampler("uDepth", depthTexture);


        Vec3 camPos = camera.getPosition();

        RenderSystem.enableBlend();
        //RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        int count = PULSES.size();
        if (ssboId == -1) {
            ssboId = GlStateManager._glGenBuffers();
        }

        // Each pulse is 2 vec4s (origin+radius, color+padding) = 32 bytes
        ByteBuffer buffer = MemoryUtil.memAlloc(count * 32);
        for (int i = 0; i < count; i++) {
            PulseInstance pulse = PULSES.get(i);
            float radius = pulse.getRadius(partialTicks);
            Vector3f origin = new Vector3f((float) (pulse.origin.x - camPos.x), (float) (pulse.origin.y - camPos.y), (float) (pulse.origin.z - camPos.z));

            buffer.putFloat(origin.x);
            buffer.putFloat(origin.y);
            buffer.putFloat(origin.z);
            buffer.putFloat(radius);

            buffer.putFloat(pulse.r);
            buffer.putFloat(pulse.g);
            buffer.putFloat(pulse.b);
            buffer.putFloat(pulse.distance);
        }
        buffer.flip();

        GlStateManager._glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, buffer, GL43C.GL_DYNAMIC_DRAW);
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, ssboId);
        MemoryUtil.memFree(buffer);

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
        if (ModShaders.AURA_PULSE.MODEL_VIEW_MATRIX != null) ModShaders.AURA_PULSE.MODEL_VIEW_MATRIX.set(new Matrix4f());
        if (ModShaders.AURA_PULSE.PROJECTION_MATRIX != null) ModShaders.AURA_PULSE.PROJECTION_MATRIX.set(new Matrix4f());

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
        GlStateManager._glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
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
