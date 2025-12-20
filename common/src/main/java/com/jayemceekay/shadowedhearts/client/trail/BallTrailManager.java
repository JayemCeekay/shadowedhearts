package com.jayemceekay.shadowedhearts.client.trail;

import com.jayemceekay.shadowedhearts.client.render.BallRenderTypes;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Maintains short-lived trail ribbons for thrown Poké Balls (client-side only). */
public final class BallTrailManager {
    private static final Map<Integer, TrailRibbon> RIBBONS = new HashMap<>();

    private BallTrailManager() {}

    // Motion → strength parameters
    private static final float MIN_SPEED = 0.00f;      // blocks/sec below which we consider "stopped"
    private static final float MAX_SPEED = 0.50f;      // blocks/sec for full strength
    private static final float STRENGTH_LERP = 0.1f;  // smoothing toward target per tick
    private static final float EXTRA_DECAY_PER_TICK = 0.08f; // boosts age while stopped

    public static void addPoint(Entity entity, double x, double y, double z) {
        RIBBONS.computeIfAbsent(entity.getId(), id -> new TrailRibbon())
                .sample(new Vec3(x, y, z));
    }

    /**
     * Id-based variant for emitter-driven rendering where we don't have the Entity instance handy.
     */
    public static void addPointForId(int entityId, double x, double y, double z) {
        RIBBONS.computeIfAbsent(entityId, id -> new TrailRibbon())
                .sample(new Vec3(x, y, z));
    }

    public static void remove(Entity entity) {
        RIBBONS.remove(entity.getId());
    }

    public static void removeById(int entityId) {
        RIBBONS.remove(entityId);
    }

    public static void render(Entity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        TrailRibbon ribbon = RIBBONS.get(entity.getId());
        if (ribbon == null || ribbon.size() < 2) return;

        // Determine camera-facing right vector for billboarding
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f upJ = cam.getUpVector();
        Vec3 camUp = new Vec3(upJ.x(), upJ.y(), upJ.z());

        // Current render origin (poseStack is at entity origin)
        double ox = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double oy = Mth.lerp(partialTicks, entity.yo, entity.getY());
        double oz = Mth.lerp(partialTicks, entity.zo, entity.getZ());
        Vec3 origin = new Vec3(ox, oy, oz);

        // Upload palette uniforms each draw so hot-reloaded shaders pick up new values immediately (like AuraEmitters)
        uploadPaletteUniforms(partialTicks);
        // Upload dynamic strength per ribbon
        setStrengthUniform(ribbon.strength);

        VertexConsumer vc = buffer.getBuffer(BallRenderTypes.trailAdditive());

        // Trail width (half-width used below) and color. Start as wide as the ball, taper with age.
        float baseWidth = Math.max(0.01f, (float) (entity.getBbWidth() * 0.5));
        int n = ribbon.size();
        // Optional shortening while stopped: scale effective count by strength
        int effectiveN = Math.max(2, (int) Math.floor(n * (ribbon.stopped ? Math.max(0.05f, ribbon.strength) : 1.0f)));
        int start = n - effectiveN;
        for (int i = 0; i < effectiveN - 1; i++) {
            int i0 = start + i;
            Vec3 p1w = ribbon.get(i0);
            Vec3 p2w = ribbon.get(i0 + 1);
            Vec3 seg = p2w.subtract(p1w);
            if (seg.lengthSqr() < 1e-6) continue;
            Vec3 side = seg.cross(camUp);
            double len2 = side.lengthSqr();
            if (len2 < 1e-6) continue;
            side = side.scale(1.0 / Math.sqrt(len2));

            // Taper from head (newest) to tail (oldest).
            // Our deque stores oldest at index 0 and newest at index n-1, so compute age fraction accordingly.
            float ageFrac = (float) (effectiveN - 1 - i) / (float) (effectiveN - 1); // 0 near head, 1 at tail
            // When stopped, increase decay so it dies faster
            float decayBoost = 1.0f + ribbon.stoppedTicks * EXTRA_DECAY_PER_TICK;
            float ageEff = Mth.clamp(ageFrac * decayBoost, 0.0f, 1.0f);
            // Non-linear taper: chunky head, gentle tail
            float width = baseWidth * (0.55f + 0.45f * (float) Math.pow(1.0f - ageEff, 1.4f));
            // Scale width by motion strength
            width *= ribbon.strength;

            // Colors: purple tint with alpha falling off
            int r = 180;
            int g = 60;
            int b = 255;
            int aHead = 240;
            int aTail = 12;
            int a = (int) Mth.lerp(ageEff, aHead, aTail);
            a = (int) (a * ribbon.strength);

            // Quad corners in world space
            Vec3 sScaled = side.scale(width);
            Vec3 p1a = p1w.add(sScaled);
            Vec3 p1b = p1w.subtract(sScaled);
            Vec3 p2a = p2w.add(sScaled);
            Vec3 p2b = p2w.subtract(sScaled);

            // Convert to local (entity) space by subtracting current origin
            emitQuad(vc,
                    p1a.subtract(origin),
                    p1b.subtract(origin),
                    p2b.subtract(origin),
                    p2a.subtract(origin),
                    r, g, b, a,
                    // UVs: u = 1 at tail (oldest), 0 at head (newest). v = 0/1 across width
                    1.0f - ((float) i / (float) (effectiveN - 1)),
                    1.0f - ((float) (i + 1) / (float) (effectiveN - 1)),
                    poseStack);
        }

        // Second, thinner core streak pass using a dedicated texture for a bright core.
        // Re-upload in case the core pass binds a separate instance/state
        uploadPaletteUniforms(partialTicks);
        setStrengthUniform(ribbon.strength);
        VertexConsumer vcCore = buffer.getBuffer(BallRenderTypes.trailCoreAdditive());
        float coreScale = 0.45f; // slightly thicker hot core
        for (int i = 0; i < effectiveN - 1; i++) {
            int i0 = start + i;
            Vec3 p1w = ribbon.get(i0);
            Vec3 p2w = ribbon.get(i0 + 1);
            Vec3 seg = p2w.subtract(p1w);
            if (seg.lengthSqr() < 1e-6) continue;
            Vec3 side = seg.cross(camUp);
            double len2 = side.lengthSqr();
            if (len2 < 1e-6) continue;
            side = side.scale(1.0 / Math.sqrt(len2));

            float ageFrac = (float) (effectiveN - 1 - i) / (float) (effectiveN - 1);
            float decayBoost = 1.0f + ribbon.stoppedTicks * EXTRA_DECAY_PER_TICK;
            float ageEff = Mth.clamp(ageFrac * decayBoost, 0.0f, 1.0f);
            float width = baseWidth * coreScale * (0.55f + 0.45f * (float) Math.pow(1.0f - ageEff, 1.4f));
            width *= ribbon.strength;

            // Bright neutral core with sharper alpha falloff
            int r = 255;
            int g = 255;
            int b = 255;
            int aHead = 255;
            int aTail = 10;
            int a = (int) Mth.lerp(ageEff, aHead, aTail);
            a = (int) (a * ribbon.strength);

            Vec3 sScaled = side.scale(width);
            Vec3 p1a = p1w.add(sScaled);
            Vec3 p1b = p1w.subtract(sScaled);
            Vec3 p2a = p2w.add(sScaled);
            Vec3 p2b = p2w.subtract(sScaled);

            emitQuad(vcCore,
                    p1a.subtract(origin),
                    p1b.subtract(origin),
                    p2b.subtract(origin),
                    p2a.subtract(origin),
                    r, g, b, a,
                    1.0f - ((float) i / (float) (effectiveN - 1)),
                    1.0f - ((float) (i + 1) / (float) (effectiveN - 1)),
                    poseStack);
        }
    }

    /**
     * Id-based render which assumes the current poseStack has been translated to the entity origin already.
     * Uses a fixed base width suitable for Poké Balls.
     */
    public static void renderForId(int entityId, float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        TrailRibbon ribbon = RIBBONS.get(entityId);
        if (ribbon == null || ribbon.size() < 2) return;

        // Determine camera-facing right vector for billboarding
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f upJ = cam.getUpVector();
        Vec3 camUp = new Vec3(upJ.x(), upJ.y(), upJ.z());

        // We assume poseStack is at entity origin; use the last point as origin
        Vec3 last = ribbon.get(ribbon.size() - 1);
        Vec3 origin = last;

        uploadPaletteUniforms(partialTicks);
        setStrengthUniform(ribbon.strength);
        VertexConsumer vc = buffer.getBuffer(BallRenderTypes.trailAdditive());

        float baseWidth = 0.25f; // fixed width for ball trails
        int n = ribbon.size();
        int effectiveN = Math.max(2, n);
        int start = n - effectiveN;
        for (int i = 0; i < effectiveN - 1; i++) {
            int i0 = start + i;
            Vec3 p1w = ribbon.get(i0);
            Vec3 p2w = ribbon.get(i0 + 1);
            Vec3 seg = p2w.subtract(p1w);
            if (seg.lengthSqr() < 1e-6) continue;
            Vec3 side = seg.cross(camUp);
            double len2 = side.lengthSqr();
            if (len2 < 1e-6) continue;
            side = side.scale(1.0 / Math.sqrt(len2));

            float ageFrac = (float) (effectiveN - 1 - i) / (float) (effectiveN - 1);
            float decayBoost = 1.0f + ribbon.stoppedTicks * EXTRA_DECAY_PER_TICK;
            float ageEff = Mth.clamp(ageFrac * decayBoost, 0.0f, 1.0f);
            float width = baseWidth * (0.55f + 0.45f * (float) Math.pow(1.0f - ageEff, 1.4f));
            width *= ribbon.strength;

            int r = 180, g = 60, b = 255;
            int aHead = 240, aTail = 12;
            int a = (int) Mth.lerp(ageEff, aHead, aTail);
            a = (int) (a * ribbon.strength);

            Vec3 sScaled = side.scale(width);
            Vec3 p1a = p1w.add(sScaled);
            Vec3 p1b = p1w.subtract(sScaled);
            Vec3 p2a = p2w.add(sScaled);
            Vec3 p2b = p2w.subtract(sScaled);

            emitQuad(vc,
                    p1a.subtract(origin),
                    p1b.subtract(origin),
                    p2b.subtract(origin),
                    p2a.subtract(origin),
                    r, g, b, a,
                    1.0f - ((float) i / (float) (effectiveN - 1)),
                    1.0f - ((float) (i + 1) / (float) (effectiveN - 1)),
                    poseStack);
        }

        uploadPaletteUniforms(partialTicks);
        setStrengthUniform(ribbon.strength);
        VertexConsumer vcCore = buffer.getBuffer(BallRenderTypes.trailCoreAdditive());
        float coreScale = 0.45f;
        for (int i = 0; i < effectiveN - 1; i++) {
            int i0 = start + i;
            Vec3 p1w = ribbon.get(i0);
            Vec3 p2w = ribbon.get(i0 + 1);
            Vec3 seg = p2w.subtract(p1w);
            if (seg.lengthSqr() < 1e-6) continue;
            Vec3 side = seg.cross(camUp);
            double len2 = side.lengthSqr();
            if (len2 < 1e-6) continue;
            side = side.scale(1.0 / Math.sqrt(len2));

            float ageFrac = (float) (effectiveN - 1 - i) / (float) (effectiveN - 1);
            float decayBoost = 1.0f + ribbon.stoppedTicks * EXTRA_DECAY_PER_TICK;
            float ageEff = Mth.clamp(ageFrac * decayBoost, 0.0f, 1.0f);
            float width = baseWidth * coreScale * (0.55f + 0.45f * (float) Math.pow(1.0f - ageEff, 1.4f));
            width *= ribbon.strength;

            int r = 255, g = 255, b = 255;
            int aHead = 255, aTail = 10;
            int a = (int) Mth.lerp(ageEff, aHead, aTail);
            a = (int) (a * ribbon.strength);

            Vec3 sScaled = side.scale(width);
            Vec3 p1a = p1w.add(sScaled);
            Vec3 p1b = p1w.subtract(sScaled);
            Vec3 p2a = p2w.add(sScaled);
            Vec3 p2b = p2w.subtract(sScaled);

            emitQuad(vcCore,
                    p1a.subtract(origin),
                    p1b.subtract(origin),
                    p2b.subtract(origin),
                    p2a.subtract(origin),
                    r, g, b, a,
                    1.0f - ((float) i / (float) (effectiveN - 1)),
                    1.0f - ((float) (i + 1) / (float) (effectiveN - 1)),
                    poseStack);
        }
    }

    /**
     * Mirrors AuraEmitters' pattern: push tweakable uniforms every render so shader hot-reloads reflect changes.
     * Safe even if a different shader is bound; we guard by uniform presence.
     */
    private static void uploadPaletteUniforms(float partialTicks) {
        try {
            ShaderInstance sh = RenderSystem.getShader();
            if (sh == null) return;

            // Time-based hue phase available; we will use a gentle fixed phase + slow scroll
            Minecraft mc = Minecraft.getInstance();
            float t = 0f;
            if (mc != null) {
                if (mc.level != null) t = (mc.level.getGameTime() + partialTicks) * 0.05f;
                else if (mc.gui != null) t = (mc.gui.getGuiTicks() + partialTicks) * 0.05f;
            }

            // Freeze palette motion; trail uses a constant vertical gradient now
            set1f(sh, "u_paletteMix", 1.0f);
            float speed = 0.0f;
            set1f(sh, "u_paletteSpeed", speed);
            set1f(sh, "u_paletteShift", 0.5f);
            set1f(sh, "u_paletteSaturation", 1.0f);
            // Luminance coefficients for saturation control
            set3f(sh, "u_lumaCoeff", 0.299f, 0.587f, 0.114f);

            // Upload dynamic palette stops (defaults match shader JSON)
            set3f(sh, "u_c0", 1.00f, 1.00f, 1.00f);
            set3f(sh, "u_c1", 1.00f, 0.92f, 0.12f);
            set3f(sh, "u_c2", 1.00f, 0.68f, 0.10f);
            set3f(sh, "u_c3", 0.60f, 0.25f, 0.95f);
            set1f(sh, "u_t1", 0.30f);
            set1f(sh, "u_t2", 0.60f);
            set1f(sh, "u_t3", 1.00f);
        } catch (Throwable ignored) {
        }
    }

    private static void setStrengthUniform(float strength) {
        try {
            ShaderInstance sh = RenderSystem.getShader();
            if (sh == null) return;
            // Send a boosted strength to make the additive trail brighter overall.
            // Allow modest overbright but keep a sane upper bound.
            float boost = 2.5f; // global brightness boost for the trail
            float s = Math.max(0.0f, strength * boost);
            // Cap to prevent extreme overdraw; shader no longer clamps to 1.0
            set1f(sh, "uStrength", Math.min(s, 2.0f));
        } catch (Throwable ignored) {
        }
    }

    // Small uniform helper (same style as AuraEmitters)
    private static void set1f(ShaderInstance sh, String name, float v) {
        if (sh == null) return;
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set3f(ShaderInstance sh, String name, float x, float y, float z) {
        if (sh == null) return;
        Uniform u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private static void emitQuad(@NotNull VertexConsumer vc, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, int r, int g, int b, int a, float u1, float u2, PoseStack poseStack) {
        var last = poseStack.last();
        var mat = last.pose();
        // Normal matrix carried in Pose; this overload expects the Pose, not the raw Matrix3f
        // Vertex order matches quad winding above; assign v across width {0,1}, and u along trail
        vc.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z)
                .setColor(r, g, b, a)
                .setUv(u1, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0.0f, 1.0f, 0.0f);
        vc.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z)
                .setColor(r, g, b, a)
                .setUv(u1, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0.0f, 1.0f, 0.0f);
        vc.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z)
                .setColor(r, g, b, a)
                .setUv(u2, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0.0f, 1.0f, 0.0f);
        vc.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z)
                .setColor(r, g, b, a)
                .setUv(u2, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(last, 0.0f, 1.0f, 0.0f);
    }

    private static final class TrailRibbon {
        private static final int MAX_POINTS = 40;
        private final Deque<Vec3> points = new ArrayDeque<>(MAX_POINTS);
        private Vec3 lastPos = null;
        private float strength = 0.0f;
        private int stoppedTicks = 0;
        private boolean stopped = false;

        void sample(Vec3 p) {
            if (lastPos == null) {
                lastPos = p;
                points.addLast(p);
                return;
            }

            double dist = p.distanceTo(lastPos);
            // Approximate dt as 1/20s per tick sample
            float speed = (float) (dist * 20.0);
            float target = smoothstep(MIN_SPEED, MAX_SPEED, speed);

            if (speed < MIN_SPEED) {
                stopped = true;
                stoppedTicks++;
                strength = lerp(strength, 0.0f, STRENGTH_LERP);
                // do not append new sample; just age existing visually
            } else {
                stopped = false;
                stoppedTicks = 0;
                // Only append if sufficiently separated to avoid overdraw
                if (points.isEmpty() || points.getLast().distanceToSqr(p) >= 0.0004) {
                    points.addLast(p);
                    if (points.size() > MAX_POINTS) points.removeFirst();
                }
                strength = lerp(strength, target, STRENGTH_LERP);
            }

            lastPos = p;
        }

        int size() { return points.size(); }

        Vec3 get(int idx) {
            if (idx < 0 || idx >= points.size()) throw new IndexOutOfBoundsException();
            int i = 0;
            for (Vec3 p : points) {
                if (i++ == idx) return p;
            }
            throw new IndexOutOfBoundsException();
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Mth.clamp(t, 0.0f, 1.0f);
    }

    /**
     * Renders a continuous, camera-facing ring made of the same additive trail material, with UVs that scroll
     * along the ring. Useful for portal-like trims or capture rings around a ball.
     *
     * Context: This is a Minecraft Cobblemon mod; all “trail/capture/attack/target/combat” terms are gameplay mechanics.
     *
     * @param center       world-space center of the ring
     * @param normal       ring plane normal (does not need to be normalized)
     * @param radius       ring radius in blocks
     * @param thickness    visual thickness (width) of the ribbon in blocks
     * @param segments     number of segments around the circle (>= 3 recommended)
     * @param strength     brightness multiplier, typically in [0,1]; will be boosted/clamped in shader
     * @param scroll       UV scroll offset in [0,1) where 1.0 wraps around once; increase over time to animate
     * @param partialTicks render partial ticks
     * @param poseStack    current pose stack
     * @param buffer       render buffer source
     */
    public static void renderScrollingRing(
            Vec3 center,
            Vec3 normal,
            float radius,
            float thickness,
            int segments,
            float strength,
            float scroll,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {
        if (segments < 3 || radius <= 0f || thickness <= 0f) return;

        // We no longer billboard to camera; the ring orientation should be locked to its plane normal.
        Vector3f upVector = Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector();
        Vec3 camUp = new Vec3(upVector.x(), upVector.y(), upVector.z());

        // Orthonormal basis for the ring plane
        Vec3 n = normal == null ? new Vec3(0, 1, 0) : normal;
        if (n.lengthSqr() < 1.0e-6) n = new Vec3(0, 1, 0);
        n = n.normalize();
        // Choose any vector not parallel to n
        Vec3 ref = Math.abs(n.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tangentX = n.cross(ref);
        if (tangentX.lengthSqr() < 1.0e-6) tangentX = n.cross(new Vec3(1, 0, 0));
        tangentX = tangentX.normalize();
        Vec3 tangentY = n.cross(tangentX).normalize();

        // Upload shader params
        uploadPaletteUniforms(partialTicks);
        setStrengthUniform(Math.max(0.0f, strength));

        // Outer additive shell
        VertexConsumer vc = buffer.getBuffer(BallRenderTypes.trailAdditive());
        float halfW = thickness * 0.5f;

        // Localize to center for better precision
        Vec3 origin = center == null ? Vec3.ZERO : center;

        // Color/alpha across the ring (slight falloff to avoid harsh seams); constant along ring here
        int r = 180, g = 60, b = 255;
        int aHead = 240, aTail = 200; // fairly solid ring

        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            float t0 = (float) i / (float) segments;
            float t1 = (float) j / (float) segments;
            double ang0 = 2.0 * Math.PI * t0;
            double ang1 = 2.0 * Math.PI * t1;

            Vec3 p0 = origin
                    .add(tangentX.scale(Math.cos(ang0) * radius))
                    .add(tangentY.scale(Math.sin(ang0) * radius));
            Vec3 p1 = origin
                    .add(tangentX.scale(Math.cos(ang1) * radius))
                    .add(tangentY.scale(Math.sin(ang1) * radius));

            Vec3 seg = p1.subtract(p0);
            if (seg.lengthSqr() < 1e-8) continue;
            // Width direction locked to ring plane: use n × tangent so it stays in-plane and camera-independent
            Vec3 side = n.cross(camUp);
            double len2 = side.lengthSqr();
            if (len2 < 1e-10) continue;
            side = side.scale(1.0 / Math.sqrt(len2));

            Vec3 sScaled = side.scale(halfW);
            Vec3 p0a = p0.add(sScaled);
            Vec3 p0b = p0.subtract(sScaled);
            Vec3 p1a = p1.add(sScaled);
            Vec3 p1b = p1.subtract(sScaled);

            // Alpha constant, optionally bias toward head-like brightness
            float ageEff = 0.0f; // ring has no head/tail; keep near head value
            int a = (int) Mth.lerp(ageEff, aHead, aTail);
            a = (int) (a * Math.max(0.0f, Math.min(1.0f, strength)));

            // Scroll UVs along arc length using t0/t1 + scroll phase
            float u0 = t0 + scroll;
            float u1 = t1 + scroll;
            // Keep in 0..1 for nicer gradients when wrapping; shader can also repeat
            u0 = u0 - (float) Math.floor(u0);
            u1 = u1 - (float) Math.floor(u1);

            emitQuad(vc,
                    p0a.subtract(origin),
                    p0b.subtract(origin),
                    p1b.subtract(origin),
                    p1a.subtract(origin),
                    r, g, b, a,
                    u0, u1,
                    poseStack);
        }

        // Core highlight pass
        uploadPaletteUniforms(partialTicks);
        setStrengthUniform(Math.max(0.0f, strength));
        VertexConsumer vcCore = buffer.getBuffer(BallRenderTypes.trailCoreAdditive());
        float coreScale = 0.45f;
        float halfWCore = halfW * coreScale;
        int rC = 255, gC = 255, bC = 255;
        int aCHead = 255, aCTail = 180;

        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            float t0 = (float) i / (float) segments;
            float t1 = (float) j / (float) segments;
            double ang0 = 2.0 * Math.PI * t0;
            double ang1 = 2.0 * Math.PI * t1;

            Vec3 p0 = origin
                    .add(tangentX.scale(Math.cos(ang0) * radius))
                    .add(tangentY.scale(Math.sin(ang0) * radius));
            Vec3 p1 = origin
                    .add(tangentX.scale(Math.cos(ang1) * radius))
                    .add(tangentY.scale(Math.sin(ang1) * radius));

            Vec3 seg = p1.subtract(p0);
            if (seg.lengthSqr() < 1e-8) continue;
            // Core pass uses the same camera-independent side direction
            Vec3 side = n.cross(camUp);
            double len2 = side.lengthSqr();
            if (len2 < 1e-10) continue;
            side = side.scale(1.0 / Math.sqrt(len2));

            Vec3 sScaled = side.scale(halfWCore);
            Vec3 p0a = p0.add(sScaled);
            Vec3 p0b = p0.subtract(sScaled);
            Vec3 p1a = p1.add(sScaled);
            Vec3 p1b = p1.subtract(sScaled);

            float ageEff = 0.0f;
            int a = (int) Mth.lerp(ageEff, aCHead, aCTail);
            a = (int) (a * Math.max(0.0f, Math.min(1.0f, strength)));

            float u0 = t0 + scroll;
            float u1 = t1 + scroll;
            u0 = u0 - (float) Math.floor(u0);
            u1 = u1 - (float) Math.floor(u1);

            emitQuad(vcCore,
                    p0a.subtract(origin),
                    p0b.subtract(origin),
                    p1b.subtract(origin),
                    p1a.subtract(origin),
                    rC, gC, bC, a,
                    u0, u1,
                    poseStack);
        }
    }
}
