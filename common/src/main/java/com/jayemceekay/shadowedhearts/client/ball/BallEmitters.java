package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.particle.PenumbraTrailSystem;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.jayemceekay.shadowedhearts.config.IClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
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
import java.util.function.BooleanSupplier;

/**
 * Client-side emitter system for thrown Poké Balls. Mirrors AuraEmitters pattern but renders
 * a glowing orb billboard and a motion trail, driven by server-authoritative state sync.
 */
public final class BallEmitters {
    private BallEmitters() {
    }

    private static final Map<Integer, BallInstance> ACTIVE = new ConcurrentHashMap<>();
    private static WeakReference<Object> clientLevelRef = new WeakReference<>(null);

    /**
     * Called on client when a ball entity is created/loaded.
     *
     * <p>Balls may need a few ticks before aspects arrive from the
     * server, so unresolved instances are tracked briefly and classified later
     * in {@link #onRender(net.minecraft.client.Camera, float)}.
     */
    public static void startForEntity(EmptyPokeBallEntity entity) {
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)) return;
        long now = mc.level.getGameTime();
        // Register all balls; snag/penumbra classification is deferred until aspects sync
        ACTIVE.put(entity.getId(), new BallInstance(entity.getId(), entity, now, 4, 400, 8, false, false, false));
    }

    /**
     * Begins a short fade-out for the despawned entity.
     */
    public static void onEntityDespawn(int entityId) {
        var mc = Minecraft.getInstance();
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        ACTIVE.computeIfPresent(entityId, (id, inst) -> {
            inst.beginImmediateFadeOut(now, 6);
            return inst;
        });
    }

    /**
     * Per-frame world-render entry point for CPU-side tracking and non-FBO VFX.
     *
     * <p>Framebuffer-dependent effects are intentionally deferred to
     * {@link #onRenderFBO(net.minecraft.client.Camera, float)} so shader mods and
     * the vanilla renderer have restored a predictable target before offscreen
     * passes run.
     */
    public static void onRender(net.minecraft.client.Camera camera, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)) return;

        // Tick and render all active Snag Capture VFX sequences
        SnagCaptureVfx.tickAll(1f / 20f);
        DarkBallCaptureVfx.tickAll(1f / 20f);

        // Main pass: billboards, flashes, orb, lens flare (non-density effects)
        SnagCaptureVfx.renderAll(camera, partialTicks);

        // Snag throw VFX: orange glow + diffraction spikes on in-flight snag balls
        SnagThrowVfx.pruneAll();
        SnagThrowVfx.renderAll(camera, partialTicks);

        // Tick and render shake-phase VFX for Snag Balls
        IClientConfig cfg = ShadowedHeartsConfigs.getInstance().getClientConfig();
        if (cfg.snagShakeVfxEnabled()) {
            SnagShakeVfx.tickAll();
            SnagShakeVfx.renderAll(camera, partialTicks);
        }

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
            if (!useEnt) continue;

            // Lazy-resolve snag/penumbra once aspects sync arrives from server
            if (!inst.resolved && ent instanceof EmptyPokeBallEntity ball) {
                boolean hasSnagAspect = ball.getAspects().contains("snag_ball");
                String ballName = ball.getPokeBall().getName().getPath();
                boolean isPenumbra = ballName.equals("penumbra_ball");
                boolean isDarkBall = ballName.equals("dark_ball");
                if (hasSnagAspect || isPenumbra || isDarkBall) {
                    inst.isSnagBall = hasSnagAspect && !isDarkBall;
                    inst.isPenumbraBall = isPenumbra;
                    inst.isDarkBall = isDarkBall;
                    inst.resolved = true;
                }
                // If neither after ~5 ticks, remove to avoid tracking every ball forever
                if (!inst.resolved && mc.level.getGameTime() - inst.startTick > 5) {
                    ACTIVE.remove(en.getKey());
                    continue;
                }
                if (!inst.resolved) continue; // still waiting for sync
            }

            double ix, iy, iz;
            ix = Mth.lerp(partialTicks, ent.xOld, ent.getX());
            iy = Mth.lerp(partialTicks, ent.yOld, ent.getY());
            iz = Mth.lerp(partialTicks, ent.zOld, ent.getZ());

            // Feed trail samples in world space; render in the entity-origin pose later
            // We use the interpolated world position
            BallTrailManager.addPointForId(inst.entityId, ix, iy, iz);

            // Emit penumbra trail particles (shadow aura fog puffs) behind the ball
            if (inst.isPenumbraBall && ent instanceof EmptyPokeBallEntity) {
                emitPenumbraTrailParticles(mc, ent, ix, iy, iz, inst);
            }

            // Emit snag trail density puffs along the ball's trajectory
            if (inst.isSnagBall) {
                emitSnagTrailPuffs(mc, ent, ix, iy, iz, inst);
            }

            // Start throw VFX while the ball is in flight (suppressed once capture VFX takes over)
            boolean snagCaptureVfxActive = SnagCaptureVfx.get(inst.entityId) != null;
            if (inst.isSnagBall && !snagCaptureVfxActive && ent instanceof EmptyPokeBallEntity ball) {
                if(ball.getCaptureState() == EmptyPokeBallEntity.CaptureState.NOT) {
                    SnagThrowVfx.start(ball);
                }
            }

            // Detect when a Snag Ball enters the capture beam phase (beamMode == 3 on the phased Pokémon)
            // and start the SnagCaptureVfx if not already running.
            if (inst.isSnagBall && !snagCaptureVfxActive && ent instanceof EmptyPokeBallEntity ball) {
                maybeStartSnagVfx(ball, mc);
            }
            if (inst.isDarkBall && ent instanceof EmptyPokeBallEntity ball) {
                inst.darkCaptureStart.tryStart(
                        ball.getCaptureState() == EmptyPokeBallEntity.CaptureState.HIT,
                        () -> maybeStartDarkBallVfx(ball, mc));
            }

            // Track Snag Balls that have entered the shake phase
            if (inst.isSnagBall && ent instanceof EmptyPokeBallEntity ball2) {
                SnagShakeVfx.maybeTrack(ball2);
            }
        }
    }

    /**
     * Renders the FBO-dependent density and bloom pipelines for Snag Capture VFX.
     * <p>
     * This must be called at a late render stage (e.g. {@code WorldRenderEvents.END}
     * on Fabric or {@code AFTER_PARTICLES} on NeoForge) so that Iris/Oculus has
     * already finished its own pipeline and restored the vanilla framebuffer.
     * Non-FBO rendering (billboards, trails, sparks) stays in {@link #onRender}.
     */
    public static void onRenderFBO(net.minecraft.client.Camera camera, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (!prepareClientLevel(mc)) return;

        // Snag trail density pipeline (orange smoke and purple motes)
        SnagTrailDensitySystem.renderDensityPipeline(camera, partialTicks);

        // Density FBO pass: render beam splats + particle splats → blur → composite
        if (!SnagCaptureVfx.getActiveInstances().isEmpty()
                && SnagDensityFBO.beginDensityPass()) {
            SnagCaptureVfx.renderAllDensityPass(camera, partialTicks);
            SnagDensityFBO.endDensityPass();
            SnagDensityFBO.blur();
            SnagDensityFBO.composite();
        }

        if (!DarkBallCaptureVfx.getActiveInstances().isEmpty()) {
            DarkBallCaptureVfx.beginDirectCompositeFrame();
            if (DarkBallDensityFBO.beginDensityPass(true, true)) {
                DarkBallCaptureVfx.renderAllDensityPass(camera, partialTicks);
                DarkBallDensityFBO.endDensityPass();
                DarkBallCaptureVfx.finishDirectCompositeFrame(
                        DarkBallDensityFBO.composite());
            }
        }

        // Bloom pass: re-render snag VFX into a half-res FBO, blur, composite
        IClientConfig cfg = ShadowedHeartsConfigs.getInstance().getClientConfig();
        if (cfg.snagBloomEnabled() && SnagBloomFBO.shouldRun() && SnagBloomFBO.beginBloomPass()) {
            SnagCaptureVfx.renderAll(camera, partialTicks);
            SnagBloomFBO.endBloomPass();
            SnagBloomFBO.blurAndComposite();
        }

    }

    private static boolean prepareClientLevel(Minecraft mc) {
        Object currentLevel = mc == null ? null : mc.level;
        if (currentLevel == null) {
            DarkBallCaptureVfx.clearAll();
            clientLevelRef = new WeakReference<>(null);
            return false;
        }

        if (clientLevelRef.get() != currentLevel) {
            // A render callback is not guaranteed while the title screen is
            // active. Clear stale per-capture GPU resources on the first hook
            // for a replacement world as well as on an observed null world.
            DarkBallCaptureVfx.clearAll();
            clientLevelRef = new WeakReference<>(currentLevel);
        }
        return true;
    }

    /**
     * Scans the level for a PokemonEntity whose {@code phasingTargetId} matches this ball's ID
     * and whose {@code beamMode} is 3 (the capture-beam recall phase). If found, starts the
     * {@link SnagCaptureVfx} for this ball.
     */
    private static void maybeStartSnagVfx(EmptyPokeBallEntity ball, Minecraft mc) {
        if (mc.level == null) return;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PokemonEntity pokemon)) continue;
            if (pokemon.getPhasingTargetId() == ball.getId() && pokemon.getBeamMode() == 3) {
                SnagCaptureVfx.start(ball, pokemon);
                return;
            }
        }
    }

    private static boolean maybeStartDarkBallVfx(EmptyPokeBallEntity ball, Minecraft mc) {
        if (mc.level == null) return false;
        if (ball.getCaptureState() != EmptyPokeBallEntity.CaptureState.HIT) return false;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PokemonEntity pokemon)) continue;
            if (pokemon.getPhasingTargetId() == ball.getId()) {
                DarkBallCaptureVfx.startAtHit(ball, pokemon);
                return true;
            }
        }
        return false;
    }

    static final class OneShotStartLatch {
        private boolean started;

        boolean tryStart(boolean eligible, BooleanSupplier starter) {
            if (!eligible || started || !starter.getAsBoolean()) {
                return false;
            }
            started = true;
            return true;
        }
    }

    private static void renderOrb(PoseStack poseStack, MultiBufferSource buffer, float partialTicks) {
        final int FULLBRIGHT = 0x00F000F0;
        poseStack.pushPose();
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float base = 0.85f;
        poseStack.scale(base, base, base);
        if (ModShaders.BALL_ORB_GLOW != null) {
            try {
                apply(ModShaders.BALL_ORB_GLOW);
            } catch (Throwable ignored) {
            }
        }
        VertexConsumer vc = buffer.getBuffer(BallRenderTypes.ballOrbGlow());
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
        if (ModShaders.BALL_ORB_GLOW != null) {
            try {
                apply(ModShaders.BALL_ORB_GLOW);
            } catch (Throwable ignored) {
            }
        }
        VertexConsumer vc = buffers.getBuffer(BallRenderTypes.ballOrbGlowHud());
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

    /** Particles emitted per block of travel distance for snag ball trails. */
    private static final double SNAG_PARTICLES_PER_BLOCK = 14.0;

    /**
     * Spawns snag trail density puffs along the ball's trajectory, similar to
     * {@link #emitPenumbraTrailParticles} but feeding into
     * {@link SnagTrailDensitySystem} instead of the penumbra particle system.
     */
    private static void emitSnagTrailPuffs(Minecraft mc, Entity ent,
                                            double ix, double iy, double iz,
                                            BallInstance inst) {
        if (mc.level == null) return;

        long gameTime = mc.level.getGameTime();
        if (gameTime == inst.lastSnagSpawnTick) {
            inst.lastSnagTrailPos = new Vec3(ix, iy + 0.15, iz);
            return;
        }
        inst.lastSnagSpawnTick = gameTime;

        double vx = ent.getDeltaMovement().x;
        double vy = ent.getDeltaMovement().y;
        double vz = ent.getDeltaMovement().z;

        Vec3 curr = new Vec3(ix, iy + 0.15, iz);
        Vec3 prev = inst.lastSnagTrailPos;

        if (prev != null) {
            double gap = prev.distanceTo(curr);
            int count = Math.max(6, Math.min(28, (int) (gap * SNAG_PARTICLES_PER_BLOCK)));
            for (int i = 0; i < count; i++) {
                double t = (double) i / count;
                double lx = Mth.lerp(t, prev.x, curr.x) + (mc.level.random.nextFloat() - 0.5) * 0.52;
                double ly = Mth.lerp(t, prev.y, curr.y) + (mc.level.random.nextFloat() - 0.5) * 0.52;
                double lz = Mth.lerp(t, prev.z, curr.z) + (mc.level.random.nextFloat() - 0.5) * 0.52;
                SnagTrailDensitySystem.registerOrangeSmokePuff(lx, ly, lz, vx, vy, vz);
                // Emit purple motes at lower density (roughly 1 in 3 positions)
                if (i % 3 == 0) {
                    SnagTrailDensitySystem.registerPurpleMote(lx, ly, lz, vx, vy, vz);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                double ox = curr.x + (mc.level.random.nextFloat() - 0.5) * 0.25;
                double oy = curr.y + (mc.level.random.nextFloat() - 0.5) * 0.20;
                double oz = curr.z + (mc.level.random.nextFloat() - 0.5) * 0.25;
                SnagTrailDensitySystem.registerOrangeSmokePuff(ox, oy, oz, vx, vy, vz);
                SnagTrailDensitySystem.registerPurpleMote(ox, oy, oz, vx, vy, vz);
            }
        }
        inst.lastSnagTrailPos = curr;
    }

    private static final class BallInstance {
        /**
         * Lightweight tracking state for one rendered ball entity.
         *
         * <p>It stores only classification and trail-emission bookkeeping; the
         * actual particles, trails, and density fields are owned by their
         * specialized systems.
         */
        final int entityId;
        final WeakReference<Entity> entityRef;
        boolean isSnagBall;
        boolean isPenumbraBall;
        boolean isDarkBall;
        final OneShotStartLatch darkCaptureStart = new OneShotStartLatch();
        boolean resolved = false;
        long startTick;
        int fadeInTicks;
        int sustainTicks;
        int fadeOutTicks;
        Vec3 lastTrailPos;
        long lastSpawnTick = Long.MIN_VALUE;
        Vec3 lastSnagTrailPos;
        long lastSnagSpawnTick = Long.MIN_VALUE;

        BallInstance(int entityId, @Nullable Entity ent, long startTick, int fi, int sus, int fo,
                     boolean isSnagBall, boolean isPenumbraBall, boolean isDarkBall) {
            this.entityId = entityId;
            this.entityRef = new WeakReference<>(ent);
            this.isSnagBall = isSnagBall;
            this.isPenumbraBall = isPenumbraBall;
            this.isDarkBall = isDarkBall;
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
        // These defaults are intentionally sparse: null means "leave the shader
        // JSON/default uniform alone" so older shader variants keep working.
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

        set1f(shader, "u_time", time);

        set1f(shader, "u_rimStrength", null);
        set1f(shader, "u_pulseSpeed", 0.125f);
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
