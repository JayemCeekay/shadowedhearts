package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.particle.PenumbraTrailSystem;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.jayemceekay.shadowedhearts.client.trail.BallTrailManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;

import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side emitter system for thrown Poké Balls. Mirrors AuraEmitters pattern but renders
 * a glowing orb billboard and a motion trail, driven by server-authoritative state sync.
 */
public final class BallEmitters {
    private BallEmitters() {
    }

    private static final Map<Integer, BallInstance> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Called on client when a ball entity is created/loaded.
     */
    public static void startForEntity(EmptyPokeBallEntity entity) {
        boolean isSnag = entity.getAspects().contains("snag_ball");
        boolean isPenumbra = entity.getPokeBall().getName().getPath().equals("penumbra_ball");

        if (!isSnag && !isPenumbra) return;

        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        long now = mc.level.getGameTime();

        ACTIVE.put(entity.getId(), new BallInstance(entity.getId(), entity, now, 4, 400, 8, isSnag, isPenumbra));
    }

    public static void onEntityDespawn(int entityId) {
        var mc = Minecraft.getInstance();
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        ACTIVE.computeIfPresent(entityId, (id, inst) -> {
            inst.beginImmediateFadeOut(now, 6);
            return inst;
        });
    }

    public static void onRender(net.minecraft.client.Camera camera, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        for (Map.Entry<Integer, BallInstance> en : ACTIVE.entrySet()) {
            BallInstance inst = en.getValue();
            if (inst == null) {
                ACTIVE.remove(en.getKey());
                continue;
            }
            if (inst.isExpired(mc.level.getGameTime())) {
                ACTIVE.remove(en.getKey());
                continue;
            }

            Entity ent = inst.entityRef != null ? inst.entityRef.get() : null;
            boolean useEnt = ent != null && ent.isAlive() && ent.getId() == inst.entityId;

            double ix, iy, iz;
            if (useEnt) {
                ix = Mth.lerp(partialTicks, ent.xOld, ent.getX());
                iy = Mth.lerp(partialTicks, ent.yOld, ent.getY());
                iz = Mth.lerp(partialTicks, ent.zOld, ent.getZ());
            } else {
                // If entity ref is gone, skip
                continue;
            }

            // Feed trail samples in world space; render in the entity-origin pose later
            // We use the interpolated world position
            BallTrailManager.addPointForId(inst.entityId, ix, iy, iz);

            // Emit penumbra trail particles (shadow aura fog puffs) behind the ball
            if (inst.isPenumbraBall && ent instanceof EmptyPokeBallEntity) {
                emitPenumbraTrailParticles(mc, ent, ix, iy, iz, inst);
            }

            // Render from entity-local pose
            PoseStack poseStack = new PoseStack();
            var camPos = camera.getPosition();
            poseStack.translate(ix - camPos.x, iy + (ent.getBbHeight() / 8f) - camPos.y, iz - camPos.z);
            MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();

            // Render orb billboard for snag ball at center
            if (inst.isSnagBall) {
                renderOrb(poseStack, buf, partialTicks);
            }

            // Render trail for snag ball
            if (inst.isSnagBall) {
                BallTrailManager.renderSnagRibbonForId(inst.entityId, partialTicks, poseStack, buf);
            }

            buf.endBatch();
        }
    }

    private static void renderOrb(PoseStack poseStack, MultiBufferSource buffer, float partialTicks) {
        final int FULLBRIGHT = 0x00F000F0;
        poseStack.pushPose();
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float base = 0.85f;
        poseStack.scale(base, base, base);
        if (ModShaders.BALL_GLOW != null) {
            try {
                apply(ModShaders.BALL_GLOW);
            } catch (Throwable ignored) {
            }
        }
        // Use any texture; shader in orb mode ignores it. We route via BallRenderTypes to bind the glow shader.
        VertexConsumer vc = buffer.getBuffer(BallRenderTypes.ballGlow(null));
        emitUnitQuad(vc, poseStack, FULLBRIGHT);
        poseStack.popPose();
    }

    private static void emitUnitQuad(VertexConsumer vc, PoseStack stack, int packedLight) {
        var last = stack.last();
        Matrix4f pose = last.pose();
        float x0 = -1f, y0 = -1f, x1 = 1f, y1 = 1f;
        vc.addVertex(pose, x0, y0, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y0, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y1, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x0, y1, 0f)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
    }

    /**
     * HUD helper: renders the same orb billboard used in-world, but in screen-space using the provided pose.
     * Scales a unit quad to the requested pixel size and routes through BallRenderTypes.ballGlow so the
     * BallGlowUniforms path and shader stay identical to in-world rendering.
     */
    public static void renderHudOrb(PoseStack poseStack, MultiBufferSource buffers, int sizePx, float alpha) {
        final int FULLBRIGHT = 0x00F000F0;
        poseStack.pushPose();
        float s = sizePx * 0.5f; // our quad is [-1,1]
        poseStack.scale(s, s, s);
        if (ModShaders.BALL_GLOW != null) {
            try {
                apply(ModShaders.BALL_GLOW);
            } catch (Throwable ignored) {
            }
        }
        VertexConsumer vc = buffers.getBuffer(BallRenderTypes.ballGlowHud());
        emitUnitQuadAlpha(vc, poseStack, FULLBRIGHT, alpha);
        poseStack.popPose();
    }

    private static void emitUnitQuadAlpha(VertexConsumer vc, PoseStack stack, int packedLight, float alpha) {
        var last = stack.last();
        Matrix4f pose = last.pose();
        float x0 = -1f, y0 = -1f, x1 = 1f, y1 = 1f;
        vc.addVertex(pose, x0, y0, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y0, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x1, y1, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
        vc.addVertex(pose, x0, y1, 0f)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(last, 0f, 0f, 1f);
    }

    /** Particles emitted per block of travel distance when the ball is in motion. */
    private static final double PARTICLES_PER_BLOCK = 12.0;

    /**
     * Spawns penumbra trail particles along the previous-to-current motion segment
     * so fast throws do not leave gaps.
     * <p>
     * Emission rate: 8–20 particles/tick while thrown (velocity-scaled).
     */
    private static void emitPenumbraTrailParticles(Minecraft mc, Entity ent, double ix, double iy, double iz, BallInstance inst) {
        if (mc.level == null) return;

        // Throttle spawning to once per game tick to avoid frame-rate dependent
        // particle density (e.g. 3x more particles at 60 FPS than 20 FPS).
        long gameTime = mc.level.getGameTime();
        if (gameTime == inst.lastSpawnTick) {
            // Still update lastTrailPos so the next tick's gap calculation is accurate
            inst.lastTrailPos = new Vec3(ix, iy + 0.22, iz);
            return;
        }
        inst.lastSpawnTick = gameTime;

        double vx = ent.getDeltaMovement().x;
        double vy = ent.getDeltaMovement().y;
        double vz = ent.getDeltaMovement().z;

        Vec3 curr = new Vec3(ix, iy + 0.22, iz);
        Vec3 prev = inst.lastTrailPos;

        if (prev != null) {
            double gap = prev.distanceTo(curr);
            // Scale particle count by distance traveled — enough for coverage without overcrowding
            int count = Math.max(6, Math.min(24, (int) (gap * PARTICLES_PER_BLOCK)));
            for (int i = 0; i < count; i++) {
                double t = (double) i / count;
                // More aggressive perpendicular spread for smokier trails (suggestion L)
                double lx = Mth.lerp(t, prev.x, curr.x) + (mc.level.random.nextFloat() - 0.5) * 0.2;
                double ly = Mth.lerp(t, prev.y, curr.y) + (mc.level.random.nextFloat() - 0.5) * 0.2;
                double lz = Mth.lerp(t, prev.z, curr.z) + (mc.level.random.nextFloat() - 0.5) * 0.2;
                spawnPenumbraTrailPuff(mc, lx, ly, lz, vx, vy, vz);
            }
        } else {
            // First frame: seed a small cluster at the current position
            for (int i = 0; i < 3; i++) {
                double ox = curr.x + (mc.level.random.nextFloat() - 0.5) * 0.40;
                double oy = curr.y + (mc.level.random.nextFloat() - 0.5) * 0.30;
                double oz = curr.z + (mc.level.random.nextFloat() - 0.5) * 0.40;
                spawnPenumbraTrailPuff(mc, ox, oy, oz, vx, vy, vz);
            }
        }
        inst.lastTrailPos = curr;
    }

    private static void spawnPenumbraTrailPuff(Minecraft mc,
                                                double x, double y, double z,
                                                double vx, double vy, double vz) {
        if (mc.level == null) return;

        mc.level.addParticle(
                ModParticleTypes.PENUMBRA_TRAIL.get(),
                x, y, z,
                vx, vy, vz
        );

        // Mirror the same spawn into our dedicated density trail manager.
        // This keeps the FBO pipeline independent from ParticleEngine internals.
        PenumbraTrailSystem.registerPuff(x, y, z, vx, vy, vz);
    }

    private static final class BallInstance {
        final int entityId;
        final WeakReference<Entity> entityRef;
        final boolean isSnagBall;
        final boolean isPenumbraBall;
        long startTick;
        int fadeInTicks;
        int sustainTicks;
        int fadeOutTicks;
        Vec3 lastTrailPos;
        long lastSpawnTick = Long.MIN_VALUE;

        BallInstance(int entityId, @Nullable Entity ent, long startTick, int fi, int sus, int fo, boolean isSnagBall, boolean isPenumbraBall) {
            this.entityId = entityId;
            this.entityRef = new WeakReference<>(ent);
            this.isSnagBall = isSnagBall;
            this.isPenumbraBall = isPenumbraBall;
            this.startTick = startTick;
            this.fadeInTicks = Math.max(1, fi);
            this.sustainTicks = Math.max(0, sus);
            this.fadeOutTicks = Math.max(1, fo);
        }

        void beginImmediateFadeOut(long now, int outTicks) {
            this.startTick = now - (long) this.fadeInTicks - (long) this.sustainTicks;
            this.fadeOutTicks = Math.max(1, outTicks);
        }

        boolean isExpired(long now) {
            long total = (long) fadeInTicks + (long) sustainTicks + (long) fadeOutTicks;
            return now - startTick >= total;
        }
    }

    public static void apply(ShaderInstance shader) {
        if (shader == null) return;

        float time = Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        // Palette stops and thresholds
        float[] u_c0 = null;
        float[] u_c1 = new float[]{1.25f, 0.72f, 0.12f};
        float[] u_c2 = new float[]{1.25f, 0.25f, 0.10f};
        float[] u_c3 = null;
        Float u_t1 = 0.250f;
        Float u_t2 = null;
        Float u_t3 = null;
        float[] u_lumaCoeff = null;

        float[] u_glowTint = null;

        set1f(shader, "u_orbMode", 1.0f);
        set1f(shader, "u_time", time);

        set1f(shader, "u_rimStrength", null);
        set1f(shader, "u_pulseSpeed", 0.125f);
        set1f(shader, "u_useMask", 1.0f);

        set1f(shader, "u_orbMode", null);
        set1f(shader, "u_orbIntensity", 1.4f);
        set1f(shader, "u_orbSoftness", 1.0f);

        set1f(shader, "u_starStrength", 1.0f);
        set1f(shader, "u_starSharpness", 20.0f);
        set1f(shader, "u_starCount", 4.0f);
        set1f(shader, "u_starFalloff", 0.65f);
        set1f(shader, "u_starRotateSpeed", 0.0f);
        set1f(shader, "u_starPhase", 0.0f);

        set1f(shader, "u_glowMix", 1.0f);
        set1f(shader, "u_paletteSpeed", 0.20f);
        set1f(shader, "u_paletteShift", null);
        set1f(shader, "u_paletteSaturation", null);

        set1f(shader, "u_t1", u_t1);
        set1f(shader, "u_t2", u_t2);
        set1f(shader, "u_t3", u_t3);

        if (u_glowTint != null && u_glowTint.length >= 3) {
            set3f(shader, "u_glowTint", u_glowTint[0], u_glowTint[1], u_glowTint[2]);
        }

        if (u_c0 != null && u_c0.length >= 3)
            set3f(shader, "u_c0", u_c0[0], u_c0[1], u_c0[2]);
        if (u_c1 != null && u_c1.length >= 3)
            set3f(shader, "u_c1", u_c1[0], u_c1[1], u_c1[2]);
        if (u_c2 != null && u_c2.length >= 3)
            set3f(shader, "u_c2", u_c2[0], u_c2[1], u_c2[2]);
        if (u_c3 != null && u_c3.length >= 3)
            set3f(shader, "u_c3", u_c3[0], u_c3[1], u_c3[2]);
        if (u_lumaCoeff != null && u_lumaCoeff.length >= 3)
            set3f(shader, "u_lumaCoeff", u_lumaCoeff[0], u_lumaCoeff[1], u_lumaCoeff[2]);
    }

    private static void set1f(ShaderInstance shader, String name, Float value) {
        if (value == null) return;
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    private static void set3f(ShaderInstance shader, String name, float x, float y, float z) {
        Uniform u = shader.getUniform(name);
        if (u != null) u.set(x, y, z);
    }
}
