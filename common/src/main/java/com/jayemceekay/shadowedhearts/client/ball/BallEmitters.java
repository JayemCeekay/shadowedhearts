package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.BallRenderTypes;
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
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        ACTIVE.put(entity.getId(), new BallInstance(entity.getId(), entity, now, 4, 400, 8));
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

            // Render from entity-local pose
            PoseStack poseStack = new PoseStack();
            var camPos = camera.getPosition();
            poseStack.translate(ix  - camPos.x, iy + (ent.getBbHeight() / 8f) - camPos.y, iz - camPos.z);
            MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();

            // Render orb billboard at center
            renderOrb(poseStack, buf, partialTicks);

            // Render trail using existing manager (expects current pose at ball origin)
            BallTrailManager.renderForId(inst.entityId, partialTicks, poseStack, buf);

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
                apply(ModShaders.BALL_GLOW );
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

    private static final class BallInstance {
        final int entityId;
        final WeakReference<Entity> entityRef;
        long startTick;
        int fadeInTicks;
        int sustainTicks;
        int fadeOutTicks;

        BallInstance(int entityId, @Nullable Entity ent, long startTick, int fi, int sus, int fo) {
            this.entityId = entityId;
            this.entityRef = new WeakReference<>(ent);
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
        //System.out.println(shader.getUniform("GameTime").getFloatBuffer().get());
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
