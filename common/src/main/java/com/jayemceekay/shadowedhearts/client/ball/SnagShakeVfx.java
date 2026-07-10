package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity.CaptureState;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.ball.vfx.GroundPulse;
import com.jayemceekay.shadowedhearts.client.ball.vfx.ShakeInstance;
import com.jayemceekay.shadowedhearts.client.ball.vfx.ShakeSpark;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side VFX for the Snag Ball shake/result phase (Phase 2d + 2e).
 *
 * <p>Monitors Snag Balls in {@link CaptureState#SHAKE} state and renders:
 * <ul>
 *   <li>Seam glow line on the ball during shaking</li>
 *   <li>Expanding ground pulse ring beneath the ball on each shake</li>
 *   <li>Dark/shadow sparks shed on each shake</li>
 *   <li>Success burst (bright energy + lock flash) on {@link CaptureState#CAPTURED}</li>
 *   <li>Failure burst (dark sparks + shadow pulse) on {@link CaptureState#BROKEN_FREE}</li>
 * </ul>
 */
public final class SnagShakeVfx {

    private SnagShakeVfx() {}

    // ── Instance registry ───────────────────────────────────────────────────
    private static final Map<Integer, ShakeInstance> ACTIVE = new ConcurrentHashMap<>();
    private static long lastTickNanos = 0;


    // ── Public API ──────────────────────────────────────────────────────────

    /** Called from BallEmitters each frame to check if any snag balls should be tracked. */
    public static void maybeTrack(EmptyPokeBallEntity ball) {
        if (!ball.getAspects().contains("snag_ball")) return;
        CaptureState state = ball.getCaptureState();
        if (state != CaptureState.SHAKE && state != CaptureState.CRITICAL
                && state != CaptureState.CAPTURED && state != CaptureState.BROKEN_FREE) return;
        ACTIVE.computeIfAbsent(ball.getId(), ShakeInstance::new);
    }

    public static void tickAll() {
        long now = System.nanoTime();
        float dt;
        if (lastTickNanos == 0 || ACTIVE.isEmpty()) {
            dt = 0f;
            lastTickNanos = now;
        } else {
            dt = (now - lastTickNanos) / 1_000_000_000f;
            dt = Math.min(dt, 0.1f);
        }
        lastTickNanos = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        final float fdt = dt;
        ACTIVE.entrySet().removeIf(entry -> {
            ShakeInstance inst = entry.getValue();
            Entity ent = mc.level.getEntity(inst.ballId);
            if (!(ent instanceof EmptyPokeBallEntity ball)) return true;
            if (!ball.isAlive()) return true;

            CaptureState state = ball.getCaptureState();

            // Detect shake toggles
            boolean currentShake = ball.getEntityData().get(EmptyPokeBallEntity.Companion.getSHAKE());
            if (currentShake != inst.lastShakeValue) {
                inst.lastShakeValue = currentShake;
                inst.seamGlowStrength = 1.0f;
                onShakeEvent(inst, ball);
            }

            // Detect capture/failure transitions
            if (state != inst.lastCaptureState) {
                if (state == CaptureState.CAPTURED) {
                    inst.isSuccess = true;
                    inst.resultAge = 0f;
                } else if (state == CaptureState.BROKEN_FREE) {
                    inst.isSuccess = false;
                    inst.resultAge = 0f;
                }
                inst.lastCaptureState = state;
            }

            // Tick result age
            if (inst.resultAge >= 0f) {
                inst.resultAge += fdt;
            }

            // Decay seam glow
            inst.seamGlowStrength = Math.max(0f, inst.seamGlowStrength - fdt * 2.5f);

            // Tick particles
            inst.sparks.removeIf(s -> s.tick(fdt));
            inst.pulses.removeIf(p -> p.tick(fdt));

            // Spawn result burst once
            if (inst.resultAge >= 0f && !inst.spawnedResultBurst) {
                inst.spawnedResultBurst = true;
                onResultEvent(inst, ball);
            }

            // Remove after result effect finishes
            if (inst.resultAge > 2.0f && inst.sparks.isEmpty() && inst.pulses.isEmpty()) {
                return true;
            }

            // Also remove if ball is no longer in shake-related states and no result pending
            if (inst.resultAge < 0f && state != CaptureState.SHAKE && state != CaptureState.CRITICAL) {
                return inst.sparks.isEmpty() && inst.pulses.isEmpty();
            }

            return false;
        });
    }

    public static void renderAll(Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Vec3 camPos = camera.getPosition();

        for (ShakeInstance inst : ACTIVE.values()) {
            Entity ent = mc.level.getEntity(inst.ballId);
            if (!(ent instanceof EmptyPokeBallEntity ball)) continue;

            Vec3 ballPos = new Vec3(
                    Mth.lerp(partialTick, ball.xOld, ball.getX()),
                    Mth.lerp(partialTick, ball.yOld, ball.getY()),
                    Mth.lerp(partialTick, ball.zOld, ball.getZ())
            );

            // Render seam glow billboard
            if (inst.seamGlowStrength > 0.01f) {
                renderSeamGlow(ballPos, camPos, camera, inst.seamGlowStrength, buf);
            }

            // Render ground pulses
            for (GroundPulse pulse : inst.pulses) {
                renderGroundPulse(pulse, camPos, buf);
            }

            // Render sparks
            renderShakeSparks(inst.sparks, camPos, buf);

            // Render result effects
            //if (inst.resultAge >= 0f) {
            //    renderResultEffect(inst, ballPos, camPos, camera, buf);
            //}
        }

        buf.endBatch();
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    private static final Random RNG = new Random();

    private static void onShakeEvent(ShakeInstance inst, EmptyPokeBallEntity ball) {
        Vec3 pos = ball.position();

        // Spawn dark sparks from the seam — fast, short-lived needle sparks
        int sparkCount = 10 + RNG.nextInt(8);
        for (int i = 0; i < sparkCount; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double speed = 2.0 + RNG.nextDouble() * 3.0;
            double vy = RNG.nextDouble() * 2.0 - 0.5;
            Vec3 vel = new Vec3(
                    Math.cos(angle) * speed,
                    vy,
                    Math.sin(angle) * speed
            );
            float life = 0.1f + RNG.nextFloat() * 0.2f;
            // Dark purple/red sparks
            int r = 120 + RNG.nextInt(60);
            int g = 20 + RNG.nextInt(40);
            int b = 160 + RNG.nextInt(80);
            inst.sparks.add(new ShakeSpark(pos.add(0, ball.getBbHeight() * 0.5, 0), vel, life, r, g, b));
        }

        // Spawn ground pulse ring
        float groundY = (float) pos.y;
        inst.pulses.add(new GroundPulse(pos, groundY, 0.45f, 1.8f, 160, 60, 220));
    }

    private static void onResultEvent(ShakeInstance inst, EmptyPokeBallEntity ball) {
        Vec3 pos = ball.position().add(0, ball.getBbHeight() * 0.5, 0);

        if (inst.isSuccess) {
            // Success: bright energy burst
            int count = 24 + RNG.nextInt(12);
            for (int i = 0; i < count; i++) {
                double angle = RNG.nextDouble() * Math.PI * 2;
                double elev = (RNG.nextDouble() - 0.5) * Math.PI;
                double speed = 3.0 + RNG.nextDouble() * 4.0;
                Vec3 vel = new Vec3(
                        Math.cos(angle) * Math.cos(elev) * speed,
                        Math.sin(elev) * speed * 0.6 + 0.5,
                        Math.sin(angle) * Math.cos(elev) * speed
                );
                float life = 0.15f + RNG.nextFloat() * 0.25f;
                // Bright white/gold sparks
                int r = 240 + RNG.nextInt(16);
                int g = 200 + RNG.nextInt(56);
                int b = 100 + RNG.nextInt(80);
                inst.sparks.add(new ShakeSpark(pos, vel, life, r, g, b));
            }
            // Bright flash pulse
            inst.pulses.add(new GroundPulse(ball.position(), (float) ball.getY(), 0.5f, 2.5f, 255, 220, 140));
        } else {
            // Failure: dark shadow burst
            int count = 30 + RNG.nextInt(15);
            for (int i = 0; i < count; i++) {
                double angle = RNG.nextDouble() * Math.PI * 2;
                double elev = (RNG.nextDouble() - 0.5) * Math.PI;
                double speed = 2.5 + RNG.nextDouble() * 3.5;
                Vec3 vel = new Vec3(
                        Math.cos(angle) * Math.cos(elev) * speed,
                        Math.sin(elev) * speed * 0.5,
                        Math.sin(angle) * Math.cos(elev) * speed
                );
                float life = 0.2f + RNG.nextFloat() * 0.3f;
                // Dark violet/black sparks
                int r = 80 + RNG.nextInt(60);
                int g = 10 + RNG.nextInt(30);
                int b = 120 + RNG.nextInt(80);
                inst.sparks.add(new ShakeSpark(pos, vel, life, r, g, b));
            }
            // Dark shadow pulse
            inst.pulses.add(new GroundPulse(ball.position(), (float) ball.getY(), 0.6f, 3.0f, 100, 20, 160));
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    /**
     * Renders a small glowing billboard at the ball's seam position.
     * Uses the ball_glow shader in starburst mode for a quick flash.
     */
    private static void renderSeamGlow(Vec3 ballPos, Vec3 camPos, Camera camera,
                                        float strength,
                                        MultiBufferSource.BufferSource buf) {
        if (ModShaders.BALL_ORB_GLOW == null) return;

        PoseStack ps = new PoseStack();
        ps.translate(ballPos.x - camPos.x, ballPos.y + 0.2 - camPos.y, ballPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        float radius = 0.4f + strength * 0.3f;
        ps.scale(radius, radius * 0.25f, radius);

        try {
            ShaderInstance sh = ModShaders.BALL_ORB_GLOW;
            Minecraft mc = Minecraft.getInstance();
            float time = mc.level != null ? mc.level.getGameTime() : 0f;
            set1f(sh, "u_time", time);
            set1f(sh, "u_orbIntensity", strength * 2.0f);
            set1f(sh, "u_orbSoftness", 0.6f);
            set1f(sh, "u_starStrength", strength * 0.3f);
            set1f(sh, "u_starSharpness", 4.0f);
            set1f(sh, "u_starCount", 2.0f);
            set1f(sh, "u_glowMix", 1.0f);
            set3f(sh, "u_c1", 0.9f, 0.3f, 1.0f);
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.ballOrbGlow());
        emitBillboardQuad(vc, ps, strength);
    }

    /**
     * Renders an expanding ground-hugging ring.
     * The ring is rendered as a flat horizontal billboard slightly above the ground.
     */
    private static void renderGroundPulse(GroundPulse pulse, Vec3 camPos,
                                           MultiBufferSource.BufferSource buf) {
        if (ModShaders.BALL_ORB_GLOW == null) return;

        float radius = pulse.radius();
        float alpha = pulse.alpha();
        if (alpha < 0.01f || radius < 0.01f) return;

        PoseStack ps = new PoseStack();
        ps.translate(
                pulse.center.x - camPos.x,
                pulse.groundY + 0.05 - camPos.y,
                pulse.center.z - camPos.z
        );
        // Lay flat on the ground (rotate 90° around X)
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
        ps.scale(radius, radius, radius);

        try {
            ShaderInstance sh = ModShaders.BALL_ORB_GLOW;
            Minecraft mc = Minecraft.getInstance();
            float time = mc.level != null ? mc.level.getGameTime() : 0f;
            set1f(sh, "u_time", time);
            set1f(sh, "u_orbIntensity", alpha * 1.2f);
            set1f(sh, "u_orbSoftness", 1.5f);
            set1f(sh, "u_starStrength", 0.0f);
            set1f(sh, "u_glowMix", 1.0f);
            set3f(sh, "u_c1",
                    pulse.r / 255f,
                    pulse.g / 255f,
                    pulse.b / 255f);
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.ballOrbGlow());
        emitBillboardQuad(vc, ps, alpha);
    }

    /**
     * Renders all shake sparks as velocity-aligned elongated needle quads.
     * Each spark is stretched along its velocity direction for a streak effect.
     */
    private static void renderShakeSparks(List<ShakeSpark> sparks, Vec3 camPos,
                                           MultiBufferSource.BufferSource buf) {
        if (sparks.isEmpty()) return;

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.sparkGlowAdditive());
        org.joml.Vector3f lookVec = Minecraft.getInstance().getEntityRenderDispatcher().camera.getLookVector();
        Vec3 camForward = new Vec3(lookVec.x, lookVec.y, lookVec.z);

        for (ShakeSpark spark : sparks) {
            float a = spark.alpha();
            if (a < 0.01f) continue;

            // Velocity-aligned billboard: stretch along velocity, thin perpendicular
            Vec3 vel = spark.vel;
            double speed = vel.length();
            if (speed < 0.001) continue;

            Vec3 dir = vel.normalize();
            // Cross velocity with camera forward to get the "width" axis
            Vec3 camFwd = new Vec3(camForward.x, camForward.y, camForward.z);
            Vec3 side = dir.cross(camFwd);
            double sideLen = side.length();
            if (sideLen < 0.001) {
                // Velocity is parallel to camera — use an arbitrary perpendicular
                side = dir.cross(new Vec3(0, 1, 0));
                sideLen = side.length();
                if (sideLen < 0.001) side = dir.cross(new Vec3(1, 0, 0));
                sideLen = side.length();
            }
            side = side.scale(1.0 / sideLen);

            // Needle dimensions: long along velocity, thin across
            float halfLength = 0.15f * a;  // half-length along velocity
            float halfWidth = 0.015f * a;  // half-width perpendicular — very thin

            Vec3 forward = dir.scale(halfLength);
            Vec3 right = side.scale(halfWidth);

            // Quad corners: elongated along velocity
            Vec3 center = spark.pos.subtract(camPos);
            Vec3 p0 = center.subtract(forward).subtract(right);
            Vec3 p1 = center.add(forward).subtract(right);
            Vec3 p2 = center.add(forward).add(right);
            Vec3 p3 = center.subtract(forward).add(right);

            PoseStack ps = new PoseStack();
            var last = ps.last();
            Matrix4f pose = last.pose();
            int alpha = (int) (a * 255);

            vc.addVertex(pose, (float) p0.x, (float) p0.y, (float) p0.z)
                    .setColor(spark.r, spark.g, spark.b, alpha)
                    .setUv(0f, 1f).setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
            vc.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z)
                    .setColor(spark.r, spark.g, spark.b, alpha)
                    .setUv(1f, 1f).setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
            vc.addVertex(pose, (float) p2.x, (float) p2.y, (float) p2.z)
                    .setColor(spark.r, spark.g, spark.b, alpha)
                    .setUv(1f, 0f).setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
            vc.addVertex(pose, (float) p3.x, (float) p3.y, (float) p3.z)
                    .setColor(spark.r, spark.g, spark.b, alpha)
                    .setUv(0f, 0f).setOverlay(0).setLight(0x00F000F0).setNormal(last, 0f, 0f, 1f);
        }
    }

    /**
     * Renders the success/failure result effect — a billboard flash that fades over time.
     */
    private static void renderResultEffect(ShakeInstance inst, Vec3 ballPos, Vec3 camPos,
                                            Camera camera, MultiBufferSource.BufferSource buf) {
        if (ModShaders.BALL_ORB_GLOW == null) return;
        float flashDuration = inst.isSuccess ? 0.6f : 0.8f;
        if (inst.resultAge > flashDuration) return;

        float t = inst.resultAge / flashDuration;
        float alpha = 1f - t * t;
        float radius = inst.isSuccess
                ? Mth.lerp(t, 1.0f, 2.5f)
                : Mth.lerp(t, 1.2f, 3.0f);

        PoseStack ps = new PoseStack();
        ps.translate(ballPos.x - camPos.x, ballPos.y + 0.2 - camPos.y, ballPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        ps.scale(radius, radius, radius);

        try {
            ShaderInstance sh = ModShaders.BALL_ORB_GLOW;
            Minecraft mc = Minecraft.getInstance();
            float time = mc.level != null ? mc.level.getGameTime() : 0f;
            set1f(sh, "u_time", time);
            set1f(sh, "u_orbIntensity", alpha * 2.5f);
            set1f(sh, "u_orbSoftness", 0.8f);
            set1f(sh, "u_starStrength", alpha * 1.2f);
            set1f(sh, "u_starSharpness", inst.isSuccess ? 10.0f : 6.0f);
            set1f(sh, "u_starCount", inst.isSuccess ? 6.0f : 4.0f);
            set1f(sh, "u_glowMix", 1.0f);
            if (inst.isSuccess) {
                set3f(sh, "u_c1", 1.0f, 0.9f, 0.5f);   // warm gold
            } else {
                set3f(sh, "u_c1", 0.6f, 0.1f, 0.8f);   // dark violet
            }
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.ballOrbGlow());
        emitBillboardQuad(vc, ps, alpha);
    }

    // ── Geometry helpers ────────────────────────────────────────────────────

    private static void emitBillboardQuad(VertexConsumer vc, PoseStack ps, float alpha) {
        final int FULLBRIGHT = 0x00F000F0;
        var last = ps.last();
        Matrix4f pose = last.pose();
        float x0 = -1f, y0 = -1f, x1 = 1f, y1 = 1f;
        vc.addVertex(pose, x0, y0, 0f).setColor(1f, 1f, 1f, alpha)
                .setUv(0f, 1f).setOverlay(0).setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y0, 0f).setColor(1f, 1f, 1f, alpha)
                .setUv(1f, 1f).setOverlay(0).setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y1, 0f).setColor(1f, 1f, 1f, alpha)
                .setUv(1f, 0f).setOverlay(0).setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x0, y1, 0f).setColor(1f, 1f, 1f, alpha)
                .setUv(0f, 0f).setOverlay(0).setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
    }

    private static void set1f(ShaderInstance sh, String name, float v) {
        var u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set3f(ShaderInstance sh, String name, float x, float y, float z) {
        var u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }
}
