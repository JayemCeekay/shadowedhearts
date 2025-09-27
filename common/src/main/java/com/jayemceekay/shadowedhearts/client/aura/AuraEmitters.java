package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuraEmitter system that attaches an aura instance to a Pokémon when it is sent out.
 * Rendering is driven by this system, not by the Pokémon's own renderer.
 */
public final class AuraEmitters {
    public  static MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(512 * 1024));

    private AuraEmitters() {
    }

    private static final Map<Integer, AuraInstance> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Called by networking handler when a state update arrives.
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
    private static final int SUSTAIN = 6000; // can be tuned; original recent value ~90-100
    private static final int FADE_OUT = 10;
    // If a position update jumps farther than this squared distance, snap to avoid long lerp streaks
    private static final double TELEPORT_SNAP_DIST2 = 36.0; // 6 blocks squared

    // Trail settings
    private static final int TRAIL_LIFETIME_TICKS = 36; // how long a ghost lingers
    private static final int TRAIL_FADE_IN_TICKS = 6;   // how long each node takes to fade in to full alpha
    private static final float TRAIL_EMIT_DIST = 1.2f;  // spacing in blocks between ghosts
    private static final int TRAIL_MAX_NODES = 8;      // cap per entity to avoid perf spikes
    private static final float TRAIL_SHRINK_MIN = 0.15f; // final scale relative to start
    private static final float TRAIL_ALPHA_BASE = 0.55f; // base opacity multiplier for trail

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
        //DepthCapture.captureIfNeeded();
        if (ModShaders.SHADOW_AURA_FOG != null) {
            try {
                ModShaders.SHADOW_AURA_FOG.setSampler("uDepth", DepthCapture.textureId());
            } catch (Throwable ignored) {
            }
        }



        var camPos = camera.getPosition();
        long now = mc.level.getGameTime();

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
            if (fade <= 0.001f) continue;

            // Interpolate corruption and skip if effectively invisible
            float corruption = Mth.lerp(partialTicks, inst.prevCorruption, inst.lastCorruption);
            if (corruption <= 0.01f) continue;

            // Build matrices
            double x = ix - camPos.x;
            double y = iy - camPos.y;
            double z = iz - camPos.z;

            float entityHeight = Math.max(0.001f, Mth.lerp(partialTicks, inst.prevBbH, inst.lastBbH));
            float radius = (float) Math.max(0.25f, Mth.lerp(partialTicks, (float) inst.prevBbSize, (float) inst.lastBbSize) * 1.35f);

            Matrix4f view = RenderSystem.getModelViewMatrix();
            Matrix4f proj = RenderSystem.getProjectionMatrix();
            Matrix4f model = new Matrix4f().translateLocal((float) x, (float) (y + (entityHeight * 0.55F)), (float) z);
            Matrix4f invModel = new Matrix4f(model).invert();
            Matrix4f mvp = new Matrix4f(proj).mul(view).mul(model);
            Matrix4f invView = new Matrix4f(view).invert();
            Matrix4f invProj = new Matrix4f(proj).invert();
            var u = ModShaders.SHADOW_AURA_FOG;
            if (ModShaders.SHADOW_AURA_FOG != null) {

                setMat4(u, "uModel", model);
                setMat4(u, "uInvModel", invModel);
                setMat4(u, "uView", view);
                setMat4(u, "uProj", proj);
                u.safeGetUniform("uMVP").set(mvp);
                u.safeGetUniform("uInvView").set(invView);
                u.safeGetUniform("uInvProj").set(invProj);
                setVec3(u, "uCameraPosWS", 0f, 0f, 0f);
                setVec3(u, "uEntityPosWS", (float) ix, (float) iy, (float) iz);

                set1f(u, "uTime", (Minecraft.getInstance().level.getGameTime() + partialTicks) * 0.05f);
                set1f(u, "uExpand", 1f);

                set1f(u, "uProxyRadius", radius);
                set1f(u, "uCapsuleHalf", 0.0f);

                set1f(u, "uAuraFade", 0.75f * fade);
                set1f(u, "uFadeGamma", 1.5f);

                setVec3(u, "uColorA", 0.30f, 0.1f, 0.35f);
                setVec3(u, "uColorB", 0.85f, 0.30f, 1.30f);

                set1f(u, "uDensity", radius * .75f);
                set1f(u, "uAbsorption", 0.15f);
                set1f(u, "uRimStrength", 2.0f);
                set1f(u, "uRimPower", 1.0f);

                set1f(u, "uMaxThickness", radius * 1.25f);
                set1f(u, "uThicknessFeather", radius * 0.015f);
                set1f(u, "uEdgeKill", 1f);

                set1f(u, "uShellFeather", 0.65f);

                set1f(u, "uNoiseScaleRel", 5f);
                set1f(u, "uScrollSpeedRel", -2.5f);
                set1f(u, "uLayersAcrossThickness", 1.0f);

                set1f(u, "uWarpAmp", 0.2f);

                set1f(u, "uLimbSoft", 0.22f);
                set1f(u, "uMinPathNorm", 0.18f);
                set1f(u, "uGlowGamma", 2.5f);
                set1f(u, "uBlackPoint", 0.35f);
            }
            u.apply();
            VertexConsumer vcShell = buffers.getBuffer(AuraRenderTypes.shadow_fog());
            Matrix4f mat = new Matrix4f();
            mat.scale(radius, radius, radius);
            com.jayemceekay.shadowedhearts.client.render.geom.SphereBuffers.drawUnitSphere(
                    vcShell, mat, 0, 0, 0, 0
            );
            // Flush the buffer for this render type to ensure per-instance uniforms apply to this aura only
            u.clear();
            buffers.endBatch(AuraRenderTypes.shadow_fog());
            // Emit trail node based on movement spacing
            if (!inst.hasEmitPos) {
                inst.lastEmitX = ix;
                inst.lastEmitY = iy;
                inst.lastEmitZ = iz;
                inst.hasEmitPos = true;
                inst.lastEmitTick = now;
            } else {
                double ddx = ix - inst.lastEmitX;
                double ddy = iy - inst.lastEmitY;
                double ddz = iz - inst.lastEmitZ;
                double dist2 = ddx * ddx + ddy * ddy + ddz * ddz;
                if (dist2 >= (double) (TRAIL_EMIT_DIST * TRAIL_EMIT_DIST)) {
                    // Add a node at previous emit pos so it lags slightly behind
                    if (inst.trail.size() >= TRAIL_MAX_NODES) {
                        inst.trail.removeFirst();
                    }
                    inst.trail.addLast(new AuraInstance.TrailNode(inst.lastEmitX, inst.lastEmitY, inst.lastEmitZ, entityHeight, radius, now, fade * TRAIL_ALPHA_BASE));
                    inst.lastEmitX = ix;
                    inst.lastEmitY = iy;
                    inst.lastEmitZ = iz;
                    inst.lastEmitTick = now;
                }
            }

            // Render trail nodes with shrinking/fading effect
            if (!inst.trail.isEmpty()) {
                var it = inst.trail.iterator();
                while (it.hasNext()) {
                    var node = it.next();
                    float age = ((float) (now - node.spawnTick) + partialTicks) / (float) TRAIL_LIFETIME_TICKS;
                    if (age >= 1.0f) {
                        it.remove();
                        continue;
                    }
                    float fadeInPortion = (float) TRAIL_FADE_IN_TICKS / (float) TRAIL_LIFETIME_TICKS;
                    float inFrac = fadeInPortion > 0f ? Mth.clamp(age / fadeInPortion, 0f, 1f) : 1f;
                    float nodeAlpha = node.baseFade * inFrac * (1.0f - age);
                    float nodeRadius = node.startRadius * (1.0f - (1.0f - TRAIL_SHRINK_MIN) * age);

                    double nx = node.x - camPos.x;
                    double ny = node.y - camPos.y;
                    double nz = node.z - camPos.z;

                    Matrix4f nModel = new Matrix4f().translateLocal((float) nx, (float) (ny + (node.height * 0.55F)), (float) nz);
                    Matrix4f nInvModel = new Matrix4f(nModel).invert();
                    Matrix4f nMvp = new Matrix4f(proj).mul(view).mul(nModel);
                    var u2 = ModShaders.SHADOW_AURA_FOG_TRAIL;
                    if (ModShaders.SHADOW_AURA_FOG_TRAIL != null) {
                        setMat4(u2, "uModel", nModel);
                        setMat4(u2, "uInvModel", nInvModel);
                        setMat4(u2, "uView", view);
                        setMat4(u2, "uProj", proj);
                        u2.safeGetUniform("uMVP").set(nMvp);
                        u2.safeGetUniform("uInvView").set(new Matrix4f(view).invert());
                        u2.safeGetUniform("uInvProj").set(new Matrix4f(proj).invert());
                        setVec3(u2, "uCameraPosWS", 0f, 0f, 0f);
                        setVec3(u2, "uEntityPosWS", (float) node.x, (float) node.y, (float) node.z);

                        set1f(u2, "uTime", (Minecraft.getInstance().level.getGameTime() + partialTicks) * 0.05f);
                        set1f(u2, "uExpand", 1f);

                        set1f(u2, "uProxyRadius", nodeRadius);
                        set1f(u2, "uCapsuleHalf", 0.0f);

                        set1f(u2, "uAuraFade", nodeAlpha);
                        set1f(u2, "uFadeGamma", 1.5f);

                        setVec3(u2, "uColorA", 0.30f, 0.1f, 0.35f);
                        setVec3(u2, "uColorB", 0.85f, 0.30f, 1.30f);

                        set1f(u2, "uDensity", nodeRadius * .75f);
                        set1f(u2, "uAbsorption", 0.15f);
                        set1f(u2, "uRimStrength", 2.0f);
                        set1f(u2, "uRimPower", 1.0f);

                        set1f(u2, "uMaxThickness", nodeRadius * 1.25f);
                        set1f(u2, "uThicknessFeather", nodeRadius * 0.015f);
                        set1f(u2, "uEdgeKill", 1f);

                        set1f(u2, "uShellFeather", 0.65f);

                        set1f(u2, "uNoiseScaleRel", 5f);
                        set1f(u2, "uScrollSpeedRel", -2.5f);
                        set1f(u2, "uLayersAcrossThickness", 1.0f);

                        set1f(u2, "uWarpAmp", 0.2f);

                        set1f(u2, "uLimbSoft", 0.22f);
                        set1f(u2, "uMinPathNorm", 0.18f);
                        set1f(u2, "uGlowGamma", 2.5f);
                        set1f(u2, "uBlackPoint", 0.35f);
                    }
                    u2.apply();
                    VertexConsumer vcTrail = buffers.getBuffer(AuraRenderTypes.shadow_fog_trail());
                    Matrix4f tGeom = new Matrix4f();
                    tGeom.scale(node.startRadius, node.startRadius, node.startRadius);
                    com.jayemceekay.shadowedhearts.client.render.geom.SphereBuffers.drawUnitSphere(
                            vcTrail, tGeom, 0, 0, 0, 0
                    );
                    // Flush after each trail node to apply its specific uniforms
                    u2.clear();
                    buffers.endBatch(AuraRenderTypes.shadow_fog_trail());
                }

            }

            float poolRadius = Math.max(0.5f, Mth.lerp(partialTicks, inst.prevBbW, inst.lastBbW) * 1.1f);
            if (ModShaders.SHADOW_POOL != null) {
                var sp = ModShaders.SHADOW_POOL;
                setMat4(sp, "uView", view);
                setMat4(sp, "uProj", proj);
                sp.safeGetUniform("uTime").set((Minecraft.getInstance().level.getGameTime() + partialTicks) * 0.05f);
                sp.safeGetUniform("uRippleAmp").set(0.012f);
                sp.safeGetUniform("uRippleFreq").set(6.0f);
                sp.safeGetUniform("uRippleSpeed").set(0.35f);
                sp.safeGetUniform("uRippleEdge").set(0.82f);
                sp.safeGetUniform("uRippleSteep").set(0.35f);
                sp.safeGetUniform("uRippleUvAmp").set(0.015f);

                sp.safeGetUniform("uPoolCenterXZ").set((float) ix, (float) iz);
                sp.safeGetUniform("uRadius").set(poolRadius);
                sp.safeGetUniform("uGoopScale").set(1.0f);
                sp.safeGetUniform("uGoopBump").set(0.1f);
                sp.safeGetUniform("uOutflow").set(0.62f);
                sp.safeGetUniform("uRingSpacing").set(0.18f);
                sp.safeGetUniform("uRingWidth").set(0.10f);
                sp.safeGetUniform("uRingSpeed").set(0.30f);
                sp.safeGetUniform("uRingAmp").set(0.006f);
                sp.safeGetUniform("uSwirlOmega").set(0.85f);
                sp.safeGetUniform("uSwirlFalloff").set(1.25f);
                sp.safeGetUniform("uSpiralAmp").set(1.020f);
                sp.safeGetUniform("uSpiralArms").set(3.0f);
                sp.safeGetUniform("uNormalStr").set(0.8f);
                sp.safeGetUniform("uSpec1Pow").set(48.0f);
                sp.safeGetUniform("uSpec1Amp").set(0.12f);
                sp.safeGetUniform("uSpec2Pow").set(160.0f);
                sp.safeGetUniform("uSpec2Amp").set(0.20f);
                sp.safeGetUniform("uSpecColor").set(0.65f, 0.25f, 0.95f);
                sp.safeGetUniform("uEdgeFeather").set(0.85f);
                sp.safeGetUniform("uCoreDark").set(1.90f);
                sp.safeGetUniform("uGlowAmp").set(0.10f);
                sp.safeGetUniform("uLightDir").set(0.2f, 1.0f, 0.15f);
                sp.safeGetUniform("uViewDir").set(0.0f, 1.0f, 0.0f);
                sp.safeGetUniform("uRimAmp").set(0.30f);
                sp.safeGetUniform("uRimRadius").set(0.92f);
                sp.safeGetUniform("uRimWidth").set(0.15f);
                sp.safeGetUniform("uShadowTint").set(0.06f, 0.02f, 0.09f);
                sp.safeGetUniform("uGlowTint").set(0.26f, 0.09f, 0.45f);
                sp.safeGetUniform("uGlobalFade").set(0.8f * fade);
            }

            // Optional pool quad geometry can be added if needed; currently render type handles it.
        }
    }

    private static final class AuraInstance {
        private long startTick;
        private int fadeInTicks;
        private int sustainTicks;
        private int fadeOutTicks;

        // Trail state
        private final Deque<TrailNode> trail = new ArrayDeque<>();
        private double lastEmitX, lastEmitY, lastEmitZ;
        private boolean hasEmitPos = false;
        private long lastEmitTick = 0L;

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

        private static final class TrailNode {
            final double x, y, z;
            final float height;
            final float startRadius;
            final long spawnTick;
            final float baseFade;
            TrailNode(double x, double y, double z, float height, float startRadius, long spawnTick, float baseFade) {
                this.x = x; this.y = y; this.z = z;
                this.height = height;
                this.startRadius = startRadius;
                this.spawnTick = spawnTick;
                this.baseFade = baseFade;
            }
        }

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
