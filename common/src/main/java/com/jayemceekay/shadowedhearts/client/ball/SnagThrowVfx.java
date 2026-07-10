package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.rendertypes.BallRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side VFX chain for the Snag Ball throw phase — the visual effects
 * that occur while the ball is travelling through the air after being thrown,
 * but before hitting a Pokémon to initiate a capture.
 *
 * <p>Modeled after the Pokémon Colosseum (GameCube) snag ball throw animation:
 * <ul>
 *   <li>Soft orange glow around the ball</li>
 *   <li>4 cardinal (up/down/left/right) diffraction spikes</li>
 *   <li>4 softer diagonal diffraction spikes</li>
 * </ul>
 *
 * <p>Trail effects (orange smoke puffs and purple motes) are handled by
 * {@link SnagTrailDensitySystem} via {@link BallEmitters}.
 */
public final class SnagThrowVfx {

    private static final int FULLBRIGHT = 0x00F000F0;

    // ── Global instance registry (by ball entity ID) ────────────────────────
    private static final Map<Integer, SnagThrowVfx> ACTIVE = new ConcurrentHashMap<>();

    // ── Instance state ──────────────────────────────────────────────────────
    private final int ballId;
    private final WeakReference<Entity> ballRef;

    private SnagThrowVfx(EmptyPokeBallEntity ball) {
        this.ballId = ball.getId();
        this.ballRef = new WeakReference<>(ball);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Start tracking a snag ball for throw VFX. Idempotent. */
    public static void start(EmptyPokeBallEntity ball) {
        ACTIVE.putIfAbsent(ball.getId(), new SnagThrowVfx(ball));
    }

    /** Stop throw VFX for a ball (e.g. when capture begins or ball despawns). */
    public static void stop(int ballId) {
        ACTIVE.remove(ballId);
    }

    /** Returns the active throw VFX for a ball, or null. */
    public static SnagThrowVfx get(int ballId) {
        return ACTIVE.get(ballId);
    }

    /** Returns true if any throw VFX are active. */
    public static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    // ── Render ──────────────────────────────────────────────────────────────

    /**
     * Renders all active throw VFX instances. Called from {@link BallEmitters#onRender}.
     */
    public static void renderAll(Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        for (SnagThrowVfx vfx : ACTIVE.values()) {
            vfx.render(camera, camPos, partialTick, buf);
        }

        buf.endBatch();
    }

    /**
     * Prune expired instances (ball no longer alive or in capture state).
     * Called from {@link BallEmitters#onRender} each frame.
     */
    public static void pruneAll() {
        ACTIVE.entrySet().removeIf(entry -> {
            SnagThrowVfx vfx = entry.getValue();
            Entity ent = vfx.ballRef.get();
            if (ent == null || !ent.isAlive()) return true;
            if (ent instanceof EmptyPokeBallEntity ball) {
                // Stop once capture begins (capture VFX takes over)
                if (!ball.getCaptureState().equals(EmptyPokeBallEntity.CaptureState.NOT)) {
                    return true;
                }
                // Stop once ball has slowed down (hit something)
                if (ball.getDeltaMovement().length() < 0.91f) {
                    return true;
                }
            }
            return false;
        });
    }

    private void render(Camera camera, Vec3 camPos, float partialTick,
                        MultiBufferSource.BufferSource buf) {
        Entity ent = ballRef.get();
        if (ent == null || !ent.isAlive()) return;

        // Interpolated world position
        Vec3 worldPos = new Vec3(
                Mth.lerp(partialTick, ent.xOld, ent.getX()),
                Mth.lerp(partialTick, ent.yOld, ent.getY()) + (ent.getBbHeight() / 8f),
                Mth.lerp(partialTick, ent.zOld, ent.getZ())
        );

        Minecraft mc = Minecraft.getInstance();
        float time = mc.level != null ? mc.level.getGameTime() + partialTick : 0f;

        // ── Layer 1: Soft orange glow around the ball ───────────────────────
        //renderOrangeGlow(worldPos, camPos, camera, time, buf);

        // ── Layer 2: 4 cardinal diffraction spikes (up/down/left/right) ─────
        renderCardinalSpikes(worldPos, camPos, camera, time, buf);

        // ── Layer 3: 4 softer diagonal diffraction spikes ───────────────────
        renderDiagonalSpikes(worldPos, camPos, camera, time, buf);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layer 1: Soft orange glow
    // ═════════════════════════════════════════════════════════════════════════

    private static void renderOrangeGlow(Vec3 worldPos, Vec3 camPos, Camera camera,
                                          float time, MultiBufferSource.BufferSource buf) {
        if (ModShaders.BALL_ORB_GLOW == null) return;

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        float radius = 0.55f;
        ps.scale(radius, radius, radius);

        ShaderInstance sh = ModShaders.BALL_ORB_GLOW;
        float alpha = 0.65f;
        try {
            set1f(sh, "u_time",         time);
            set1f(sh, "u_orbIntensity", alpha * 1.6f);
            set1f(sh, "u_orbSoftness",  1.2f);
            set1f(sh, "u_starStrength", 0.0f);          // no star pattern in glow layer
            set1f(sh, "u_starSharpness",0.0f);
            set1f(sh, "u_starCount",    0.0f);
            set1f(sh, "u_glowMix",      1.0f);
            // Soft warm orange tint
            set3f(sh, "u_c1",           1.0f, 0.55f, 0.12f);
            set3f(sh, "u_c2",           1.0f, 0.35f, 0.05f);
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.ballOrbGlow());
        emitBillboardQuad(vc, ps, alpha);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layer 2: Cardinal diffraction spikes (4-pointed, up/down/left/right)
    // ═════════════════════════════════════════════════════════════════════════

    private static void renderCardinalSpikes(Vec3 worldPos, Vec3 camPos, Camera camera,
                                              float time, MultiBufferSource.BufferSource buf) {
        if (ModShaders.SNAG_FLARE == null) return;

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        float radius = 0.7f;
        ps.scale(radius, radius, radius);

        ShaderInstance sh = ModShaders.SNAG_FLARE;
        float alpha = 0.75f;
        try {
            set1f(sh, "u_time",            time * 0.02f);
            set1f(sh, "u_streakStrength",  alpha * 2.2f);   // bright, prominent spikes
            set1f(sh, "u_streakSharpness", 28.0f);           // very narrow/sharp
            set1f(sh, "u_spikeCount",      2.0f);            // 2 spike pairs → 4 arms (UDLR)
            set1f(sh, "u_spikeStrength",   alpha * 1.4f);
            set1f(sh, "u_aspect",          1.0f);
            // Warm orange-white tint for cardinal spikes
            set3f(sh, "u_flareTint",       1.0f, 0.65f, 0.20f);
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.flareAdditive());
        emitBillboardQuad(vc, ps, alpha);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Layer 3: Diagonal diffraction spikes (4-pointed, 45° rotated, softer)
    // ═════════════════════════════════════════════════════════════════════════

    private static void renderDiagonalSpikes(Vec3 worldPos, Vec3 camPos, Camera camera,
                                              float time, MultiBufferSource.BufferSource buf) {
        if (ModShaders.SNAG_FLARE == null) return;

        PoseStack ps = new PoseStack();
        ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        ps.mulPose(camera.rotation());
        float radius = 0.55f;  // slightly smaller than cardinal
        ps.scale(radius, radius, radius);
        // Rotate 45° to place spikes on diagonals
        ps.mulPose(com.mojang.math.Axis.ZP.rotation((float) (Math.PI / 4.0)));

        ShaderInstance sh = ModShaders.SNAG_FLARE;
        float alpha = 0.45f;  // softer than cardinal spikes
        try {
            set1f(sh, "u_time",            time * 0.025f);
            set1f(sh, "u_streakStrength",  alpha * 1.6f);    // softer streaks
            set1f(sh, "u_streakSharpness", 22.0f);            // slightly less sharp
            set1f(sh, "u_spikeCount",      2.0f);             // 2 spike pairs → 4 arms (diagonals)
            set1f(sh, "u_spikeStrength",   alpha * 0.9f);
            set1f(sh, "u_aspect",          1.0f);
            // Slightly warmer/dimmer orange tint for diagonal spikes
            set3f(sh, "u_flareTint",       0.95f, 0.55f, 0.15f);
        } catch (Throwable ignored) {}

        VertexConsumer vc = buf.getBuffer(BallRenderTypes.flareAdditive());
        emitBillboardQuad(vc, ps, alpha);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Geometry helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static void emitBillboardQuad(VertexConsumer vc, PoseStack ps, float alpha) {
        var last = ps.last();
        var mat  = last.pose();
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);

        vc.addVertex(mat, -1f, -1f, 0f).setColor(255, 255, 255, a)
                .setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(mat,  1f, -1f, 0f).setColor(255, 255, 255, a)
                .setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(mat,  1f,  1f, 0f).setColor(255, 255, 255, a)
                .setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
        vc.addVertex(mat, -1f,  1f, 0f).setColor(255, 255, 255, a)
                .setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT).setNormal(last, 0f, 0f, 1f);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Shader uniform helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static void set1f(ShaderInstance sh, String name, float v) {
        var u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set3f(ShaderInstance sh, String name, float x, float y, float z) {
        var u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }
}
