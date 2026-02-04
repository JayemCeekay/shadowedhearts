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
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuraPulseRenderer {
    private static final List<PulseInstance> PULSES = Collections.synchronizedList(new ArrayList<>());
    public static final IrisHandler IRIS_HANDLER = Platform.isModLoaded("iris") ? new IrisHandlerImpl() : null;
    private static int pulseTextureId = -1;
    // Use a Pixel Unpack Buffer (PBO) for robust uploads across drivers
    private static int pulsePboId = 0;
    private static int pboCapacityTexels = 0; // number of RGBA texels capacity in the PBO/texture
    private static int allocatedTextureWidthCapacity = 0; // texture storage width currently allocated (texels)
    private static int maxTextureSizeCached = -1;
    private static final int INTERNAL_FORMAT = GL30.GL_RGBA16F; // friendlier than RGBA32F on many drivers

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
        List<PulseInstance> pulsesSnapshot;
        synchronized (PULSES) {
            if (PULSES.isEmpty()) return;
            pulsesSnapshot = List.copyOf(PULSES);
        }

        try {
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

            int count = pulsesSnapshot.size();
            if (pulseTextureId == -1) {
                pulseTextureId = GlStateManager._genTexture();
            }

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

            // Prepare PBO capacity (texel = RGBA float = 16 bytes)
            int requiredTexels = Math.max(2, width); // ensure at least 2 texels
            if (pulsePboId == 0) {
                pulsePboId = GL15.glGenBuffers();
            }

            int prevUnpackPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pulsePboId);

            // Grow capacity using next power of two to avoid frequent reallocs
            if (pboCapacityTexels < requiredTexels) {
                int newCapacity = nextPow2(requiredTexels);
                newCapacity = Math.min(newCapacity, maxTextureSizeCached);
                int newBytes = newCapacity * 16;
                GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, newBytes, GL15.GL_STREAM_DRAW);
                pboCapacityTexels = newCapacity;
            }

            // Map only the portion we will write this frame (guard against zero-length mapping)
            int writeBytes = width * 16;
            if (writeBytes > 0) {
                ByteBuffer mapped = GL30.glMapBufferRange(
                        GL21.GL_PIXEL_UNPACK_BUFFER,
                        0,
                        writeBytes,
                        GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_RANGE_BIT
                );

                if (mapped != null) {
                    mapped.clear();
                    for (int i = 0; i < count; i++) {
                        PulseInstance pulse = pulsesSnapshot.get(i);
                        float radius = pulse.getRadius(partialTicks);
                        Vector3f origin = new Vector3f((float) (pulse.origin.x - camPos.x), (float) (pulse.origin.y - camPos.y), (float) (pulse.origin.z - camPos.z));

                        mapped.putFloat(origin.x);
                        mapped.putFloat(origin.y);
                        mapped.putFloat(origin.z);
                        mapped.putFloat(radius);

                        mapped.putFloat(pulse.r);
                        mapped.putFloat(pulse.g);
                        mapped.putFloat(pulse.b);
                        mapped.putFloat(pulse.distance);
                    }
                    mapped.flip();
                    GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
                }
            }

            // Bind the texture and ensure allocation to capacity (avoid resize churn on count changes)
            RenderSystem.activeTexture(GL13.GL_TEXTURE2);
            RenderSystem.bindTexture(pulseTextureId);

            if (allocatedTextureWidthCapacity != pboCapacityTexels) {
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D, 0, INTERNAL_FORMAT, pboCapacityTexels, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, 0
                );
                allocatedTextureWidthCapacity = pboCapacityTexels;
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            }

            // Upload from PBO with offset 0 (driver reads from GL-owned storage). Guard against zero-sized upload.
            if (width > 0) {
                GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D, 0, 0, 0, width, 1, GL11.GL_RGBA, GL11.GL_FLOAT, 0L
                );
            }

            // Restore previous PBO binding
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevUnpackPbo);

            ModShaders.AURA_PULSE.setSampler("uPulseTexture", pulseTextureId);

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
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
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

    private static int nextPow2(int x) {
        if (x <= 1) return 1;
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }
}
