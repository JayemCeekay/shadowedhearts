package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import com.jayemceekay.shadowedhearts.client.render.geom.CylinderBuffers;
import com.jayemceekay.shadowedhearts.network.payload.AuraLifecycleS2C;
import com.jayemceekay.shadowedhearts.network.payload.AuraStateS2C;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuraEmitter system that attaches an aura instance to a Pokémon when it is sent out.
 * Rendering is driven by this system, not by the Pokémon's own renderer.
 */
public final class AuraEmitters {
    public static MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    private AuraEmitters() {
    }

    private static final Map<Integer, AuraInstance> ACTIVE = new ConcurrentHashMap<>();

    // Inertial tail smoothing constants/state (per-frame shared timer)
    private static final double TAU_SECONDS = 0.50; // response time for velocity lag (trail inertia)
    private static final double VIS_TAU_SECONDS = 0.50; // short visual smoothing for final visuals

    // Visual safety cap: maximum velocity magnitude considered by the aura visuals (blocks/tick)
    // This prevents extreme spikes from causing distracting flashes. Consider moving to config if needed.
    private static final float MAX_SPEED = 0.1f;

    // Envelope follower for start/stop behavior: separate attack/release to avoid pops when stopping/starting
    private static final double ENV_ATTACK_TAU_SECONDS = 0.22; // how fast visuals ramp up when starting to move
    private static final double ENV_RELEASE_TAU_SECONDS = 0.85; // how slow visuals decay when stopping
    private static final double DIR_TAU_SECONDS = 0.35; // direction smoothing for velocity vector
    private static final float DEAD_SPEED = 0.02f; // deadband to ignore micro jitter from physics
    private static final float HYSTERESIS = 0.01f; // avoids rapid flip between attack/release near threshold
    private static long LAST_FRAME_NANOS = 0L;

    /**
     * Called by a networking handler when a state update arrives.
     */
    public static void receiveState(AuraStateS2C pkt) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        AuraInstance inst = ACTIVE.getOrDefault(pkt.entityId(), null);
        if (inst == null) {
            return;
        }
        // ID reuse guard: ensure the entity UUID matches the instance's UUID; otherwise, ignore this state
        Entity cur = mc.level.getEntity(pkt.entityId());
        java.util.UUID curUuid = (cur != null) ? cur.getUUID() : null;
        if (inst.entityUuid != null && curUuid != null && !inst.entityUuid.equals(curUuid)) {
            return;
        }
        // Enforce ordering by server tick; ignore stale or duplicate packets
        if (pkt.serverTick() <= inst.lastServerTick) {
            return;
        }
        inst.lastServerTick = pkt.serverTick();
        // Update server-authoritative transform and bounding box
        // Teleport/jump snap: if movement between last and new is too large, snap to new to avoid long lerps
        double ddx = pkt.x() - inst.x;
        double ddy = pkt.y() - inst.y;
        double ddz = pkt.z() - inst.z;
        double dist2 = ddx * ddx + ddy * ddy + ddz * ddz;
        if (dist2 > TELEPORT_SNAP_DIST2) {
            inst.lastX = pkt.x();
            inst.lastY = pkt.y();
            inst.lastZ = pkt.z();
        } else {
            inst.lastX = inst.x;
            inst.lastY = inst.y;
            inst.lastZ = inst.z;
        }
        inst.x = pkt.x();
        inst.y = pkt.y();
        inst.z = pkt.z();
        inst.lastDeltaX = pkt.dx();
        inst.lastDeltaY = pkt.dy();
        inst.lastDeltaZ = pkt.dz();
        // Smooth bbox and corruption by tracking previous values for interpolation
        inst.prevBbW = inst.lastBbW;
        inst.prevBbH = inst.lastBbH;
        inst.prevBbSize = inst.lastBbSize;
        inst.prevCorruption = inst.lastCorruption;
        inst.lastBbW = pkt.bbw();
        inst.lastBbH = pkt.bbh();
        inst.lastBbSize = pkt.bbs();
        inst.lastCorruption = pkt.corruption();
    }

    /**
     * Called by networking handler when a lifecycle update arrives.
     */
    public static void receiveLifecycle(AuraLifecycleS2C pkt) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (pkt.action()) {
            case START -> {
                // Replace any existing instance for this entity ID (handles rapid entity ID reuse on recall/swap)
                Entity ent = mc.level.getEntity(pkt.entityId());
                UUID newUuid = (ent != null) ? ent.getUUID() : null;
                System.out.println(ent.getStringUUID());
                if (newUuid != null) {
                    for (Map.Entry<Integer, AuraInstance> e : ACTIVE.entrySet()) {
                        AuraInstance ai = e.getValue();
                        if (ai != null && newUuid.equals(ai.entityUuid) && e.getKey() != pkt.entityId()) {
                            ACTIVE.remove(e.getKey());
                        }
                    }
                }
                ACTIVE.put(pkt.entityId(), new AuraInstance(pkt.entityId(), ent, now, FADE_IN, SUSTAIN, FADE_OUT, pkt.x(), pkt.y(), pkt.z(), pkt.dx(), pkt.dy(), pkt.dz(), pkt.bbw(), pkt.bbh(), pkt.bbs(), pkt.corruption()));
            }
            case FADE_OUT -> {
                ACTIVE.computeIfPresent(pkt.entityId(), (id, inst) -> {
                    inst.beginImmediateFadeOut(now, Math.max(1, pkt.outTicks()));
                    return inst;
                });
            }
            default -> {
            }
        }
    }


    // Timings (ticks): ~0.25s fade-in, ~5s sustain, ~0.5s fade-out
    private static final int FADE_IN = 10;
    private static final int SUSTAIN = 6800; // can be tuned; original recent value ~90-100
    private static final int FADE_OUT = 10;
    // If a position update jumps farther than this squared distance, snap to avoid long lerp streaks
    private static final double TELEPORT_SNAP_DIST2 = 36.0; // 6 blocks squared

    public static void init() {
        // Server is authoritative for aura lifecycle now. Client no longer subscribes to Cobblemon send/recall events.
    }

    public static void onPokemonDespawn(int entityId) {
        // Start a quick fade-out if we still have an instance; if missing, nothing to do
        var mc = Minecraft.getInstance();
        long now = (mc != null && mc.level != null) ? mc.level.getGameTime() : 0L;
        ACTIVE.computeIfPresent(entityId, (id, inst) -> {
            inst.beginImmediateFadeOut(now, 10);
            return inst;
        });
    }

    public static void onRender(Camera camera, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // Keep depth available for the aura shader's soft intersection
        DepthCapture.captureIfNeeded();
        if (ModShaders.SHADOW_AURA_FOG_CYLINDER != null) {
            try {
                ModShaders.SHADOW_AURA_FOG_CYLINDER.setSampler("uDepth", DepthCapture.textureId());
            } catch (Throwable ignored) {
            }
        }

        // Cache per-frame matrices and projection parameters
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f invView = new Matrix4f(view).invert();
        Matrix4f invProj = new Matrix4f(proj).invert();
        int screenHeightPx = mc.getWindow().getHeight();
        // Derive tan(fovY/2) from projection matrix: proj.m11 = cot(fovY/2)
        float tanHalfFovY = 1.0f / proj.m11();

        var camPos = camera.getPosition();
        long now = mc.level.getGameTime();

        // --- Inertial tail: CPU lagged velocity update ---
        // Compute a per-frame dt from a high-resolution timer; clamp to sane bounds.
        long nowNano = System.nanoTime();
        if (LAST_FRAME_NANOS == 0L) LAST_FRAME_NANOS = nowNano;
        double dtSec = (nowNano - LAST_FRAME_NANOS) / 1_000_000_000.0;
        if (dtSec < 0.0) dtSec = 0.0;
        if (dtSec > 0.25)
            dtSec = 0.25; // avoid huge jumps on stalls
        LAST_FRAME_NANOS = nowNano;

        // Hoist per-frame uniforms (view/proj/inverses, time) out of the per-instance loop
        var shFrame = ModShaders.SHADOW_AURA_FOG_CYLINDER;
        var uuFrame = ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;
        float timeValFrame = (Minecraft.getInstance().level.getGameTime() + partialTicks) * 0.05f;
        if (shFrame != null) {
            if (uuFrame != null) {
                if (uuFrame.uView() != null) uuFrame.uView().set(view);
                if (uuFrame.uProj() != null) uuFrame.uProj().set(proj);
                if (uuFrame.uInvView() != null) uuFrame.uInvView().set(invView);
                if (uuFrame.uInvProj() != null) uuFrame.uInvProj().set(invProj);
                if (uuFrame.uTime() != null) uuFrame.uTime().set(timeValFrame);
            } else {
                setMat4(shFrame, "uView", view);
                setMat4(shFrame, "uProj", proj);
                shFrame.safeGetUniform("uInvView").set(invView);
                shFrame.safeGetUniform("uInvProj").set(invProj);
                set1f(shFrame, "uTime", timeValFrame);
            }
        }

        for (Map.Entry<Integer, AuraInstance> en : ACTIVE.entrySet()) {
            AuraInstance inst = en.getValue();
            if (inst == null) {
                ACTIVE.remove(en.getKey());
                continue;
            }
            if (inst.isExpired(now)) {
                ACTIVE.remove(en.getKey());
                continue;
            }

            // Interpolate position from client-side entity when available; fallback to last server state
            double ix, iy, iz;
            Entity ent = (inst.entityRef != null) ? inst.entityRef.get() : null;
            boolean useEnt = false;
            if (ent != null && ent.isAlive() && ent.getId() == inst.entityId) {
                if (inst.entityUuid == null || inst.entityUuid.equals(ent.getUUID())) {
                    useEnt = true;

                }
            }
            if (useEnt) {
                ix = Mth.lerp(partialTicks, ent.xOld, ent.getX());
                iy = Mth.lerp(partialTicks, ent.yOld, ent.getY());
                iz = Mth.lerp(partialTicks, ent.zOld, ent.getZ());
            } else {
                ix = Mth.lerp(partialTicks, inst.lastX, inst.x);
                iy = Mth.lerp(partialTicks, inst.lastY, inst.y);
                iz = Mth.lerp(partialTicks, inst.lastZ, inst.z);
            }
            float fade = inst.fadeFactor(now);
            // Interpolate corruption
            float corruption = Mth.lerp(partialTicks, inst.prevCorruption, inst.lastCorruption);
            // Debug handling: if not debugging, completely skip when invisible; otherwise, proceed so the trail can render
            boolean hasVisibility = fade > 0.001f && corruption > 0.01f;
            if (!hasVisibility) continue;

            // Build matrices
            double x = ix - camPos.x;
            double y = iy - camPos.y;
            double z = iz - camPos.z;

            float entityHeight = Math.max(0.001f, Mth.lerp(partialTicks, inst.prevBbH, inst.lastBbH));
            float radius = Math.max(0.25f, Mth.lerp(partialTicks, (float) inst.prevBbSize, (float) inst.lastBbSize) * 1.35f);

            // Screen-space radius for LOD selection
            double cy = y;
            double distCenter = Math.sqrt(x * x + cy * cy + z * z);
            float pxRadiusShell = distCenter > 0.0001 ? (radius * screenHeightPx) / (2f * (float) distCenter * tanHalfFovY) : 9999f;
            int lodShell = (pxRadiusShell > 150f) ? 3 : (pxRadiusShell > 60f) ? 2 : (pxRadiusShell > 20f) ? 1 : 0;

            Matrix4f model = new Matrix4f().translate((float) (x), (float) ((y) + (entityHeight / 2.0f)), (float) (z));
            Matrix4f invModel = new Matrix4f(model).invert();
            Matrix4f mvp = new Matrix4f(proj).mul(view).mul(model);
            var sh = ModShaders.SHADOW_AURA_FOG_CYLINDER;
            var uu = ModShaders.SHADOW_AURA_FOG_CYLINDER_UNIFORMS;
            if (sh != null) {
                if (uu != null) {
                    if (uu.uModel() != null) uu.uModel().set(model);
                    if (uu.uInvModel() != null) uu.uInvModel().set(invModel);
                    if (uu.uMVP() != null) uu.uMVP().set(mvp);
                    if (uu.uCameraPosWS() != null)
                        uu.uCameraPosWS().set((float) 0f, (float) 0f, (float) 0f);
                    if (uu.uEntityPosWS() != null)
                        uu.uEntityPosWS().set((float) ix, (float) iy, (float) iz);
                } else {
                    // Fallback if caching not initialized yet
                    setMat4(sh, "uModel", model);
                    setMat4(sh, "uInvModel", invModel);
                    sh.safeGetUniform("uMVP").set(mvp);
                    setVec3(sh, "uCameraPosWS", 0f, 0f, 0f);
                    setVec3(sh, "uEntityPosWS", (float) x, (float) y, (float) z);
                }

                {
                    float velX = (float) inst.lastDeltaX;
                    float velY = (float) inst.lastDeltaY + 0.0784000015258789f; // include gravity bias to keep slight lift
                    float velZ = (float) inst.lastDeltaZ;
                    float speed = (float) Math.sqrt(velX * velX + velY * velY + velZ * velZ);

                    // Clamp raw velocity magnitude to avoid spikes feeding the pipeline
                    if (speed > MAX_SPEED) {
                        float scale = MAX_SPEED / Math.max(1.0e-6f, speed);
                        velX *= scale;
                        velY *= scale;
                        velZ *= scale;
                        speed = MAX_SPEED;
                    }

                    // 1) Lagged velocity for trail inertia (slower, independent of envelope)
                    final double kLag = 1.0 - Math.exp(-dtSec / TAU_SECONDS);
                    inst.vLagX = Mth.lerp((float) kLag, inst.vLagX, velX);
                    inst.vLagY = Mth.lerp((float) kLag, inst.vLagY, velY);
                    inst.vLagZ = Mth.lerp((float) kLag, inst.vLagZ, velZ);

                    // 2) Speed envelope with attack/release and deadband to avoid sudden start/stop pops
                    float targetSpeed = speed;
                    if (targetSpeed < DEAD_SPEED)
                        targetSpeed = 0f; // ignore micro jitter
                    if (targetSpeed > MAX_SPEED)
                        targetSpeed = MAX_SPEED; // enforce cap

                    // Hysteresis-based selection of attack vs release tau
                    double chosenTau = ENV_ATTACK_TAU_SECONDS;
                    if (targetSpeed + HYSTERESIS < inst.speedEnv) {
                        chosenTau = ENV_RELEASE_TAU_SECONDS; // decay slower when stopping
                    } else if (targetSpeed > inst.speedEnv + HYSTERESIS) {
                        chosenTau = ENV_ATTACK_TAU_SECONDS; // ramp faster when starting
                    } else {
                        // in hysteresis band: keep previous tendency by selecting tau based on last move
                        chosenTau = inst.envWasReleasing ? ENV_RELEASE_TAU_SECONDS : ENV_ATTACK_TAU_SECONDS;
                    }
                    inst.envWasReleasing = chosenTau == ENV_RELEASE_TAU_SECONDS;
                    final double kEnv = 1.0 - Math.exp(-dtSec / chosenTau);
                    inst.speedEnv = Mth.lerp((float) kEnv, inst.speedEnv, targetSpeed);

                    // 3) Direction envelope: keep direction stable; align toward current velocity smoothly
                    float dirX, dirY, dirZ;
                    if (speed > 1.0e-4f) {
                        float inv = 1.0f / speed;
                        dirX = velX * inv;
                        dirY = velY * inv;
                        dirZ = velZ * inv;
                    } else {
                        // If we're essentially stopped, keep previous direction (or zero if none)
                        float prevMag = (float) Math.sqrt(inst.envVelX * inst.envVelX + inst.envVelY * inst.envVelY + inst.envVelZ * inst.envVelZ);
                        if (prevMag > 1.0e-4f) {
                            float inv = 1.0f / prevMag;
                            dirX = inst.envVelX * inv;
                            dirY = inst.envVelY * inv;
                            dirZ = inst.envVelZ * inv;
                        } else {
                            dirX = dirY = dirZ = 0f;
                        }
                    }
                    float targetEnvVelX = dirX * inst.speedEnv;
                    float targetEnvVelY = dirY * inst.speedEnv;
                    float targetEnvVelZ = dirZ * inst.speedEnv;

                    final double kDir = 1.0 - Math.exp(-dtSec / DIR_TAU_SECONDS);
                    inst.envVelX = Mth.lerp((float) kDir, inst.envVelX, targetEnvVelX);
                    inst.envVelY = Mth.lerp((float) kDir, inst.envVelY, targetEnvVelY);
                    inst.envVelZ = Mth.lerp((float) kDir, inst.envVelZ, targetEnvVelZ);

                    // 4) Final visual smoothing to avoid micro jitter before sending to shader
                    final double kVis = 1.0 - Math.exp(-dtSec / VIS_TAU_SECONDS);
                    inst.velVisX = Mth.lerp((float) kVis, inst.velVisX, inst.envVelX);
                    inst.velVisY = Mth.lerp((float) kVis, inst.velVisY, inst.envVelY);
                    inst.velVisZ = Mth.lerp((float) kVis, inst.velVisZ, inst.envVelZ);
                    inst.speedVis = Mth.lerp((float) kVis, inst.speedVis, inst.speedEnv);

                    // Final safety clamps on visuals
                    float visMag = (float) Math.sqrt(inst.velVisX * inst.velVisX + inst.velVisY * inst.velVisY + inst.velVisZ * inst.velVisZ);
                    if (visMag > MAX_SPEED) {
                        float s = MAX_SPEED / Math.max(1.0e-6f, visMag);
                        inst.velVisX *= s;
                        inst.velVisY *= s;
                        inst.velVisZ *= s;
                    }
                    if (inst.speedVis > MAX_SPEED) inst.speedVis = MAX_SPEED;

                    if (uu != null) {
                        if (uu.uEntityVelWS() != null)
                            uu.uEntityVelWS().set(inst.velVisX, inst.velVisY, inst.velVisZ);
                        if (uu.uVelLagWS() != null)
                            uu.uVelLagWS().set(inst.vLagX, inst.vLagY, inst.vLagZ);
                        if (uu.uSpeed() != null)
                            uu.uSpeed().set(inst.speedVis * 300);
                    } else {
                        setVec3(sh, "uEntityVelWS", inst.velVisX, inst.velVisY, inst.velVisZ);
                        setVec3(sh, "uVelLagWS", inst.vLagX, inst.vLagY, inst.vLagZ);
                        set1f(sh, "uSpeed", inst.speedVis * 100);
                    }
                }

                if (uu != null) {
                    if (uu.uExpand() != null)
                        uu.uExpand().set(1f);
                    if (uu.uProxyRadius() != null)
                        uu.uProxyRadius().set(radius);
                    if (uu.uProxyHalfHeight() != null)
                        uu.uProxyHalfHeight().set(entityHeight);
                    if (uu.uAuraFade() != null)
                        uu.uAuraFade().set(0.8f * fade);
                    if (uu.uDensity() != null)
                        uu.uDensity().set(radius);
                    if (uu.uMaxThickness() != null)
                        uu.uMaxThickness().set(radius * 0.65f);
                    if (uu.uThicknessFeather() != null)
                        uu.uThicknessFeather().set(radius * 0f);
                    if (uu.uEdgeKill() != null)
                        uu.uEdgeKill().set(0.0f);
                    if (uu.uLimbSoft() != null) uu.uLimbSoft().set(0.22f);
                    if (uu.uLimbHardness() != null)
                        uu.uLimbHardness().set(2.25f);
                    if (uu.uMinPathNorm() != null) uu.uMinPathNorm().set(0.15f);
                    if (uu.uHeightFadePow() != null)
                        uu.uHeightFadePow().set(0.20f);
                    if (uu.uHeightFadeMin() != null)
                        uu.uHeightFadeMin().set(0.60f);
                    if (uu.uCorePow() != null) uu.uCorePow().set(3.5f);
                    if (uu.uGlowGamma() != null) uu.uGlowGamma().set(0.5f);
                    if (uu.uRimPower() != null) uu.uRimPower().set(0.05f);
                    if (uu.uRimStrength() != null) uu.uRimStrength().set(5.5f);
                    if (uu.uHeightFadePow() != null)
                        uu.uHeightFadePow().set(1.60f);
                    if (uu.uHeightFadeMin() != null)
                        uu.uHeightFadeMin().set(-0.15f);
                    if (uu.uPixelsPerRadius() != null)
                        uu.uPixelsPerRadius().set(20.0f);
                    if (uu.uPosterizeSteps() != null)
                        uu.uPosterizeSteps().set(3.0f);
                    if (uu.uPatchSharpness() != null)
                        uu.uPatchSharpness().set(0.25f);
                    if (uu.uPatchGamma() != null) uu.uPatchGamma().set(0.65f);
                    if (uu.uPatchThreshTop() != null)
                        uu.uPatchThreshTop().set(0.80f);
                    if (uu.uPatchThreshBase() != null)
                        uu.uPatchThreshBase().set(0.30f);
                } else {
                    set1f(sh, "uExpand", 1f);
                    set1f(sh, "uProxyRadius", radius);
                    set1f(sh, "uProxyHalfHeight", radius * 0.5f);
                    set1f(sh, "uAuraFade", 0.75f * fade);
                    set1f(sh, "uDensity", radius);
                    set1f(sh, "uMaxThickness", radius);
                    set1f(sh, "uThicknessFeather", 0);
                    set1f(sh, "uEdgeKill", radius * 0.5f);
                }
            }
            VertexConsumer vcShell = buffers.getBuffer(AuraRenderTypes.shadow_fog());
            Matrix4f mat = new Matrix4f();
            mat.scale(radius, entityHeight * 1.5f, radius);
            //CylinderBuffers.drawCylinderFlatCaps(vcShell, mat, 0, 0, 0, 0, lodShell);
            CylinderBuffers.drawCylinderWithDomesLod(vcShell, mat, 1f, 0, 0, 0, 0, lodShell);
            /*com.jayemceekay.shadowedhearts.client.render.geom.SphereBuffers.drawUnitSphereLod(vcShell, mat, 0, 0, 0, 0, lodShell);*/
            // Flush the buffer for this render type to ensure per-instance uniforms apply to this aura only
            buffers.endLastBatch();
        }
    }

    private static final class AuraInstance {
        private long startTick;
        private int fadeInTicks;
        private int sustainTicks;
        private int fadeOutTicks;

        // Entity reference for client-side interpolation
        private int entityId;
        private WeakReference<Entity> entityRef;
        private UUID entityUuid;

        // Cached transform/state updated from server (used as fallback/smoothing for size & corruption)
        double x, y, z;
        double lastX, lastY, lastZ;
        double lastDeltaX, lastDeltaY, lastDeltaZ;
        float lastBbH, lastBbW;
        float prevBbH, prevBbW;
        double lastBbSize;
        double prevBbSize;
        float lastCorruption = 1.0f;
        float prevCorruption = 1.0f;
        long lastServerTick = -1L;

        // Lagged velocity state (object uploads uVelLagWS per frame)
        float vLagX = 0f, vLagY = 0f, vLagZ = 0f;
        // Smoothed (display) velocity and speed sent to shader to avoid popping
        float velVisX = 0f, velVisY = 0f, velVisZ = 0f;
        float speedVis = 0f;

        // Envelope follower state for start/stop smoothing
        float speedEnv = 0f;       // accumulated speed with attack/release
        float envVelX = 0f, envVelY = 0f, envVelZ = 0f; // direction-weighted envelope velocity
        boolean envWasReleasing = false; // remembers last envelope mode inside hysteresis band

        AuraInstance(int entityId, Entity ent, long startTick, int fadeInTicks, int sustainTicks, int fadeOutTicks, double x, double y, double z, double dx, double dy, double dz, float bbw, float bbh, double bbs, float lastCorruption) {
            this.entityId = entityId;
            this.entityRef = new WeakReference<>(ent);
            this.entityUuid = (ent != null) ? ent.getUUID() : null;
            this.startTick = startTick;
            this.fadeInTicks = Math.max(1, fadeInTicks);
            this.sustainTicks = Math.max(0, sustainTicks);
            this.fadeOutTicks = Math.max(1, fadeOutTicks);
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastDeltaX = dx;
            this.lastDeltaY = dy;
            this.lastDeltaZ = dz;
            this.lastBbH = bbh;
            this.prevBbH = bbh;
            this.lastBbW = bbw;
            this.prevBbW = bbw;
            this.lastBbSize = bbs;
            this.prevBbSize = bbs;
            this.lastCorruption = lastCorruption;
            this.prevCorruption = lastCorruption;
            // Initialize visual smoothing state to current inputs to avoid first-frame pops
            this.velVisX = (float) dx;
            this.velVisY = (float) dy;
            this.velVisZ = (float) dz;
            this.speedVis = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Initialize envelope follower to current motion to avoid first-frame snap
            this.speedEnv = this.speedVis;
            this.envVelX = (float) dx;
            this.envVelY = (float) dy;
            this.envVelZ = (float) dz;
            this.envWasReleasing = false;
        }

        void beginImmediateFadeOut(long now, int outTicks) {
            this.startTick = now - (long) this.fadeInTicks - (long) this.sustainTicks;
            this.fadeOutTicks = Math.max(1, outTicks);
        }

        boolean isExpired(long now) {
            long total = (long) fadeInTicks + (long) sustainTicks + (long) fadeOutTicks;
            return now - startTick >= total;
        }

        float fadeFactor(long now) {
            long age = Math.max(0, now - startTick);
            long fi = this.fadeInTicks;
            long sus = this.sustainTicks;
            long fo = this.fadeOutTicks;
            if (age < fi) return (float) age / (float) fi;
            age -= fi;
            if (age < sus) return 1f;
            age -= sus;
            if (age < fo) return 1f - (float) age / (float) fo;
            return 0f;
        }

        float getCorruption() {
            return lastCorruption;
        }
    }

    // --- small uniform helpers ---
    private static void set1f(ShaderInstance sh, String name, float v) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setVec3(ShaderInstance sh, String name, float x, float y, float z) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(x, y, z);
    }

    private static void setMat4(ShaderInstance sh, String name, Matrix4f m) {
        final Uniform u = sh.getUniform(name);
        if (u != null) u.set(m);
    }
}
