package com.jayemceekay.shadowedhearts.client.particle;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dedicated penumbra puff manager and density pipeline entrypoint.
 * <p>
 * This avoids ParticleEngine mixin coupling by owning its own puff list and
 * rendering the density FBO passes from stable platform render callbacks.
 */
public final class PenumbraTrailSystem {

    private static final ResourceLocation DENSITY_SPRITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/particle/penumbra_trail.png"
    );

    private static final int FULLBRIGHT = 0x00F000F0;
    private static final int MAX_PUFFS = 4096;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Puff> ACTIVE = new ArrayList<>();

    private static long lastTickGameTime = Long.MIN_VALUE;
    private static long lastRenderFrameToken = Long.MIN_VALUE;

    private PenumbraTrailSystem() {}

    public static void registerPuff(double x, double y, double z, double vx, double vy, double vz) {
        float roll = RANDOM.nextFloat();
        float baseSize;
        int lifetime;
        if (roll < 0.80f) {
            // Core Body (80%): Provides a consistent, sleek trail width
            baseSize = 0.55f + RANDOM.nextFloat() * 0.20f;  // Range: 0.55 - 0.75
            lifetime = 26 + RANDOM.nextInt(10); // Range: 16 - 26 ticks
        } else if (roll < 0.95f) {
            // Fine Wisps (15%): Adds organic "flicker" and detail at the edges
            baseSize = 0.25f + RANDOM.nextFloat() * 0.20f;  // Range: 0.25 - 0.45
            lifetime = 22 + RANDOM.nextInt(8);
        } else {
            // Occasional Flares (5%): Rare licks of flame that extend further
            baseSize = 0.65f + RANDOM.nextFloat() * 0.25f;  // Range: 0.65 - 0.90
            lifetime = 20 + RANDOM.nextInt(6);
        }


        double xd = vx * -0.06 + (RANDOM.nextFloat() - 0.5) * 0.06;
        double yd = vy * -0.01 + (RANDOM.nextFloat() - 0.5) * 0.06;
        double zd = vz * -0.06 + (RANDOM.nextFloat() - 0.5) * 0.06;

        synchronized (ACTIVE) {
            if (ACTIVE.size() >= MAX_PUFFS) {
                ACTIVE.remove(0);
            }
            ACTIVE.add(new Puff(x, y, z, xd, yd, zd, baseSize, lifetime));
        }
    }

    public static void clear() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
        lastTickGameTime = Long.MIN_VALUE;
        lastRenderFrameToken = Long.MIN_VALUE;
    }

    public static void renderDensityPipeline(Camera camera, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || camera == null) {
            clear();
            return;
        }

        long gameTime = mc.level.getGameTime();

        long frameToken = mc.getFrameTimeNs();
        if (frameToken == 0L) {
            frameToken = (gameTime << 20) ^ (long) (partialTicks * 1000.0f);
        }
        if (frameToken == lastRenderFrameToken) {
            return;
        }
        lastRenderFrameToken = frameToken;

        tickToGameTime(gameTime);

        int puffCount;
        synchronized (ACTIVE) {
            puffCount = ACTIVE.size();
        }
        if (puffCount <= 0) {
            return;
        }

        if (ModShaders.PENUMBRA_DENSITY_SPLAT == null) {
            return;
        }

        if (!PenumbraDensityFBO.beginDensityPass()) {
            return;
        }

        try {
            renderDensitySplats(camera, partialTicks);
        } finally {
            PenumbraDensityFBO.endDensityPass();
        }

        PenumbraDensityFBO.blur();
        PenumbraDensityFBO.composite();
    }

    private static void tickToGameTime(long gameTime) {
        if (lastTickGameTime == Long.MIN_VALUE) {
            lastTickGameTime = gameTime;
            return;
        }

        long delta = gameTime - lastTickGameTime;
        if (delta <= 0) {
            return;
        }

        // Advance at most 1 tick per render call to prevent batching multiple
        // age increments into a single frame (which causes visible size/alpha jumps).
        // Any skipped ticks are absorbed — partialTicks interpolation in sampleSize/
        // sampleAlpha keeps the visual transition smooth between frames.
        tickOnce();

        lastTickGameTime = gameTime;
    }

    private static void tickOnce() {
        synchronized (ACTIVE) {
            Iterator<Puff> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Puff puff = it.next();
                puff.xo = puff.x;
                puff.yo = puff.y;
                puff.zo = puff.z;
                puff.xdo = puff.xd;
                puff.ydo = puff.yd;
                puff.zdo = puff.zd;

                puff.x += puff.xd;
                puff.y += puff.yd;
                puff.z += puff.zd;

                puff.xd *= 0.75 + (RANDOM.nextFloat() * 0.5);
                puff.yd *= 0.94;
                puff.zd *= 0.75 + (RANDOM.nextFloat() * 0.5);
                // Gentle upward drift like smoke
                puff.yd += 0.005;

                puff.age++;
                if (puff.age >= puff.lifetime) {
                    it.remove();
                }
            }
        }
    }

    private static void renderDensitySplats(Camera camera, float partialTicks) {
        // Enable depth test against the blitted main depth buffer so splats
        // behind terrain/blocks are occluded. Depth writes stay off so splats
        // don't interfere with each other's depth ordering.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);

        // Save current RenderSystem matrices
        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        // Set the camera view matrix on RenderSystem directly — BufferUploader.drawWithShader()
        // reads from RenderSystem and overwrites any values set on the shader instance.
        // Puff positions are camera-relative world-space offsets (worldPos - camPos), so
        // the view matrix (inverse camera rotation) must transform them into camera space
        // before the perspective projection. The billboard rotation (camOrientation) on each
        // quad combines with this view matrix to produce correctly oriented, positioned splats.
        Quaternionf viewRot = new Quaternionf(camera.rotation()).conjugate();
        RenderSystem.getModelViewMatrix().set(new Matrix4f().rotation(viewRot));

        var shader = ModShaders.PENUMBRA_DENSITY_SPLAT;
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, DENSITY_SPRITE_TEXTURE);

        // Set GameTime uniform for animated noise in the density splat shader
        float gameTime = 0.0f;
        if (Minecraft.getInstance().level != null) {
            gameTime = Minecraft.getInstance().level.getGameTime()
                + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        }
        com.mojang.blaze3d.shaders.Uniform uGameTime = shader.getUniform("GameTime");
        if (uGameTime != null) {
            uGameTime.set(gameTime / 1200.0f);
        }

        // Set CameraPos uniform so vertex shader can reconstruct stable world positions
        Vec3 camPos = camera.getPosition();
        com.mojang.blaze3d.shaders.Uniform uCameraPos = shader.getUniform("CameraPos");
        if (uCameraPos != null) {
            uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        }

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        PoseStack stack = new PoseStack();
        var camOrientation = camera.rotation();

        synchronized (ACTIVE) {
            for (Puff puff : ACTIVE) {
                float alpha = sampleAlpha(puff, partialTicks);
                if (alpha <= 0.001f) continue;

                float size = sampleSize(puff, partialTicks);
                if (size <= 0.001f) continue;

                double rx = Mth.lerp(partialTicks, puff.xo, puff.x) - camPos.x;
                double ry = Mth.lerp(partialTicks, puff.yo, puff.y) - camPos.y;
                double rz = Mth.lerp(partialTicks, puff.zo, puff.z) - camPos.z;

                stack.pushPose();
                stack.translate(rx, ry, rz);
                stack.mulPose(camOrientation);

                // Interpolate velocity between ticks for smooth stretch/angle
                double ivx = Mth.lerp(partialTicks, puff.xdo, puff.xd);
                double ivy = Mth.lerp(partialTicks, puff.ydo, puff.yd);
                double ivz = Mth.lerp(partialTicks, puff.zdo, puff.zd);
                double speed = Math.sqrt(ivx * ivx + ivy * ivy + ivz * ivz);
                float stretch = 1.0f + (float)(speed * 12.0);
                stretch = Math.min(stretch, 1.25f);

                // Compute velocity-based angle
                float velAngle = puff.rotation;  // fallback
                if (speed > 0.0005) {
                    Vec3 vel = new Vec3(ivx, ivy, ivz);
                    org.joml.Vector3f right = camOrientation.transform(new org.joml.Vector3f(1, 0, 0));
                    org.joml.Vector3f up = camOrientation.transform(new org.joml.Vector3f(0, 1, 0));
                    Vec3 camRight = new Vec3(right.x, right.y, right.z);
                    Vec3 camUp = new Vec3(up.x, up.y, up.z);
                    float dotR = (float) vel.dot(camRight);
                    float dotU = (float) vel.dot(camUp);
                    velAngle = (float) Math.atan2(dotU, dotR);
                }
                // Smoothly blend from velocity-based angle to random rotation as speed drops
                float speedBlend = (float) Math.min(speed / 0.005, 1.0);
                float angle = puff.rotation + speedBlend * wrapAngle(velAngle - puff.rotation);
                stack.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
                stack.scale(size * stretch, size / Math.max(stretch * 0.6f, 1.0f), 1.0f);

                Matrix4f pose = stack.last().pose();
                buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, 1f, -1f, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, 1f, 1f, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, -1f, 1f, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, alpha).setLight(FULLBRIGHT);

                stack.popPose();
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        // Restore original RenderSystem model-view matrix
        RenderSystem.getModelViewMatrix().set(savedModelView);

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static float sampleSize(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        float growPhase = Math.min(t * 8.0f, 1.0f);
        float shrinkPhase = 1.0f - smoothstep(0.15f, 1.0f, t);

        // Start at 0.25f (25% size) and grow to 100% size before shrinking to prevent jittery pops
        return puff.baseSize * (0.05f + 0.95f * growPhase) * shrinkPhase;
    }

    private static float sampleAlpha(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        float fadeIn = Math.min(1.0f, t / 0.6f); // Slower fade-in (20% of life) to "ooze" into the metaball aura
        float fadeOut = 1.0f - smoothstep(0.8f, 1.0f, t);
        return Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity;
    }

    /** Wraps an angle difference into the range [-PI, PI] for shortest-path interpolation. */
    private static float wrapAngle(float a) {
        while (a > (float) Math.PI) a -= (float)(Math.PI * 2.0);
        while (a < -(float) Math.PI) a += (float)(Math.PI * 2.0);
        return a;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static final class Puff {
        double x;
        double y;
        double z;
        double xo;
        double yo;
        double zo;

        double xd;
        double yd;
        double zd;
        double xdo;  // previous-tick velocity for interpolation
        double ydo;
        double zdo;

        final float baseSize;
        final int lifetime;
        final float baseDensity;
        final float rotation;  // per-puff random rotation (suggestion C)
        int age;

        Puff(double x, double y, double z,
             double xd, double yd, double zd,
             float baseSize, int lifetime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.xdo = xd;
            this.ydo = yd;
            this.zdo = zd;
            this.baseSize = baseSize;
            this.lifetime = lifetime;
            this.baseDensity = 0.5f + RANDOM.nextFloat() * 0.7f;  // 0.5 to 1.2 — organic variation
            this.rotation = RANDOM.nextFloat() * (float)(Math.PI * 2.0);  // random rotation for organic overlap
            this.age = 0;
        }
    }
}
