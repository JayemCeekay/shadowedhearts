package com.jayemceekay.shadowedhearts.client.ball;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallCaptureTimings;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Short world-space electricity after the real ball lid has completed closing. */
public final class DarkBallAnimeBallFinish {
    private static final int MAX_ACTIVE = 64;
    private static final Map<Integer, Instance> ACTIVE = new LinkedHashMap<>();
    private static VertexBuffer geometry;

    private DarkBallAnimeBallFinish() {
    }

    public static void arm(EmptyPokeBallEntity ball) {
        Instance previous = ACTIVE.get(ball.getId());
        if (previous != null && previous.entity.get() == ball) return;
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(ACTIVE.keySet().iterator().next());
        ACTIVE.put(ball.getId(), new Instance(new WeakReference<>(ball),
                new DarkBallAnimeBallFinishPlan(ball.tickCount,
                        DarkBallCaptureTimings.VFX_DURATION_SECONDS), ball.getUUID().hashCode()));
    }

    public static void stop(int ballId) {
        ACTIVE.remove(ballId);
    }

    public static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
        VertexBuffer old = geometry;
        geometry = null;
        if (old != null) {
            if (RenderSystem.isOnRenderThread()) old.close();
            else RenderSystem.recordRenderCall(old::close);
        }
    }

    /** Call once in the same restored world target/matched transform as Dark Ball compositing. */
    public static void updateAndRender(Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        ACTIVE.values().removeIf(instance -> {
            EmptyPokeBallEntity ball = instance.entity.get();
            if (ball == null || !ball.isAlive() || ball.level() != minecraft.level) return true;
            var state = ball.getCaptureState();
            boolean captureActive = state != EmptyPokeBallEntity.CaptureState.NOT
                    && state != EmptyPokeBallEntity.CaptureState.BROKEN_FREE;
            if (!captureActive) return true;
            if (!minecraft.isPaused()) {
                String pose = ball.getDelegate() instanceof PosableState posable
                        ? posable.getCurrentPose() : null;
                instance.plan.observe(ball.tickCount + Mth.clamp(partialTick, 0, 1),
                        "open".equals(pose), "shut".equals(pose), true);
            }
            return instance.plan.expired();
        });
        if (ACTIVE.isEmpty() || camera == null) return;
        boolean visible = ACTIVE.values().stream().anyMatch(i -> i.plan.ageSeconds() >= 0);
        if (!visible || GameRenderer.getPositionColorShader() == null) return;

        try (var ignored = DensityFboPipeline.preserveRenderState();
             ByteBufferBuilder storage = new ByteBufferBuilder(32768)) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR);
            Vec3 cameraPosition = camera.getPosition();
            Vec3 cameraView = new Vec3(camera.getLookVector().x, camera.getLookVector().y,
                    camera.getLookVector().z);
            for (Instance instance : ACTIVE.values()) {
                EmptyPokeBallEntity ball = instance.entity.get();
                if (ball == null) continue;
                float age = instance.plan.ageSeconds();
                if (age < 0) continue;
                Vec3 center = new Vec3(Mth.lerp(partialTick, ball.xOld, ball.getX()),
                        Mth.lerp(partialTick, ball.yOld, ball.getY()) + ball.getBbHeight() * .5,
                        Mth.lerp(partialTick, ball.zOld, ball.getZ())).subtract(cameraPosition);
                Vec3 view = center.lengthSqr() > 1.0e-12 ? center.normalize() : cameraView;
                double radius = Math.max(.08, Math.min(.5, ball.getBbWidth() * .64));
                float strength = DarkBallAnimeBallFinishPlan.arcStrength(age);
                for (int arc = 0; arc < DarkBallAnimeLightning.ARC_COUNT && strength > .001f; arc++) {
                    float strikeStrength = strength * DarkBallAnimeLightning.strikeStrength(age, arc);
                    if (strikeStrength <= .001f) continue;
                    DarkBallAnimeLightning.Bolt bolt = instance.bolt(arc, age, radius);
                    lightningStroke(builder, center, bolt.trunk(), view, radius, strikeStrength, false);
                    for (List<Vec3> branch : bolt.branches()) {
                        lightningStroke(builder, center, branch, view, radius * .63,
                                strikeStrength * .72f, true);
                    }
                }
                float pulse = DarkBallAnimeBallFinishPlan.closePulse(age)
                        * (1 - Mth.clamp(age / .1f, 0, 1));
                if (pulse > .001f) {
                    // A compact star at closure; a large expanding ring reads as a halo,
                    // overpowering the first short electrical discharge.
                    Vec3 right = fallbackSide(view);
                    Vec3 up = view.cross(right).normalize();
                    Vec3 flashCenter = view.scale(-radius * .95);
                    double reach = radius * (.24 + pulse * .10);
                    polyline(builder, center, List.of(flashCenter.subtract(right.scale(reach)),
                                    flashCenter, flashCenter.add(right.scale(reach))), view,
                            radius * .021, 239, 219, 255, pulse * .42f, false);
                    polyline(builder, center, List.of(flashCenter.subtract(up.scale(reach * .72)),
                                    flashCenter, flashCenter.add(up.scale(reach * .72))), view,
                            radius * .021, 239, 219, 255, pulse * .42f, false);
                }
            }
            MeshData mesh = builder.build();
            if (mesh == null) return;
            if (geometry == null || geometry.isInvalid()) geometry = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            geometry.bind();
            geometry.upload(mesh);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            geometry.drawWithShader(DarkBallRenderContext.modelView(camera),
                    DarkBallRenderContext.projection(), GameRenderer.getPositionColorShader());
        }
    }

    private static void lightningStroke(BufferBuilder builder, Vec3 center, List<Vec3> points,
                                        Vec3 view, double radius, float strength, boolean branch) {
        polyline(builder, center, points, view, radius * .057,
                126, 45, 238, strength * .34f, branch);
        polyline(builder, center, points, view, radius * .017,
                242, 222, 255, strength, branch);
    }

    private static void polyline(BufferBuilder builder, Vec3 center, List<Vec3> points, Vec3 view,
                                 double halfWidth, int r, int g, int blue, float strength,
                                 boolean branch) {
        if (points.size() < 2 || strength <= .001f) return;
        Vec3[] offsets = ribbonOffsets(points, view, halfWidth, branch);
        int alpha = Mth.clamp((int) (strength * 255), 0, 255);
        for (int segment = 0; segment + 1 < points.size(); segment++) {
            Vec3 a = center.add(points.get(segment));
            Vec3 b = center.add(points.get(segment + 1));
            if (a.distanceToSqr(b) < 1.0e-16) continue;
            vertex(builder, a.subtract(offsets[segment]), r, g, blue, alpha);
            vertex(builder, b.subtract(offsets[segment + 1]), r, g, blue, alpha);
            vertex(builder, b.add(offsets[segment + 1]), r, g, blue, alpha);
            vertex(builder, a.add(offsets[segment]), r, g, blue, alpha);
        }
    }

    /** Shared joins prevent cracks at sharp elbows; the miter cannot exceed twice the half-width. */
    static Vec3[] ribbonOffsets(List<Vec3> points, Vec3 view, double halfWidth, boolean branch) {
        int count = points.size();
        Vec3[] offsets = new Vec3[count];
        if (count < 2) {
            Arrays.fill(offsets, Vec3.ZERO);
            return offsets;
        }
        Vec3[] sides = new Vec3[count - 1];
        Vec3 lastValid = null;
        for (int segment = 0; segment < sides.length; segment++) {
            Vec3 direction = points.get(segment + 1).subtract(points.get(segment));
            Vec3 side = direction.cross(view);
            if (side.lengthSqr() > 1.0e-12) lastValid = side.normalize();
            sides[segment] = lastValid;
        }
        // A view-aligned segment borrows an adjacent direction. A fully end-on
        // bolt remains bounded and naturally collapses in projection.
        if (lastValid == null) lastValid = fallbackSide(view);
        for (int segment = sides.length - 1; segment >= 0; segment--) {
            if (sides[segment] == null) sides[segment] = lastValid;
            else lastValid = sides[segment];
        }
        for (int point = 0; point < count; point++) {
            Vec3 incoming = sides[Math.max(0, point - 1)];
            Vec3 outgoing = sides[Math.min(sides.length - 1, point)];
            Vec3 miter = incoming.add(outgoing);
            double extension = 1;
            if (miter.lengthSqr() > 1.0e-12) {
                miter = miter.normalize();
                extension = Math.min(2, 1 / Math.max(.5, Math.abs(miter.dot(outgoing))));
            } else {
                miter = outgoing;
            }
            double progress = point / (double) (count - 1);
            double taper = branch ? Math.pow(1 - progress, .7)
                    : Math.sqrt(Math.max(0, Math.sin(Math.PI * progress)));
            if (point == count - 1 || (!branch && point == 0)) taper = 0;
            offsets[point] = miter.scale(halfWidth * taper * extension);
        }
        return offsets;
    }

    private static Vec3 fallbackSide(Vec3 view) {
        Vec3 side = view.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-12) side = view.cross(new Vec3(1, 0, 0));
        return side.lengthSqr() > 1.0e-12 ? side.normalize() : new Vec3(1, 0, 0);
    }

    private static void vertex(BufferBuilder builder, Vec3 p, int r, int g, int b, int alpha) {
        builder.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, alpha);
    }

    private static final class Instance {
        private final WeakReference<EmptyPokeBallEntity> entity;
        private final DarkBallAnimeBallFinishPlan plan;
        private final int seed;
        private final int[] strikes = new int[DarkBallAnimeLightning.ARC_COUNT];
        private final DarkBallAnimeLightning.Bolt[] bolts =
                new DarkBallAnimeLightning.Bolt[DarkBallAnimeLightning.ARC_COUNT];
        private double boltRadius = Double.NaN;

        private Instance(WeakReference<EmptyPokeBallEntity> entity,
                         DarkBallAnimeBallFinishPlan plan, int seed) {
            this.entity = entity;
            this.plan = plan;
            this.seed = seed;
            Arrays.fill(strikes, Integer.MIN_VALUE);
        }

        private DarkBallAnimeLightning.Bolt bolt(int arc, float age, double radius) {
            if (Double.compare(radius, boltRadius) != 0) {
                Arrays.fill(bolts, null);
                Arrays.fill(strikes, Integer.MIN_VALUE);
                boltRadius = radius;
            }
            int strike = DarkBallAnimeLightning.strikeIndex(age, arc);
            if (bolts[arc] == null || strikes[arc] != strike) {
                bolts[arc] = DarkBallAnimeLightning.create(seed, arc, strike, radius);
                strikes[arc] = strike;
            }
            return bolts[arc];
        }
    }
}
