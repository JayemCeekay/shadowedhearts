package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
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
 * Snag ball trail particle manager with independent density FBO pipelines for
 * orange smoke puffs and purple motes.
 */
public final class SnagTrailDensitySystem {

    private static final ResourceLocation SMOKE_DENSITY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/particle/penumbra_trail.png"
    );
    private static final ResourceLocation MOTE_DENSITY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "shadowedhearts",
            "textures/vfx/soft_glow.png"
    );

    private static final int FULLBRIGHT = 0x00F000F0;
    private static final int MAX_PUFFS = 4096;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Puff> ACTIVE = new ArrayList<>();

    private static long lastTickGameTime = Long.MIN_VALUE;
    private static long lastRenderFrameToken = Long.MIN_VALUE;

    private SnagTrailDensitySystem() {}

    private enum PuffType {
        ORANGE_SMOKE,
        PURPLE_MOTE
    }

    /**
     * Registers a short-lived orange smoke puff behind a moving snag ball.
     */
    public static void registerOrangeSmokePuff(double x, double y, double z,
                                               double vx, double vy, double vz) {
        float baseSize;
        int lifetime;
        float roll = RANDOM.nextFloat();
        if (roll < 0.80f) {
            baseSize = 0.30f + RANDOM.nextFloat() * 0.15f;
            lifetime = 10 + RANDOM.nextInt(6);
        } else {
            baseSize = 0.40f + RANDOM.nextFloat() * 0.18f;
            lifetime = 12 + RANDOM.nextInt(6);
        }

        double xd = vx * -0.06 + (RANDOM.nextFloat() - 0.5) * 0.035;
        double yd = vy * -0.03 + (RANDOM.nextFloat() - 0.5) * 0.035;
        double zd = vz * -0.06 + (RANDOM.nextFloat() - 0.5) * 0.035;

        addPuff(new Puff(x, y, z, xd, yd, zd, baseSize, lifetime, PuffType.ORANGE_SMOKE));
    }

    /**
     * Registers a longer-lived purple glowing mote behind a moving snag ball.
     */
    public static void registerPurpleMote(double x, double y, double z,
                                          double vx, double vy, double vz) {
        float baseSize = 0.08f + RANDOM.nextFloat() * 0.07f;
        int lifetime = 30 + RANDOM.nextInt(20);

        double xd = vx * -0.02 + (RANDOM.nextFloat() - 0.5) * 0.02;
        double yd = vy * -0.01 + (RANDOM.nextFloat() - 0.5) * 0.025 + 0.005;
        double zd = vz * -0.02 + (RANDOM.nextFloat() - 0.5) * 0.02;

        addPuff(new Puff(x, y, z, xd, yd, zd, baseSize, lifetime, PuffType.PURPLE_MOTE));
    }

    private static void addPuff(Puff puff) {
        synchronized (ACTIVE) {
            if (ACTIVE.size() >= MAX_PUFFS) {
                ACTIVE.remove(0);
            }
            ACTIVE.add(puff);
        }
    }

    /**
     * @deprecated Use {@link #registerOrangeSmokePuff} or {@link #registerPurpleMote} instead.
     */
    @Deprecated
    public static void registerTrailPuff(double x, double y, double z,
                                         double vx, double vy, double vz) {
        registerOrangeSmokePuff(x, y, z, vx, vy, vz);
    }

    public static void clear() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
        lastTickGameTime = Long.MIN_VALUE;
        lastRenderFrameToken = Long.MIN_VALUE;
    }

    /**
     * Full trail density pipeline. Orange smoke uses {@link SnagTrailDensityFBO};
     * purple motes use {@link SnagPurpleMoteFBO}; the capture beam remains on
     * {@link SnagDensityFBO}.
     */
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

        boolean hasOrangeSmoke;
        boolean hasPurpleMotes;
        synchronized (ACTIVE) {
            hasOrangeSmoke = ACTIVE.stream().anyMatch(p -> p.type == PuffType.ORANGE_SMOKE);
            hasPurpleMotes = ACTIVE.stream().anyMatch(p -> p.type == PuffType.PURPLE_MOTE);
        }

        if (!hasOrangeSmoke && !hasPurpleMotes) {
            return;
        }

        if (hasOrangeSmoke && ModShaders.SNAG_TRAIL_DENSITY != null) {
            if (SnagTrailDensityFBO.beginDensityPass()) {
                try {
                    renderDensitySplatsFiltered(camera, partialTicks,
                            PuffType.ORANGE_SMOKE, ModShaders.SNAG_TRAIL_DENSITY, SMOKE_DENSITY_TEXTURE);
                } finally {
                    SnagTrailDensityFBO.endDensityPass();
                }
                SnagTrailDensityFBO.blur();
                SnagTrailDensityFBO.composite();
            }
        }

        if (hasPurpleMotes && ModShaders.SNAG_MOTE_DENSITY != null) {
            if (SnagPurpleMoteFBO.beginDensityPass()) {
                try {
                    renderDensitySplatsFiltered(camera, partialTicks,
                            PuffType.PURPLE_MOTE, ModShaders.SNAG_MOTE_DENSITY, MOTE_DENSITY_TEXTURE);
                } finally {
                    SnagPurpleMoteFBO.endDensityPass();
                }
                SnagPurpleMoteFBO.blur();
                SnagPurpleMoteFBO.composite();
            }
        }
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

                switch (puff.type) {
                    case ORANGE_SMOKE -> {
                        puff.xd *= 0.75 + (RANDOM.nextFloat() * 0.12);
                        puff.yd *= 0.90;
                        puff.zd *= 0.75 + (RANDOM.nextFloat() * 0.12);
                    }
                    case PURPLE_MOTE -> {
                        puff.xd *= 0.92;
                        puff.yd *= 0.95;
                        puff.yd += 0.001;
                        puff.zd *= 0.92;
                    }
                }

                puff.age++;
                if (puff.age >= puff.lifetime) {
                    it.remove();
                }
            }
        }
    }

    private static void renderDensitySplatsFiltered(Camera camera, float partialTicks,
                                                    PuffType typeFilter, ShaderInstance shader,
                                                    ResourceLocation densityTexture) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);

        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        Quaternionf viewRot = new Quaternionf(camera.rotation()).conjugate();
        RenderSystem.getModelViewMatrix().set(new Matrix4f().rotation(viewRot));

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, densityTexture);

        Vec3 camPos = camera.getPosition();
        com.mojang.blaze3d.shaders.Uniform uCameraPos = shader.getUniform("CameraPos");
        if (uCameraPos != null) {
            uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        }

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        PoseStack stack = new PoseStack();
        Quaternionf camOrientation = camera.rotation();

        synchronized (ACTIVE) {
            for (Puff puff : ACTIVE) {
                if (puff.type != typeFilter) continue;

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

                float cr;
                float cg;
                float cb;
                if (puff.type == PuffType.PURPLE_MOTE) {
                    cr = 0.65f;
                    cg = 0.25f;
                    cb = 0.90f;

                    stack.mulPose(com.mojang.math.Axis.ZP.rotation(puff.rotation));
                    stack.scale(size, size, 1.0f);
                } else {
                    cr = 1.0f;
                    cg = 0.55f;
                    cb = 0.15f;

                    double ivx = Mth.lerp(partialTicks, puff.xdo, puff.xd);
                    double ivy = Mth.lerp(partialTicks, puff.ydo, puff.yd);
                    double ivz = Mth.lerp(partialTicks, puff.zdo, puff.zd);
                    double speed = Math.sqrt(ivx * ivx + ivy * ivy + ivz * ivz);
                    float stretch = 1.0f + (float) (speed * 14.0);
                    stretch = Math.min(stretch, 1.4f);

                    float velAngle = puff.rotation;
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
                    float speedBlend = (float) Math.min(speed / 0.005, 1.0);
                    float angle = puff.rotation + speedBlend * wrapAngle(velAngle - puff.rotation);
                    stack.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
                    stack.scale(size * stretch, size / Math.max(stretch * 0.6f, 1.0f), 1.0f);
                }

                Matrix4f pose = stack.last().pose();
                buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(cr, cg, cb, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, 1f, -1f, 0f).setUv(1f, 1f).setColor(cr, cg, cb, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, 1f, 1f, 0f).setUv(1f, 0f).setColor(cr, cg, cb, alpha).setLight(FULLBRIGHT);
                buf.addVertex(pose, -1f, 1f, 0f).setUv(0f, 0f).setColor(cr, cg, cb, alpha).setLight(FULLBRIGHT);

                stack.popPose();
            }
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.getModelViewMatrix().set(savedModelView);

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static float sampleSize(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        switch (puff.type) {
            case ORANGE_SMOKE -> {
                float growPhase = Math.min(t * 8.0f, 1.0f);
                float shrinkPhase = 1.0f - smoothstep(0.2f, 1.0f, t);
                float expansion = 1.0f + t * 0.3f;
                return puff.baseSize * (0.1f + 0.9f * growPhase) * shrinkPhase * expansion;
            }
            case PURPLE_MOTE -> {
                float growPhase = Math.min(t * 10.0f, 1.0f);
                float shrinkPhase = 1.0f - smoothstep(0.7f, 1.0f, t);
                return puff.baseSize * growPhase * shrinkPhase;
            }
        }
        return puff.baseSize;
    }

    private static float sampleAlpha(Puff puff, float partialTicks) {
        float t = Mth.clamp((puff.age + partialTicks) / (float) puff.lifetime, 0.0f, 1.0f);
        switch (puff.type) {
            case ORANGE_SMOKE -> {
                float fadeIn = Math.min(1.0f, t / 0.25f);
                float fadeOut = 1.0f - smoothstep(0.4f, 1.0f, t);
                return Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity;
            }
            case PURPLE_MOTE -> {
                float fadeIn = Math.min(1.0f, t / 0.1f);
                float fadeOut = 1.0f - smoothstep(0.6f, 1.0f, t);
                float twinkle = 0.85f + 0.15f * (float) Math.sin(puff.age * 0.8f + puff.rotation * 10.0f);
                return Math.min(1.0f, fadeIn * fadeOut) * puff.baseDensity * twinkle;
            }
        }
        return puff.baseDensity;
    }

    private static float wrapAngle(float a) {
        while (a > (float) Math.PI) a -= (float) (Math.PI * 2.0);
        while (a < -(float) Math.PI) a += (float) (Math.PI * 2.0);
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
        double xdo;
        double ydo;
        double zdo;

        final float baseSize;
        final int lifetime;
        final float baseDensity;
        final float rotation;
        final PuffType type;
        int age;

        Puff(double x, double y, double z,
             double xd, double yd, double zd,
             float baseSize, int lifetime, PuffType type) {
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
            this.type = type;
            this.baseDensity = switch (type) {
                case ORANGE_SMOKE -> 0.5f + RANDOM.nextFloat() * 0.5f;
                case PURPLE_MOTE -> 0.4f + RANDOM.nextFloat() * 0.3f;
            };
            this.rotation = RANDOM.nextFloat() * (float) (Math.PI * 2.0);
            this.age = 0;
        }
    }
}
