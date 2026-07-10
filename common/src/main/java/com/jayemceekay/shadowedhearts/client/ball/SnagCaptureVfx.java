package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jayemceekay.shadowedhearts.client.ball.vfx.ConvergenceRing;
import com.jayemceekay.shadowedhearts.client.ball.vfx.HighlightSpark;
import com.jayemceekay.shadowedhearts.client.ball.vfx.NeedleSpark;
import com.jayemceekay.shadowedhearts.client.ball.vfx.ShockRing;
import com.jayemceekay.shadowedhearts.client.ball.vfx.SnagProjectile;
import com.jayemceekay.shadowedhearts.client.ball.vfx.SuctionMote;
import com.jayemceekay.shadowedhearts.client.ball.vfx.TrailNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side VFX manager for the Snag Ball capture sequence.
 * Modeled after Pokémon Colosseum's snag animation — five white streaks
 * that open outward from the ball then close inward around the Pokémon
 * like a hand grabbing it.
 *
 * <p>Five overlapping concurrent tracks (relative to beamMode == 3 start):
 * <pre>
 *   Track 1 — Beam motion:       0.13–1.13s  (fan out, wrap, snap, fray, dissolve)
 *   Track 2 — Pokémon dissolve:  0.73–1.20s  (tint → silhouette break → full conversion)
 *   Track 3 — Cloud formation:   0.78–1.28s  (dense core + shell + trailing fragments)
 *   Track 4 — Return suction:    1.05–1.73s  (cloud streams back toward ball)
 *   Track 5 — Ball intake:       1.31–1.85s  (glow, absorb, contraction flash)
 *   Convergence flash:           ~0.90s      (white impact flash + splinter sparks)
 *   VFX end:                     ~2.03s      (drop / shake logic resumes)
 * </pre>
 * <p>The windows intentionally overlap so all five processes are active
 * during the convergence period, making the effect feel like a single
 * continuous energy transfer rather than several sequential events.
 */
public final class SnagCaptureVfx {

    // ── Timing constants (seconds) — five overlapping concurrent tracks ─────
    // These windows intentionally overlap so the effect reads as a single
    // continuous energy transfer rather than several sequential events.
    public static final float FLASH_START         = 0.06f;

    // Track 1: Beam motion
    public static final float BEAM_START          = 0.13f;
    public static final float BEAM_END            = 1.13f;

    // Convergence impact (flash / sparks moment during beam closure)
    public static final float CONVERGE_TIME       = 0.90f;

    // Track 2: Pokémon dissolution
    public static final float DISSOLVE_START      = 0.73f;
    public static final float DISSOLVE_END        = 1.20f;

    // Track 3: Cloud formation
    public static final float CLOUD_FORM_START    = 0.78f;
    public static final float CLOUD_FORM_END      = 1.28f;

    // Track 4: Return suction
    public static final float CLOUD_RETURN_START  = 1.05f;
    public static final float CLOUD_RETURN_END    = 1.73f;

    // Track 5: Ball intake reaction
    public static final float BALL_REACT_START    = 1.31f;
    public static final float BALL_ABSORB_END     = 1.85f;

    public static final float VFX_END             = 2.03f;

    // Number of arcing capture beams
    private static final int BEAM_COUNT = 5;
    private static final float TAU = (float) (Math.PI * 2.0);

    // ── Global instance registry (by ball entity ID) ────────────────────────
    private static final Map<Integer, SnagCaptureVfx> ACTIVE = new ConcurrentHashMap<>();

    /** Timestamp of the last tickAll call, for computing real frame delta. */
    private static long lastTickNanos = 0;

    public static void start(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        // Don't restart if a VFX is already running for this ball
        ACTIVE.putIfAbsent(ball.getId(), new SnagCaptureVfx(ball, pokemon));
    }

    public static void stop(int ballId) {
        ACTIVE.remove(ballId);
    }

    public static SnagCaptureVfx get(int ballId) {
        return ACTIVE.get(ballId);
    }

    /** Returns the collection of all active VFX instances. */
    public static java.util.Collection<SnagCaptureVfx> getActiveInstances() {
        return ACTIVE.values();
    }

    /** Find the active VFX instance for a given Pokémon entity ID, if any. */
    public static SnagCaptureVfx getByPokemonId(int pokemonId) {
        for (SnagCaptureVfx vfx : ACTIVE.values()) {
            if (vfx.pokemonId == pokemonId) return vfx;
        }
        return null;
    }

    /**
     * Returns the desired entityScaleModifier for a Pokémon being captured by a Snag Ball.
     * Shrink begins at DISSOLVE_START (during the final ~35% of beam closure) and
     * reaches zero at DISSOLVE_END, overlapping with cloud formation.
     */
    public float getDesiredPokemonScale() {
        if (age < DISSOLVE_START) return 1f;
        if (age > DISSOLVE_END)   return 0f;
        float t = remapClamped(age, DISSOLVE_START, DISSOLVE_END);
        t = t * t; // ease-in: slow start then aggressive yank
        return 1f - t;
    }

    /**
     * Returns the dissolve progress (0.0 = fully solid, 1.0 = fully dissolved).
     * Starts at DISSOLVE_START and reaches 1.0 at DISSOLVE_END.
     * The dissolve overlaps with beam convergence and cloud formation so
     * the Pokémon appears to break apart into energy while beams are still active.
     */
    public float getDissolveProgress() {
        return remapClamped(age, DISSOLVE_START, DISSOLVE_END);
    }

    /** Returns the current VFX age in seconds. */
    public float getAge() {
        return age;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Config helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static float configParticleMultiplier() {
        return com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                .getInstance().getClientConfig().snagParticleMultiplier();
    }

    private static boolean configReducedMotion() {
        return com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                .getInstance().getClientConfig().snagReducedMotion();
    }

    private static boolean configLensFlareEnabled() {
        return com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
                .getInstance().getClientConfig().snagLensFlareEnabled();
    }

    /** Tick all active VFX instances. Call from a client-tick handler. */
    public static void tickAll(float ignoredDt) {
        long now = System.nanoTime();
        float dt;
        if (lastTickNanos == 0 || ACTIVE.isEmpty()) {
            dt = 0f; // First frame or no active VFX — don't advance
            lastTickNanos = now;
        } else {
            dt = (now - lastTickNanos) / 1_000_000_000f; // nanoseconds → seconds
            dt = Math.min(dt, 0.1f); // Clamp to prevent huge jumps (e.g., lag spikes)
        }
        lastTickNanos = now;

        final float finalDt = dt;
        ACTIVE.entrySet().removeIf(e -> {
            e.getValue().tick(finalDt);
            return e.getValue().age > VFX_END + 0.5f;
        });
    }

    /** Render all active VFX instances (main pass — billboards, flashes, orb). */
    public static void renderAll(Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Vec3 camPos = camera.getPosition();

        for (SnagCaptureVfx vfx : ACTIVE.values()) {
            vfx.render(camera, camPos, partialTick, buf);
        }
        buf.endBatch();
    }

    /**
     * Render all active VFX density splats into the currently bound FBO.
     * Called between {@link SnagDensityFBO#beginDensityPass()} and
     * {@link SnagDensityFBO#endDensityPass()} to fill the density buffer
     * with beam splats and particle splats.
     */
    public static void renderAllDensityPass(Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = camera.getPosition();

        for (SnagCaptureVfx vfx : ACTIVE.values()) {
            vfx.renderDensityPass(camera, camPos, partialTick);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance state
    // ═══════════════════════════════════════════════════════════════════════════

    private final int ballId;
    private final int pokemonId;
    private final Random rng = new Random();

    /** Age in seconds since this VFX started. */
    public float age = 0f;

    private boolean spawnedOpenSparks     = false;
    private boolean spawnedConvergeSparks = false;
    private boolean spawnedConvergeHighlights = false;
    private boolean spawnedConvergeRing   = false;
    private boolean spawnedSuctionMotes   = false;
    private boolean spawnedIntakeFlash    = false;

    private Vec3 convergencePos = null;

    private final List<HighlightSpark> highlightSparks = new ArrayList<>();
    private final List<ConvergenceRing> convergenceRings = new ArrayList<>();
    private final List<NeedleSpark>    sparks      = new ArrayList<>();
    private final List<ShockRing>      rings       = new ArrayList<>();
    private final List<SuctionMote>    motes       = new ArrayList<>();
    private final List<SnagProjectile> projectiles = new ArrayList<>();
    private boolean projectilesInitialized = false;

    /** Last dt from tick(), used in render for trail emission. */
    private float lastDt = 0f;

    /** Returns the current visual ball position (always the real entity position). */
    public Vec3 getVisualBallPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity ent = mc.level.getEntity(ballId);
        return ent != null ? ent.position() : null;
    }

    private SnagCaptureVfx(EmptyPokeBallEntity ball, PokemonEntity pokemon) {
        this.ballId    = ball.getId();
        this.pokemonId = pokemon.getId();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Update
    // ═══════════════════════════════════════════════════════════════════════════

    private void tick(float dt) {
        age += dt;
        lastDt = dt;

        // Tick and prune sparks
        highlightSparks.removeIf(s -> s.tick(dt));
        convergenceRings.removeIf(r -> r.tick(dt));
        sparks.removeIf(s -> s.tick(dt));
        rings.removeIf(r -> r.tick(dt));
        motes.removeIf(m -> m.tick(dt));

        // Tick projectile trail nodes
        for (SnagProjectile proj : projectiles) {
            proj.trail.removeIf(n -> n.tick(dt));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════════════

    private void render(Camera camera, Vec3 camPos, float partialTick,
                        MultiBufferSource.BufferSource buf) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity ballEnt    = mc.level.getEntity(ballId);
        Entity pokemonEnt = mc.level.getEntity(pokemonId);

        if (!(ballEnt instanceof EmptyPokeBallEntity ball)) return;
        if (!(pokemonEnt instanceof PokemonEntity pokemon)) return;

        // Interpolated world positions
        Vec3 ballPos = new Vec3(
                Mth.lerp(partialTick, ball.xOld, ball.getX()),
                Mth.lerp(partialTick, ball.yOld, ball.getY()),
                Mth.lerp(partialTick, ball.zOld, ball.getZ())
        );

        Vec3 pokemonCenter = pokemon.getBoundingBox().getCenter()
                .add(0, pokemon.getBbHeight() * 0.05, 0);

        // Compute & cache convergence point
        if (convergencePos == null) {
            Vec3 fromPkmnToBall = ballPos.subtract(pokemonCenter).normalize();
            convergencePos = pokemonCenter
                    .add(0, pokemon.getBbHeight() * 0.15, 0)
                    .add(fromPkmnToBall.scale(0.25))
                    .add(0, 0.15, 0);
        }

        // Compute cloud position: starts at convergence, lerps back to ball
        Vec3 cloudPos = computeCloudPos(ballPos);

        // ── Opening flash + sparks ──────────────────────────────────────────
        if (age >= FLASH_START && age < FLASH_START + 0.08f && !configReducedMotion()) {
            float flashAge   = age - FLASH_START;
            float flashAlpha = 1f - (flashAge / 0.08f);
            //renderBillboardFlash(ballPos, camPos, camera, flashAlpha * 1.2f, 0.8f, buf);
        }

        if (age >= FLASH_START && !spawnedOpenSparks) {
            spawnedOpenSparks = true;
            float pm = configParticleMultiplier();
            spawnNeedleSparks(ballPos, Math.round(12 * pm), 0.08f, 0.40f, false);
        }

        // ── Beam rendering moved to density pass (renderDensityPass) ─────────

        // ── Convergence flash + sparks (strong white impact flash) ───────────
        if (age >= CONVERGE_TIME && age < CONVERGE_TIME + 0.08f && !configReducedMotion()) {
            float flashAge   = age - CONVERGE_TIME;
            float flashAlpha = 1f - (flashAge / 0.08f);
            //renderBillboardFlash(pokemonCenter, camPos, camera, flashAlpha * 2.2f, 1.8f, buf);
        }

        // Brief lens flare at convergence
        if (age >= CONVERGE_TIME && age < CONVERGE_TIME + 0.05f && configLensFlareEnabled()) {
            float flareAge   = age - CONVERGE_TIME;
            float flareAlpha = 1f - (flareAge / 0.05f);
            renderLensFlare(pokemonCenter, camPos, camera, flareAlpha, 2.0f, buf);
        }

        if (age >= CONVERGE_TIME && !spawnedConvergeSparks) {
            spawnedConvergeSparks = true;
            float pm = configParticleMultiplier();
            spawnNeedleSparks(pokemonCenter, Math.round(28 * pm), 0.12f, 0.60f, true);

            // Beam collision splinters
            if (pm > 0f) {
                spawnBeamCollisionSplinters(ballPos, pokemonCenter);
            }
        }

        // ── Convergence highlight sparks (bright orange/white burst) ────────
        if (age >= CONVERGE_TIME && !spawnedConvergeHighlights) {
            spawnedConvergeHighlights = true;
            spawnConvergenceHighlightSparks(convergencePos != null ? convergencePos : pokemonCenter);
        }

        // Render highlight sparks (velocity-aligned needles via sparkGlowAdditive)
        renderHighlightSparks(highlightSparks, camPos, buf);

        // ── Purple convergence ring (single ring tracking absorption mass) ──
        if (age >= CONVERGE_TIME && !spawnedConvergeRing) {
            spawnedConvergeRing = true;
            // Single ring that lives from convergence through ball absorption
            float ringLife = BALL_ABSORB_END - CONVERGE_TIME;
            convergenceRings.add(new ConvergenceRing(ringLife, 0.75f));
        }

        // Render convergence ring centered on the current cloud/particle mass position
        renderConvergenceRings(convergenceRings, cloudPos, camPos, camera, buf);

        // ── Diffraction spikes on absorption cloud (inside purple ring) ──
        if (age >= CONVERGE_TIME && age < BALL_ABSORB_END && !configReducedMotion()) {
            float spikeProgress = remapClamped(age, CONVERGE_TIME, BALL_ABSORB_END);

            // Quick fade in over first 10%, hold, fade out over last 25%
            float spikeAlpha;
            if (spikeProgress < 0.10f) {
                spikeAlpha = spikeProgress / 0.10f;
            } else if (spikeProgress > 0.75f) {
                spikeAlpha = (1f - spikeProgress) / 0.25f;
            } else {
                spikeAlpha = 1f;
            }

            // Subtle pulse
            float pulse = 0.85f + 0.15f * (float) Math.sin(age * 12.0f);
            spikeAlpha *= pulse;

            // Shrink spikes as cloud moves toward ball
            float spikeRadius = Mth.lerp(spikeProgress, 1.0f, 0.6f);

            // Slow rotation for shimmer
            float rotAngle = age * 0.3f;

            renderDiffractionSpikes(cloudPos, camPos, camera, spikeAlpha * 0.7f, spikeRadius, rotAngle, buf);
        }

        // ── Spawn suction / return motes at cloud formation ─────────────────
        if (age >= CLOUD_FORM_START && !spawnedSuctionMotes) {
            spawnedSuctionMotes = true;
            spawnSuctionMotes(pokemon.getBoundingBox());
        }

        // ── Ball intake reaction — starts before cloud arrives ─────────────────
        if (age >= BALL_REACT_START && age < BALL_ABSORB_END && !configReducedMotion()) {
            float intakeAge   = age - BALL_REACT_START;
            float intakeDur   = BALL_ABSORB_END - BALL_REACT_START;
            float intakeAlpha = 1f - Mth.clamp(intakeAge / intakeDur, 0f, 1f);
            float intakeRadius = Mth.lerp(intakeAge / intakeDur, 0.6f, 0.15f);
            //renderBillboardFlash(ballPos, camPos, camera, intakeAlpha * 1.5f, intakeRadius, buf);
        }

        if (age >= BALL_REACT_START && !spawnedIntakeFlash) {
            spawnedIntakeFlash = true;
            float pm = configParticleMultiplier();
            spawnNeedleSparks(ballPos, Math.round(10 * pm), 0.06f, 0.30f, true);
        }

        // ── Sparks, rings, motes, and cloud splats rendered through density FBO
        // ── (see renderDensityPass) for metaball merging with beam splats.
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Highlight spark system (bright orange/white convergence burst)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Spawns bright orange/white highlight sparks bursting in all directions
     * from the convergence point. Short-lived (~0.2s) needle sparks.
     */
    private void spawnConvergenceHighlightSparks(Vec3 origin) {
        float pm = configParticleMultiplier();
        int count = Math.round(36 * pm) + rng.nextInt(Math.max(1, Math.round(12 * pm)));
        for (int i = 0; i < count; i++) {
            // Uniform sphere direction
            double theta = rng.nextDouble() * TAU;
            double phi   = Math.acos(2.0 * rng.nextDouble() - 1.0);
            double vx = Math.sin(phi) * Math.cos(theta);
            double vy = Math.sin(phi) * Math.sin(theta);
            double vz = Math.cos(phi);

            float speed = 2.5f + rng.nextFloat() * 4.0f;
            Vec3 vel = new Vec3(vx, vy, vz).scale(speed);

            float life = 0.4f + rng.nextFloat() * 0.20f; // 0.10–0.20s

            // Bright orange/white highlight palette
            int sparkType = rng.nextInt(5);
            int r, g, b;
            switch (sparkType) {
                case 0  -> { r = 255; g = 245 + rng.nextInt(10); b = 220 + rng.nextInt(35); } // hot white
                case 1  -> { r = 255; g = 220 + rng.nextInt(30); b = 160 + rng.nextInt(60); } // warm white
                case 2  -> { r = 255; g = 180 + rng.nextInt(40); b =  60 + rng.nextInt(60); } // bright orange
                case 3  -> { r = 255; g = 150 + rng.nextInt(50); b =  30 + rng.nextInt(50); } // deep orange
                default -> { r = 255; g = 200 + rng.nextInt(40); b = 100 + rng.nextInt(80); } // gold-orange
            }

            highlightSparks.add(new HighlightSpark(origin, vel, life, r, g, b));
        }
    }

    /**
     * Renders highlight sparks as velocity-aligned elongated needle quads
     * using sparkGlowAdditive render type for bright, glowing appearance.
     */
    private static void renderHighlightSparks(List<HighlightSpark> sparks, Vec3 camPos,
                                               MultiBufferSource.BufferSource buf) {
        if (sparks.isEmpty()) return;

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.sparkGlowAdditive());
        org.joml.Vector3f lookVec = Minecraft.getInstance().getEntityRenderDispatcher().camera.getLookVector();
        Vec3 camForward = new Vec3(lookVec.x, lookVec.y, lookVec.z);

        for (HighlightSpark spark : sparks) {
            float a = spark.alpha();
            if (a < 0.01f) continue;

            Vec3 vel = spark.vel;
            double speed = vel.length();
            if (speed < 0.001) continue;

            Vec3 dir = vel.normalize();
            Vec3 side = dir.cross(camForward);
            double sideLen = side.length();
            if (sideLen < 0.001) {
                side = dir.cross(new Vec3(0, 1, 0));
                sideLen = side.length();
                if (sideLen < 0.001) side = dir.cross(new Vec3(1, 0, 0));
                sideLen = side.length();
            }
            side = side.scale(1.0 / sideLen);

            // Needle dimensions
            float halfLength = 0.18f * a;
            float halfWidth  = 0.018f * a;

            Vec3 forward = dir.scale(halfLength);
            Vec3 right   = side.scale(halfWidth);

            Vec3 center = spark.pos.subtract(camPos);
            Vec3 p0 = center.subtract(forward).subtract(right);
            Vec3 p1 = center.add(forward).subtract(right);
            Vec3 p2 = center.add(forward).add(right);
            Vec3 p3 = center.subtract(forward).add(right);

            PoseStack ps = new PoseStack();
            var last = ps.last();
            org.joml.Matrix4f pose = last.pose();
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Purple convergence ring rendering
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Renders convergence rings as a single camera-facing shock_ring billboard
     * centered on the current particle mass position (cloudPos), tracking it as
     * it moves from the convergence point back to the ball.
     */
    private static void renderConvergenceRings(List<ConvergenceRing> rings, Vec3 centerPos,
                                                Vec3 camPos, Camera camera,
                                                MultiBufferSource.BufferSource buf) {
        if (rings.isEmpty()) return;

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.shockRingGlowAdditive());

        for (ConvergenceRing ring : rings) {
            float alpha = ring.alpha();
            if (alpha < 0.01f) continue;

            float size = ring.radius();

            // Purple glow color (bright purple-magenta)
            float cr = 0.65f;
            float cg = 0.20f;
            float cb = 1.0f;
            float density = alpha * 0.65f;

            PoseStack ps = new PoseStack();
            ps.pushPose();
            ps.translate(centerPos.x - camPos.x, centerPos.y - camPos.y, centerPos.z - camPos.z);
            ps.mulPose(camera.rotation());
            ps.scale(size, size, 1f);
            org.joml.Matrix4f pose = ps.last().pose();

            // Camera-facing normal: transform (0,0,1) by camera rotation so lighting is uniform
            org.joml.Vector3f camNormal = new org.joml.Vector3f(0f, 0f, 1f);
            camera.rotation().transform(camNormal);

            vc.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setColor(cr, cg, cb, density).setLight(0xF000F0).setNormal(camNormal.x, camNormal.y, camNormal.z);
            vc.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setColor(cr, cg, cb, density).setLight(0xF000F0).setNormal(camNormal.x, camNormal.y, camNormal.z);
            vc.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setColor(cr, cg, cb, density).setLight(0xF000F0).setNormal(camNormal.x, camNormal.y, camNormal.z);
            vc.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setColor(cr, cg, cb, density).setLight(0xF000F0).setNormal(camNormal.x, camNormal.y, camNormal.z);
            ps.popPose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Spark system
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Spawns velocity-aligned needle sparks radially from {@code origin}.
     *
     * @param biasInward  when true, biases some sparks back along incoming beam directions
     *                    for a "collision splinter" look at convergence
     */
    private void spawnNeedleSparks(Vec3 origin, int count, float minLife, float maxSpeed,
                                   boolean biasInward) {
        for (int i = 0; i < count; i++) {
            // Random sphere direction
            double theta = rng.nextDouble() * TAU;
            double phi   = Math.acos(2.0 * rng.nextDouble() - 1.0);
            double vx = Math.sin(phi) * Math.cos(theta);
            double vy = Math.sin(phi) * Math.sin(theta);
            double vz = Math.cos(phi);

            float speed = 0.8f + rng.nextFloat() * maxSpeed;
            Vec3 vel = new Vec3(vx, vy, vz).scale(speed);

            // For convergence sparks, bias ~30% of them along inward direction (downward here)
            if (biasInward && rng.nextFloat() < 0.3f) {
                vel = vel.add(0, -speed * 0.6, 0);
            }

            float life     = minLife + rng.nextFloat() * 0.25f;
            float length   = 0.20f + rng.nextFloat() * 0.55f;
            float width    = 0.012f + rng.nextFloat() * 0.022f;

            // Snag palette: purple/magenta dominant with occasional orange/white hot cores
            int sparkType = rng.nextInt(7);
            int r, g, b;
            switch (sparkType) {
                case 0  -> { r = 255; g = 210 + rng.nextInt(45); b = 180 + rng.nextInt(60); }  // warm white (rare)
                case 1  -> { r = 255; g = 140 + rng.nextInt(60); b =  50 + rng.nextInt(50); }  // bright orange (rare)
                case 2  -> { r = 230 + rng.nextInt(25); g = 80 + rng.nextInt(50); b = 200 + rng.nextInt(55); }  // magenta
                case 3  -> { r = 200 + rng.nextInt(40); g = 60 + rng.nextInt(40); b = 220 + rng.nextInt(35); }  // purple
                case 4  -> { r = 180 + rng.nextInt(50); g = 50 + rng.nextInt(40); b = 230 + rng.nextInt(25); }  // deep purple
                case 5  -> { r = 240 + rng.nextInt(15); g = 100 + rng.nextInt(50); b = 220 + rng.nextInt(35); } // bright magenta
                default -> { r = 160 + rng.nextInt(50); g = 40 + rng.nextInt(50); b = 200 + rng.nextInt(55); }  // violet
            }

            sparks.add(new NeedleSpark(origin, vel, life, length, width, r, g, b));
        }
    }

    /**
     * Spawns directional splinter sparks along each projectile's incoming direction
     * at the convergence point, plus small impact ripple rings at offset contact
     * points around the Pokémon surface. Uses the actual projectile end-points
     * for accurate beam arrival directions.
     */
    private void spawnBeamCollisionSplinters(Vec3 ballPos, Vec3 target) {
        // Colosseum snag energy: purple/magenta dominant with orange/white hot cores
        int[][] splinterColours = {
            { 230, 100, 240 },  // bright magenta
            { 255, 200,  80 },  // orange-gold hot core
            { 190,  60, 230 },  // deep purple
            { 240, 120, 220 },  // pink-magenta
            { 200,  70, 240 },  // violet
        };

        for (int b = 0; b < Math.min(projectiles.size(), BEAM_COUNT); b++) {
            SnagProjectile proj = projectiles.get(b);

            // Beam incoming direction: from cp2 toward cp3 (final approach)
            Vec3 incomingDir = proj.p3.subtract(proj.p2).normalize();
            Vec3 contactPoint = proj.p3;

            // Spawn 8 directional splinter sparks per beam, biased along reflection
            Vec3 reflectDir = incomingDir.scale(-1);
            for (int i = 0; i < 8; i++) {
                double theta = rng.nextDouble() * TAU;
                double phi   = Math.acos(2.0 * rng.nextDouble() - 1.0);
                double rx = Math.sin(phi) * Math.cos(theta);
                double ry = Math.sin(phi) * Math.sin(theta);
                double rz = Math.cos(phi);

                Vec3 randDir = new Vec3(rx, ry, rz);
                Vec3 splinterVel = reflectDir.scale(0.6).add(randDir.scale(0.4)).normalize();
                float speed = 1.5f + rng.nextFloat() * 2.0f;
                splinterVel = splinterVel.scale(speed);

                float life   = 0.08f + rng.nextFloat() * 0.15f;
                float length = 0.15f + rng.nextFloat() * 0.40f;
                float width  = 0.008f + rng.nextFloat() * 0.015f;

                int cr = splinterColours[b][0];
                int cg = splinterColours[b][1];
                int cb = splinterColours[b][2];

                sparks.add(new NeedleSpark(contactPoint, splinterVel, life, length, width, cr, cg, cb));
            }

            // Small impact ripple ring at each beam contact point
            rings.add(new ShockRing(contactPoint, 0.15f, 0.6f,
                    splinterColours[b][0], splinterColours[b][1], splinterColours[b][2]));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Suction mote system
    // ═══════════════════════════════════════════════════════════════════════════

    /** Spawns suction motes from random points on/around the Pokémon bounding box. */
    private void spawnSuctionMotes(AABB bounds) {
        int count = 64;
        float totalDuration = CLOUD_RETURN_END - CLOUD_FORM_START;

        for (int i = 0; i < count; i++) {
            // Random point on the extended bounding box surface
            double x = bounds.minX + rng.nextDouble() * (bounds.maxX - bounds.minX);
            double y = bounds.minY + rng.nextDouble() * (bounds.maxY - bounds.minY);
            double z = bounds.minZ + rng.nextDouble() * (bounds.maxZ - bounds.minZ);
            Vec3 start = new Vec3(x, y, z);

            float phase       = rng.nextFloat() * TAU;
            float startRadius = 0.25f + rng.nextFloat() * 0.55f;
            // Per-particle delay: dense center motes (low delay) lead,
            // outer fragments (high delay) lag behind for natural stretching
            float delay = rng.nextFloat() * 0.6f;
            // Stagger lifetimes so motes arrive at the ball across the whole absorption window
            float life = totalDuration * (0.4f + rng.nextFloat() * 0.6f);

            // Snag palette motes: purple/magenta dominant with orange/white hot cores
            int colorIdx = rng.nextInt(7);
            int r, g, b;
            switch (colorIdx) {
                case 0  -> { r = 255; g = 220; b = 190; }  // warm white (rare)
                case 1  -> { r = 255; g = 170; b =  60; }  // bright orange (rare)
                case 2  -> { r = 230; g =  90; b = 240; }  // bright magenta
                case 3  -> { r = 200; g =  60; b = 240; }  // purple
                case 4  -> { r = 180; g =  50; b = 230; }  // deep purple
                case 5  -> { r = 240; g = 110; b = 230; }  // pink-magenta
                default -> { r = 170; g =  45; b = 210; }  // violet
            }

            motes.add(new SuctionMote(start, phase, startRadius, delay, life, r, g, b));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Projectile system — initialization and trail emission
    // ═══════════════════════════════════════════════════════════════════════════

    /** Distance between consecutive trail nodes (blocks). */
    private static final double NODE_SPACING = 0.07;
    /** Maximum trail length before distance-based fade kicks in. */
    private static final float MAX_TAIL_LENGTH = 2.2f;
    /** Duration (seconds) after impact during which tails collapse into cloud. */
    private static final float IMPACT_COLLAPSE_DURATION = 0.14f;

    /**
     * Lazily initializes five projectiles arranged around the ball-to-target axis.
     * Gives each projectile a slight launch stagger, speed variance, and
     * tail lifetime variance for organic separation.
     */
    private void initProjectiles(Vec3 ballPos, Vec3 pokemonCenter) {
        if (projectilesInitialized) return;
        projectilesInitialized = true;

        Vec3 forward = pokemonCenter.subtract(ballPos);
        float dist = (float) forward.length();
        if (dist < 1e-4) return;
        forward = forward.scale(1.0 / dist);

        Vec3 reference = Math.abs(forward.y) > 0.9
                ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up    = right.cross(forward).normalize();

        float baseAngle = rng.nextFloat() * TAU;

        for (int i = 0; i < BEAM_COUNT; i++) {
            float angle = baseAngle + i * TAU / BEAM_COUNT;
            Vec3 radial = right.scale(Math.cos(angle))
                    .add(up.scale(Math.sin(angle)));

            float spreadRadius     = dist * 0.85f;
            float targetWrapRadius = dist * 0.25f;
            float contactRadius    = 0.15f;

            // Give the top beam (closest to up) a stronger arc
            float upDot = (float) radial.dot(new Vec3(0, 1, 0));
            if (upDot > 0.6f) {
                spreadRadius *= 1.25f;
            }

            Vec3 cp0 = ballPos;
            Vec3 cp1 = ballPos
                    .add(forward.scale(dist * 0.15))
                    .add(radial.scale(spreadRadius));
            Vec3 cp2 = pokemonCenter
                    .subtract(forward.scale(dist * 0.25))
                    .add(radial.scale(targetWrapRadius));
            Vec3 cp3 = pokemonCenter
                    .add(radial.scale(contactRadius));

            float launchDelay = -0.025f + rng.nextFloat() * 0.05f;
            float speedMult   = 0.97f + rng.nextFloat() * 0.06f;
            float tailLife    = 0.30f * (0.90f + rng.nextFloat() * 0.20f);

            projectiles.add(new SnagProjectile(cp0, cp1, cp2, cp3,
                    launchDelay, speedMult, tailLife));
        }
    }

    /**
     * Updates projectile trail emission and impact collapse for one frame.
     * Called from renderDensityPass so entity positions are available.
     */
    private void updateProjectiles() {
        for (SnagProjectile proj : projectiles) {
            float headT = proj.getHeadT(age);
            if (headT <= 0f) continue;

            // Impact detection
            if (headT >= 1.0f && !proj.impacted) {
                proj.impacted = true;
            }

            if (!proj.impacted) {
                // Emit trail nodes based on distance
                Vec3 headPos = cubicBezier(proj.p0, proj.p1, proj.p2, proj.p3, headT);
                Vec3 tangent = cubicBezierTangent(proj.p0, proj.p1, proj.p2, proj.p3, headT);

                while (proj.lastTrailPos.distanceTo(headPos) >= NODE_SPACING) {
                    Vec3 dir = headPos.subtract(proj.lastTrailPos).normalize();
                    Vec3 next = proj.lastTrailPos.add(dir.scale(NODE_SPACING));

                    proj.trail.addLast(new TrailNode(next, tangent, proj.baseTailLifetime));
                    proj.lastTrailPos = next;
                }
            } else if (convergencePos != null) {
                // Impact collapse: pull remaining trail nodes toward convergence
                float impactAge = age - (BEAM_START + proj.launchDelay
                        + (BEAM_END - BEAM_START) / proj.speedMultiplier);
                float collapseProgress = Mth.clamp(
                        impactAge / IMPACT_COLLAPSE_DURATION, 0f, 1f);

                for (TrailNode node : proj.trail) {
                    float pull = collapseProgress * lastDt * 6f;
                    node.position = node.position.lerp(convergencePos, pull);
                }
            }
        }
    }

    /**
     * Renders the density pass for this VFX instance: beam splats along five
     * cubic Bezier curves, plus spark/ring/mote splats — all rendered as
     * camera-facing billboards into the density FBO with additive blending.
     */
    private void renderDensityPass(Camera camera, Vec3 camPos, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity ballEnt    = mc.level.getEntity(ballId);
        Entity pokemonEnt = mc.level.getEntity(pokemonId);

        if (!(ballEnt instanceof EmptyPokeBallEntity ball)) return;
        if (!(pokemonEnt instanceof PokemonEntity pokemon)) return;

        Vec3 ballPos = new Vec3(
                Mth.lerp(partialTick, ball.xOld, ball.getX()),
                Mth.lerp(partialTick, ball.yOld, ball.getY()),
                Mth.lerp(partialTick, ball.zOld, ball.getZ())
        );

        Vec3 pokemonCenter = pokemon.getBoundingBox().getCenter()
                .add(0, pokemon.getBbHeight() * 0.05, 0);

        // Cloud position: starts at convergence, lerps back to ball
        Vec3 cloudPos = computeCloudPos(ballPos);

        // Set up density splat shader
        ShaderInstance densityShader = ModShaders.SNAG_BEAM_DENSITY;
        if (densityShader == null) return;

        // Set CameraPos uniform for world-space coordinate reconstruction
        com.mojang.blaze3d.shaders.Uniform uCamPos = densityShader.getUniform("CameraPos");
        if (uCamPos != null) uCamPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        // Set up RenderSystem state for density splat rendering
        com.mojang.blaze3d.systems.RenderSystem.setShader(() -> densityShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,
                net.minecraft.resources.ResourceLocation.parse("shadowedhearts:textures/vfx/soft_glow.png"));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();

        // Set ModelViewMat to the camera's view matrix (inverse rotation).
        // Vertex positions are camera-relative world-space offsets, so the view matrix
        // must transform them into view space before the perspective projection.
        org.joml.Matrix4f savedMV = new org.joml.Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        org.joml.Quaternionf viewRot = new org.joml.Quaternionf(camera.rotation()).conjugate();
        com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()
                .set(new org.joml.Matrix4f().rotation(viewRot));

        var tess = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        var buf = tess.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);

        PoseStack stack = new PoseStack();
        org.joml.Quaternionf camOrientation = camera.rotation();

        // ── Projectile beams ─────────────────────────────────────────────────
        initProjectiles(ballPos, pokemonCenter);
        updateProjectiles();
        renderProjectiles(camPos, camOrientation, stack, buf);

        // ── Cloud splats — three-component deforming energy cluster ──────────
        // 1. Dense core  2. Flowing shell  3. Trailing fragments
        if (age >= CLOUD_FORM_START && age < BALL_ABSORB_END) {
            float cloudFormation = remapClamped(age, CLOUD_FORM_START, CLOUD_FORM_END);
            float cloudReturn    = remapClamped(age, CLOUD_RETURN_START, CLOUD_RETURN_END);
            float ballIntake     = remapClamped(age, BALL_REACT_START, BALL_ABSORB_END);

            float cloudDensity = cloudFormation * (1f - ballIntake * ballIntake);

            // Stretch along flow direction as cloud accelerates toward ball
            float stretch = Mth.lerp(cloudReturn, 0.15f, 1.1f);
            float radius  = Mth.lerp(cloudReturn, 0.75f, 0.18f);

            // Flow direction basis for axial stretch
            Vec3 flowDir = ballPos.subtract(cloudPos);
            double fLen = flowDir.length();
            if (fLen < 1e-4) flowDir = new Vec3(0, 1, 0);
            else flowDir = flowDir.scale(1.0 / fLen);
            Vec3 refUp = Math.abs(flowDir.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 cRight = flowDir.cross(refUp);
            double crLen = cRight.length();
            if (crLen < 1e-7) cRight = new Vec3(1, 0, 0);
            else cRight = cRight.scale(1.0 / crLen);
            Vec3 cUp = cRight.cross(flowDir);
            double cuLen = cUp.length();
            if (cuLen < 1e-7) cUp = new Vec3(0, 1, 0);
            else cUp = cUp.scale(1.0 / cuLen);

            // Component 1: Dense core (5 large, tight splats)
            for (int i = 0; i < 5; i++) {
                float angle1 = (float) i / 5f * TAU + age * 2.0f;
                float jR = radius * 0.25f * (0.5f + 0.5f * (float) Math.sin(angle1 * 1.3f + age));
                Vec3 offset = cRight.scale(Math.cos(angle1) * jR)
                        .add(cUp.scale(Math.sin(angle1) * jR))
                        .add(flowDir.scale(Math.sin(angle1 * 0.5f) * jR * stretch * 0.3f));
                Vec3 splatPos = cloudPos.add(offset);
                float size = radius * 0.7f;
                float density = cloudDensity * 0.85f;
                emitDensitySplat(splatPos, camPos, camOrientation, stack, buf, density, size);
            }

            // Component 2: Flowing shell (8 medium splats with lag and wobble)
            for (int i = 0; i < 8; i++) {
                float angle1 = (float) i / 8f * TAU + age * 1.2f;
                float shellLag = 0.15f + 0.1f * (float) i / 8f;
                float localReturn = Mth.clamp((cloudReturn - shellLag) / (1f - shellLag), 0f, 1f);
                Vec3 laggedCloud = convergencePos != null
                        ? convergencePos.lerp(ballPos, easeInCubic(localReturn))
                        : cloudPos;
                float jR = radius * (0.6f + 0.4f * (float) Math.abs(Math.sin(angle1 * 1.618f)));
                float noiseX = (float) Math.cos(angle1 + age * 0.8f) * jR;
                float noiseY = (float) Math.sin(angle1 * 0.7f + age * 1.1f) * jR * 0.6f;
                float noiseForward = (float) Math.sin(angle1 * 0.5f + age * 0.6f) * jR * stretch;
                Vec3 offset = cRight.scale(noiseX).add(cUp.scale(noiseY)).add(flowDir.scale(noiseForward));
                Vec3 splatPos = laggedCloud.add(offset);
                float size = radius * (0.4f + 0.2f * (1f - (float) i / 8f));
                float density = cloudDensity * (0.5f - 0.15f * ((float) i / 8f));
                emitDensitySplat(splatPos, camPos, camOrientation, stack, buf, density, size);
            }

            // Component 3: Trailing fragments (10 small motes on delayed trajectories)
            for (int i = 0; i < 10; i++) {
                float fragDelay = 0.3f + 0.5f * (float) i / 10f;
                float localReturn = Mth.clamp((cloudReturn - fragDelay) / (1f - fragDelay), 0f, 1f);
                Vec3 fragBase = convergencePos != null
                        ? convergencePos.lerp(ballPos, easeInCubic(localReturn))
                        : cloudPos;
                float angle1 = (float) i / 10f * TAU + age * 1.8f + i * 0.618f;
                float fragR = radius * 1.2f * (1f - localReturn);
                Vec3 offset = cRight.scale(Math.cos(angle1) * fragR)
                        .add(cUp.scale(Math.sin(angle1) * fragR))
                        .add(flowDir.scale(-fragR * stretch * 0.5f)); // trail behind
                Vec3 splatPos = fragBase.add(offset);
                float size = radius * 0.25f * (1f - localReturn * 0.7f);
                float density = cloudDensity * 0.35f * (1f - localReturn * 0.5f);
                if (density > 0.01f) {
                    emitDensitySplat(splatPos, camPos, camOrientation, stack, buf, density, size);
                }
            }
        }

        // ── Spark splats ─────────────────────────────────────────────────────
        for (NeedleSpark s : sparks) {
            float alpha = s.alpha();
            if (alpha < 0.01f) continue;

            float size = s.length * 0.4f;
            float density = alpha * 0.6f;

            stack.pushPose();
            stack.translate(s.pos.x - camPos.x, s.pos.y - camPos.y, s.pos.z - camPos.z);
            stack.mulPose(camOrientation);
            stack.scale(size, size, 1f);
            org.joml.Matrix4f pose = stack.last().pose();
            buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            buf.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            buf.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            buf.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            stack.popPose();
        }

        // ── Ring splats ──────────────────────────────────────────────────────
        for (ShockRing ring : rings) {
            float alpha = ring.alpha();
            if (alpha < 0.01f) continue;

            float outerR = ring.radius();
            if (outerR < 0.01f) continue;

            // Render ring as a cluster of splats around the circumference
            int ringSamples = 16;
            for (int i = 0; i < ringSamples; i++) {
                float angle = (float) i / ringSamples * TAU;
                float splatR = outerR * 0.875f; // midpoint between inner and outer

                org.joml.Vector3f camRightJ = camera.getLeftVector();
                org.joml.Vector3f camUpJ    = camera.getUpVector();
                Vec3 camRight = new Vec3(-camRightJ.x(), -camRightJ.y(), -camRightJ.z());
                Vec3 camUpV   = new Vec3(camUpJ.x(), camUpJ.y(), camUpJ.z());

                Vec3 offset = camRight.scale(Math.cos(angle) * splatR)
                        .add(camUpV.scale(Math.sin(angle) * splatR));
                Vec3 splatPos = ring.origin.add(offset);

                float size = outerR * 0.3f;
                float density = alpha * 0.4f;

                stack.pushPose();
                stack.translate(splatPos.x - camPos.x, splatPos.y - camPos.y, splatPos.z - camPos.z);
                stack.mulPose(camOrientation);
                stack.scale(size, size, 1f);
                org.joml.Matrix4f pose = stack.last().pose();
                buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
                buf.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
                buf.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
                buf.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
                stack.popPose();
            }
        }

        // ── Mote splats ──────────────────────────────────────────────────────
        // Motes spiral toward the real ball position using a flow-direction
        // basis so the return stream looks stable from all viewing angles.
        Vec3 moteTarget = ballPos;

        Vec3 flowDir = ballPos.subtract(cloudPos);
        double flowLen = flowDir.length();
        if (flowLen < 1e-4) flowDir = new Vec3(0, 1, 0);
        else flowDir = flowDir.scale(1.0 / flowLen);
        Vec3 referenceUp = Math.abs(flowDir.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 cRight = flowDir.cross(referenceUp);
        double rLen = cRight.length();
        if (rLen < 1e-7) cRight = new Vec3(1, 0, 0);
        else cRight = cRight.scale(1.0 / rLen);
        Vec3 cUp = cRight.cross(flowDir);
        double uLen = cUp.length();
        if (uLen < 1e-7) cUp = new Vec3(0, 1, 0);
        else cUp = cUp.scale(1.0 / uLen);

        for (SuctionMote m : motes) {
            float alpha = m.alpha();
            if (alpha < 0.01f) continue;

            Vec3 motePos = m.computePos(moteTarget, cRight, cUp);

            float size = 0.12f * (1f - m.t() * 0.6f);
            float density = alpha * 0.5f;

            stack.pushPose();
            stack.translate(motePos.x - camPos.x, motePos.y - camPos.y, motePos.z - camPos.z);
            stack.mulPose(camOrientation);
            stack.scale(size, size, 1f);
            org.joml.Matrix4f pose = stack.last().pose();
            buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            buf.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            buf.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            buf.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
            stack.popPose();
        }

        // ── Flash burst splats (opening + convergence + intake) ─────────────
        if (age >= FLASH_START && age < FLASH_START + 0.12f) {
            float flashAlpha = 1f - (age - FLASH_START) / 0.12f;
            emitFlashSplat(ballPos, camPos, camOrientation, stack, buf,
                    flashAlpha * 1.5f, 0.8f);
        }
        if (age >= CONVERGE_TIME && age < CONVERGE_TIME + 0.10f) {
            float flashAlpha = 1f - (age - CONVERGE_TIME) / 0.10f;
            emitFlashSplat(pokemonCenter, camPos, camOrientation, stack, buf,
                    flashAlpha * 2.0f, 1.5f);
        }
        // Intake flash at ball — starts before cloud fully arrives
        if (age >= BALL_REACT_START && age < BALL_ABSORB_END) {
            float intakeAlpha = 1f - remapClamped(age, BALL_REACT_START, BALL_ABSORB_END);
            emitFlashSplat(ballPos, camPos, camOrientation, stack, buf,
                    intakeAlpha * 1.8f, 0.5f);
        }

        // Upload and draw
        com.mojang.blaze3d.vertex.MeshData mesh = buf.build();
        if (mesh != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        }

        // Restore state
        com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix().set(savedMV);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
    }

    /**
     * Emits a single large density splat at a position for flash bursts.
     */
    private static void emitFlashSplat(Vec3 worldPos, Vec3 camPos,
                                        org.joml.Quaternionf camOrientation,
                                        PoseStack stack,
                                        com.mojang.blaze3d.vertex.BufferBuilder buf,
                                        float density, float size) {
        stack.pushPose();
        stack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        stack.mulPose(camOrientation);
        stack.scale(size, size, 1f);
        org.joml.Matrix4f pose = stack.last().pose();
        buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        stack.popPose();
    }

    /**
     * Emits a single density splat billboard at the given world position.
     * Used by cloud components for consistent rendering.
     */
    private static void emitDensitySplat(Vec3 worldPos, Vec3 camPos,
                                          org.joml.Quaternionf camOrientation,
                                          PoseStack stack,
                                          com.mojang.blaze3d.vertex.BufferBuilder buf,
                                          float density, float size) {
        stack.pushPose();
        stack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        stack.mulPose(camOrientation);
        stack.scale(size, size, 1f);
        org.joml.Matrix4f pose = stack.last().pose();
        buf.addVertex(pose, -1f, -1f, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose,  1f, -1f, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose,  1f,  1f, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose, -1f,  1f, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        stack.popPose();
    }

    /**
     * Renders all five projectiles: concentric head splats + tangent-aligned
     * trail splats with age/distance-based fading.
     */
    private void renderProjectiles(Vec3 camPos,
                                    org.joml.Quaternionf camOrientation,
                                    PoseStack stack,
                                    com.mojang.blaze3d.vertex.BufferBuilder buf) {
        // Per-beam brightness variation for individual readability
        float[] beamBrightness = { 1.0f, 0.88f, 0.92f, 0.85f, 0.95f };

        for (int b = 0; b < projectiles.size(); b++) {
            SnagProjectile proj = projectiles.get(b);
            float headT = proj.getHeadT(age);
            if (headT <= 0f && proj.trail.isEmpty()) continue;

            float brightness = b < beamBrightness.length ? beamBrightness[b] : 0.9f;

            // ── Render projectile head (three concentric splats) ────────────
            if (headT > 0f && !proj.impacted) {
                Vec3 headPos = cubicBezier(proj.p0, proj.p1, proj.p2, proj.p3, headT);
                Vec3 tangent = cubicBezierTangent(proj.p0, proj.p1, proj.p2, proj.p3, headT);

                // White-hot core
                emitTangentAlignedSplat(headPos, tangent, camPos, stack, buf,
                        1.8f * brightness, 0.09f, 0.09f * 1.5f);
                // Warm energy body
                emitTangentAlignedSplat(headPos, tangent, camPos, stack, buf,
                        0.9f * brightness, 0.18f, 0.18f * 1.4f);
                // Soft density halo
                emitDensitySplat(headPos, camPos, camOrientation, stack, buf,
                        0.25f * brightness, 0.32f);
            }

            // ── Render trail nodes ──────────────────────────────────────────
            Vec3 headWorldPos = headT > 0f
                    ? cubicBezier(proj.p0, proj.p1, proj.p2, proj.p3, Math.min(headT, 1f))
                    : proj.p0;

            for (TrailNode node : proj.trail) {
                float life = node.life();
                if (life <= 0f) continue;

                // Fade from both age and distance behind head
                float ageFade = life * life;
                float distBehind = (float) node.position.distanceTo(headWorldPos);
                float distFade = 1f - Mth.clamp(distBehind / MAX_TAIL_LENGTH, 0f, 1f);
                float alpha = ageFade * distFade * brightness;

                if (alpha < 0.01f) continue;

                // Radius: larger near head, smaller toward tail end
                float radius = Mth.lerp(life, 0.025f, 0.14f);

                // Tangent-aligned elliptical splat (length ~3x width)
                float width  = radius;
                float length = radius * 3.0f;

                emitTangentAlignedSplat(node.position, node.tangent, camPos,
                        stack, buf, alpha * 0.65f, width, length);

                // Crossed micro-ribbon: second quad rotated 60° around tangent
                Vec3 tang = node.tangent.normalize();
                Vec3 perpA = computePerpendicular(tang);
                Vec3 perpB = perpA.scale(Math.cos(Math.toRadians(60)))
                        .add(tang.cross(perpA).normalize().scale(Math.sin(Math.toRadians(60))));
                // Use perpB as a modified "tangent" for the crossed quad
                emitTangentAlignedSplat(node.position, perpB, camPos,
                        stack, buf, alpha * 0.35f, width * 0.8f, length * 0.7f);
            }
        }
    }

    /**
     * Emits a tangent-aligned elliptical density splat. The quad is stretched
     * along the projected tangent direction so it reads as a streak from the
     * side and compresses to a circle viewed head-on.
     */
    private static void emitTangentAlignedSplat(Vec3 worldPos, Vec3 tangent, Vec3 camPos,
                                                 PoseStack stack,
                                                 com.mojang.blaze3d.vertex.BufferBuilder buf,
                                                 float density, float width, float length) {
        if (density < 0.005f) return;

        Vec3 toCamera = camPos.subtract(worldPos);
        double toCamLen = toCamera.length();
        if (toCamLen < 1e-6) return;
        toCamera = toCamera.scale(1.0 / toCamLen);

        Vec3 tang = tangent.normalize();
        Vec3 widthAxis = tang.cross(toCamera);
        if (widthAxis.lengthSqr() < 0.0001) {
            widthAxis = computePerpendicular(tang);
        }
        widthAxis = widthAxis.normalize();
        Vec3 lengthAxis = widthAxis.cross(toCamera).normalize();

        Vec3 lOff = lengthAxis.scale(length);
        Vec3 wOff = widthAxis.scale(width);
        Vec3 rel  = worldPos.subtract(camPos);

        float x0 = (float)(rel.x - lOff.x - wOff.x);
        float y0 = (float)(rel.y - lOff.y - wOff.y);
        float z0 = (float)(rel.z - lOff.z - wOff.z);
        float x1 = (float)(rel.x + lOff.x - wOff.x);
        float y1 = (float)(rel.y + lOff.y - wOff.y);
        float z1 = (float)(rel.z + lOff.z - wOff.z);
        float x2 = (float)(rel.x + lOff.x + wOff.x);
        float y2 = (float)(rel.y + lOff.y + wOff.y);
        float z2 = (float)(rel.z + lOff.z + wOff.z);
        float x3 = (float)(rel.x - lOff.x + wOff.x);
        float y3 = (float)(rel.y - lOff.y + wOff.y);
        float z3 = (float)(rel.z - lOff.z + wOff.z);

        stack.pushPose();
        org.joml.Matrix4f pose = stack.last().pose();
        buf.addVertex(pose, x0, y0, z0).setUv(0f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose, x1, y1, z1).setUv(1f, 1f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose, x2, y2, z2).setUv(1f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        buf.addVertex(pose, x3, y3, z3).setUv(0f, 0f).setColor(1f, 1f, 1f, density).setLight(0xF000F0);
        stack.popPose();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Flash / orb billboard rendering
    // ═══════════════════════════════════════════════════════════════════════════

    private static void renderBillboardFlash(Vec3 worldPos, Vec3 camPos, Camera camera,
                                              float alpha, float radius,
                                              MultiBufferSource.BufferSource buf) {
        if (ModShaders.BALL_ORB_GLOW == null) return;

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        ps.scale(radius, radius, radius);

        try { applyFlashUniforms(ModShaders.BALL_ORB_GLOW, alpha); } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.ballOrbGlow());
        emitBillboardQuad(vc, ps, alpha);
    }

    private static void renderCaptureOrb(Vec3 worldPos, Vec3 camPos, Camera camera,
                                          float alpha, float radius,
                                          MultiBufferSource.BufferSource buf) {
        // Try the new orb shell shader first; fall back to ball_glow
        boolean useOrbShell = ModShaders.SNAG_ORB != null;

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        ps.scale(radius, radius, radius);

        if (useOrbShell) {
            try { applyOrbShellUniforms(ModShaders.SNAG_ORB, alpha); } catch (Throwable ignored) {}
            VertexConsumer vc = buf.getBuffer(BallRenderTypes.orbShell());
            emitBillboardQuad(vc, ps, alpha);
        } else {
            if (ModShaders.BALL_ORB_GLOW == null) return;
            try { applyOrbUniforms(ModShaders.BALL_ORB_GLOW, alpha); } catch (Throwable ignored) {}
            VertexConsumer vc = buf.getBuffer(BallRenderTypes.ballOrbGlow());
            emitBillboardQuad(vc, ps, alpha);
        }
    }

    /**
     * Renders a camera-facing lens flare billboard using the snag_flare shader.
     * Falls back to the ball_glow starburst if the flare shader isn't loaded.
     */
    private static void renderLensFlare(Vec3 worldPos, Vec3 camPos, Camera camera,
                                         float alpha, float radius,
                                         MultiBufferSource.BufferSource buf) {
        if (ModShaders.SNAG_FLARE == null) {
            // Fallback: render a small starburst flash instead
            //renderBillboardFlash(worldPos, camPos, camera, alpha * 0.5f, radius * 0.6f, buf);
            return;
        }

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        ps.scale(radius, radius, radius);

        try { applyFlareUniforms(ModShaders.SNAG_FLARE, alpha); } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.flareAdditive());
        emitBillboardQuad(vc, ps, alpha);
    }

    private static void applyFlashUniforms(ShaderInstance sh, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level != null ? mc.level.getGameTime() : 0f;
        set1f(sh, "u_time",         time);
        set1f(sh, "u_orbIntensity", alpha * 2.0f);
        set1f(sh, "u_orbSoftness",  0.8f);
        set1f(sh, "u_starStrength", alpha);
        set1f(sh, "u_starSharpness",8.0f);
        set1f(sh, "u_starCount",    6.0f);
        set1f(sh, "u_glowMix",      1.0f);
        set3f(sh, "u_c1",           0.85f, 0.50f, 1.0f);
    }

    private static void applyOrbUniforms(ShaderInstance sh, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level != null ? mc.level.getGameTime() : 0f;
        set1f(sh, "u_time",         time);
        set1f(sh, "u_orbIntensity", alpha * 1.4f);
        set1f(sh, "u_orbSoftness",  1.0f);
        set1f(sh, "u_starStrength", alpha * 0.6f);
        set1f(sh, "u_starSharpness",20.0f);
        set1f(sh, "u_starCount",    4.0f);
        set1f(sh, "u_glowMix",      1.0f);
        set3f(sh, "u_c1",           0.80f, 0.45f, 1.0f);
        set3f(sh, "u_c2",           0.55f, 0.20f, 0.85f);
    }

    /** Uniforms for the snag_orb sphere shell shader. */
    private static void applyOrbShellUniforms(ShaderInstance sh, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level != null ? mc.level.getGameTime() : 0f;
        set1f(sh, "u_time",         time * 0.05f);
        set3f(sh, "u_orbTint",      0.70f, 0.35f, 0.90f);  // purple-magenta
        set1f(sh, "u_orbIntensity", alpha * 1.5f);
        set1f(sh, "u_shellInner",   0.70f);
        set1f(sh, "u_shellOuter",   0.90f);
        set1f(sh, "u_noiseScrollX", 0.08f);
        set1f(sh, "u_noiseScrollY", 0.03f);
        set1f(sh, "u_rimPower",     2.0f);
        set1f(sh, "u_crackleScale", 1.5f);
    }

    /** Uniforms for the snag_flare lens flare shader. */
    private static void applyFlareUniforms(ShaderInstance sh, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level != null ? mc.level.getGameTime() : 0f;
        set1f(sh, "u_time",            time * 0.05f);
        set1f(sh, "u_streakStrength",  alpha * 1.5f);
        set1f(sh, "u_streakSharpness", 12.0f);
        set1f(sh, "u_spikeCount",      3.0f);
        set1f(sh, "u_spikeStrength",   alpha * 0.8f);
        set1f(sh, "u_aspect",          1.0f);
        set3f(sh, "u_flareTint",       0.75f, 0.40f, 1.0f); // purple-magenta flare
    }

    /**
     * Renders camera-facing diffraction spikes centered on the absorption cloud
     * using the snag_flare shader with sharp, narrow spike configuration.
     */
    private static void renderDiffractionSpikes(Vec3 worldPos, Vec3 camPos, Camera camera,
                                                 float alpha, float radius, float rotAngle,
                                                 MultiBufferSource.BufferSource buf) {
        if (ModShaders.SNAG_FLARE == null) return;

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        ps.scale(radius, radius, radius);
        // Slowly rotate spikes for a shimmering look
        ps.mulPose(com.mojang.math.Axis.ZP.rotation(rotAngle));

        ShaderInstance sh = ModShaders.SNAG_FLARE;
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level != null ? mc.level.getGameTime() : 0f;
        try {
            set1f(sh, "u_time",            time * 0.03f);
            set1f(sh, "u_streakStrength",  alpha * 2.0f);   // bright spikes
            set1f(sh, "u_streakSharpness", 24.0f);           // very narrow/sharp
            set1f(sh, "u_spikeCount",      4.0f);            // 4-pointed star → 8 arms
            set1f(sh, "u_spikeStrength",   alpha * 1.2f);
            set1f(sh, "u_aspect",          1.0f);
            set3f(sh, "u_flareTint",       0.85f, 0.65f, 1.0f); // light purple-white
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.flareAdditive());
        emitBillboardQuad(vc, ps, alpha);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Geometry helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static void emitBillboardQuad(VertexConsumer vc, PoseStack ps, float alpha) {
        final int FULLBRIGHT = 0x00F000F0;
        var last = ps.last();
        var mat  = last.pose();
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);

        vc.addVertex(mat, -1f, -1f, 0f).setColor(255, 255, 255, a)
                .setUv(0f, 1f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(mat,  1f, -1f, 0f).setColor(255, 255, 255, a)
                .setUv(1f, 1f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(mat,  1f,  1f, 0f).setColor(255, 255, 255, a)
                .setUv(1f, 0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(mat, -1f,  1f, 0f).setColor(255, 255, 255, a)
                .setUv(0f, 0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Math helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Cubic Bezier (4 points) — used for the two-phase grab S-curve. */
    private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float u = 1f - t;
        return p0.scale(u * u * u)
                .add(p1.scale(3f * u * u * t))
                .add(p2.scale(3f * u * t * t))
                .add(p3.scale(t * t * t));
    }

    /** Tangent (first derivative) of a cubic Bezier at parameter t. */
    private static Vec3 cubicBezierTangent(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float u = 1f - t;
        // B'(t) = 3(1-t)^2(P1-P0) + 6(1-t)t(P2-P1) + 3t^2(P3-P2)
        Vec3 tangent = p1.subtract(p0).scale(3f * u * u)
                .add(p2.subtract(p1).scale(6f * u * t))
                .add(p3.subtract(p2).scale(3f * t * t));
        double len = tangent.length();
        return len < 1e-7 ? new Vec3(0, 1, 0) : tangent.scale(1.0 / len);
    }

    public static float projectileHeadT(float progress) {
        // Single S-curve: no velocity discontinuity
        return easeInOutCubic(progress);
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static Vec3 computePerpendicular(Vec3 v) {
        Vec3 candidate = (Math.abs(v.y) < 0.9) ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = v.cross(candidate);
        double len2 = right.lengthSqr();
        if (len2 < 1e-7) return new Vec3(1, 0, 0);
        return right.scale(1.0 / Math.sqrt(len2));
    }

    /**
     * Computes the current cloud center position. Before CLOUD_RETURN_START the cloud
     * sits at convergencePos; during the return window it lerps back to ballPos.
     */
    private Vec3 computeCloudPos(Vec3 ballPos) {
        if (convergencePos == null) return ballPos;
        if (age < CLOUD_RETURN_START) return convergencePos;
        if (age > CLOUD_RETURN_END) return ballPos;
        float t = (age - CLOUD_RETURN_START) / (CLOUD_RETURN_END - CLOUD_RETURN_START);
        return convergencePos.lerp(ballPos, easeInOutCubic(t));
    }

    /** Maps {@code x} from [{@code a}, {@code b}] to [0, 1], clamped. */
    private static float remapClamped(float x, float a, float b) {
        return Mth.clamp((x - a) / (b - a), 0f, 1f);
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void set1f(ShaderInstance sh, String name, float v) {
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set3f(ShaderInstance sh, String name, float x, float y, float z) {
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }
}
