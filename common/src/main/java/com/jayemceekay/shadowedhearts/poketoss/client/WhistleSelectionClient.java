package com.jayemceekay.shadowedhearts.poketoss.client;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.AuraRenderTypes;
import com.jayemceekay.shadowedhearts.network.payload.WhistleSelectionC2S;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.architectury.networking.NetworkManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client-side helper managing the Trainer's Whistle hold-to-brush selection flow and visuals.
 *
 * Minimal implementation: uses particles to visualize a light-green disk up to 3 blocks radius
 * following the crosshair while RMB is held. On release, sends entity id list to server.
 */
public final class WhistleSelectionClient {
    private WhistleSelectionClient() {}

    // Public selection overlay color to keep visuals consistent across systems (shader and entity tint)
    public static final float SEL_R = 0.50f;
    public static final float SEL_G = 1.00f;
    public static final float SEL_B = 0.50f;

    private static boolean active = false;
    private static long startTick = 0L;
    private static final List<Vec3> samples = new ArrayList<>();
    private static float currentRadius = 0.0f;
    private static InteractionHand usingHand = InteractionHand.MAIN_HAND;
    private static final IntOpenHashSet selectedIds = new IntOpenHashSet();
    private static boolean wheelActive = false;
    private static boolean cursorUnlockedForWheel = false;

    private static final float MAX_RADIUS = 3.0f;
    private static final float GROW_PER_TICK = 0.25f; // ~0.25 block per tick -> 12 ticks to max
    private static final int QUICK_TAP_TICKS = 7; // under ~0.35s counts as quick tap

    // --- Order wheel (tween menu) state ---
    private static boolean prevWheelActive = false;
    private static boolean wasLeftDown = false;
    private static boolean combatSubOpen = false;
    private static float combatTween = 0.0f; // 0..1
    private static boolean posSubOpen = false;
    private static float posTween = 0.0f; // 0..1
    private static boolean utilSubOpen = false;
    private static float utilTween = 0.0f; // 0..1
    private static boolean ctxSubOpen = false;
    private static float ctxTween = 0.0f; // 0..1
    // Debounce flag to prevent synthetic re-presses from toggling submenus back immediately
    private static boolean suppressUntilMouseUp = false;

    // Scrollable submenu model: labels and indices with wrap-around
    private static final String[] COMBAT_LABELS = new String[] { "Attack", "Guard", "Disengage" };
    private static int combatIndex = 0; // leftmost visible item index

    private static final String[] POSITION_LABELS = new String[] { "Move To", "Hold Position" };
    private static int posIndex = 0; // top visible item index

    private static final String[] UTILITY_LABELS = new String[] { "Regroup to Me" };
    private static int utilIndex = 0; // single visible item index (cycles)

    private static final String[] CONTEXT_LABELS = new String[] { "Hold At Me" };
    private static int ctxIndex = 0; // single visible item index (cycles)

    private static int wrap(int idx, int size) {
        if (size <= 0) return 0;
        int m = idx % size;
        return m < 0 ? m + size : m;
    }

    private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.6f, 1.0f, 0.6f), 1.25f);

    public static void init() {
        // no common event bus here; platform-specific clients will call onTick() and onRender() from their hooks
    }

    /** Called by TrainersWhistleItem on client when use begins. */
    public static void begin() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        // Disable brush selection while in target-selection mode
        if (TargetSelectionClient.isActive()) {
            return;
        }
        active = true;
        startTick = mc.level.getGameTime();
        currentRadius = 0.0f;
        samples.clear();
        selectedIds.clear();
        usingHand = mc.player.getUsedItemHand();
        // Seed first sample from current crosshair hit
        Vec3 hit = currentHitPos(mc.player, 128.0);
        if (hit != null) samples.add(hit);
    }

    /** Platform clients should call this from a client tick END handler. */
    public static void onTick() {
        var mc = Minecraft.getInstance();
        if (mc == null) return;

        // Wheel state handled by separated UIs
        if (com.jayemceekay.shadowedhearts.config.ModConfig.get().tossOrderBarUI) {
            TossOrderBarUI.onTick();
        } else {
            TossOrderRadialWheel.onTick();
        }

        // Disable brush while targeting an order
        if (com.jayemceekay.shadowedhearts.poketoss.client.TargetSelectionClient.isActive()) {
            if (active) {
                clear(); // do not send; target selection takes priority
            }
            return;
        }

        if (!active) return;
        if (mc.player == null || mc.level == null) { clear(); return; }
        LocalPlayer player = mc.player;

        // If player stopped using item or switched, finish
        if (!player.isUsingItem() || player.getUseItem().isEmpty()) {
            finishAndSend(false);
            return;
        }

        // Continue sampling brush center from hit pos
        Vec3 hit = currentHitPos(player, 128.0);
        if (hit != null) {
            if (samples.isEmpty() || samples.get(samples.size() - 1).distanceToSqr(hit) > 0.09) { // ~0.3 blocks spacing
                samples.add(hit);
            }
            // Visualize a ring of particles at the current radius (or small if still growing)
            growRadius();
            //spawnRingParticles(mc.level, hit, currentRadius);
            updateIncrementalSelection(mc.level, hit, currentRadius);
        }
    }

    /** World render hook to draw the green ground overlay with a shader while selecting. */
    public static void onRender() {
        // Suppress brush overlay while in target selection mode
        if (com.jayemceekay.shadowedhearts.poketoss.client.TargetSelectionClient.isActive()) return;
        if (!active) return;
        var mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (mc == null || mc.level == null || mc.player == null) return;
        Vec3 camPos = camera.getPosition();
        Vec3 hit = currentHitPos(mc.player, 128.0);
        if (hit == null) return;
        hit = hit.subtract(camPos.x, camPos.y, camPos.z);
        float radius = Math.max(0.35f, currentRadius);
        float softness = 0.45f; // Keep in sync with uniform below
        float fadeHeight = 4.0f; // Cylinder fades out this high above the ring

        // Set shader and uniforms
        RenderSystem.setShader(() -> ModShaders.WHISTLE_GROUND_OVERLAY);
        if (ModShaders.WHISTLE_GROUND_OVERLAY != null) {
            var sh = ModShaders.WHISTLE_GROUND_OVERLAY;
            Matrix4f view = RenderSystem.getModelViewMatrix();
            Matrix4f proj = RenderSystem.getProjectionMatrix();
            // Set matrices and brush params
            sh.safeGetUniform("uView").set(view);
            sh.safeGetUniform("uProj").set(proj);
            sh.safeGetUniform("uCenterXZ").set((float) hit.x, (float) hit.z);
            sh.safeGetUniform("uRadius").set(radius);
            sh.safeGetUniform("uColor").set(SEL_R, SEL_G, SEL_B);
            sh.safeGetUniform("uOpacity").set(0.72f);
            sh.safeGetUniform("uSoftness").set(softness);
            sh.safeGetUniform("uBaseY").set((float) (hit.y + 0.02));
            sh.safeGetUniform("uFadeHeight").set(fadeHeight);
            // Time for shader animations (seconds)
            if (mc.level != null) {
                float timeSec = (Minecraft.getInstance().level.getGameTime() - startTick)/20.0f;
                sh.safeGetUniform("uTime").set(timeSec);
            } else {
                sh.safeGetUniform("uTime").set(0.0f);
            }
        }

        // Build a small grid that conforms to terrain by raycasting above/below each vertex.
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(AuraRenderTypes.whistle_ground_overlay());
        Matrix4f I = new Matrix4f().identity();

        // Expand by softness so the ring band (which extends slightly beyond radius) isn't clipped by the quad
        float quadHalf = radius + softness;
        float x0 = (float) (hit.x - quadHalf);
        float x1 = (float) (hit.x + quadHalf);
        float z0 = (float) (hit.z - quadHalf);
        float z1 = (float) (hit.z + quadHalf);

        // Grid resolution (balanced perf/quality)
        final float STEP = 0.5f;
        final int NX = Math.max(2, (int)Math.ceil((x1 - x0) / STEP) + 1);
        final int NZ = Math.max(2, (int)Math.ceil((z1 - z0) / STEP) + 1);
        final double baseWorldY = hit.y + camPos.y;
        final double camY = camPos.y;
        final double SEARCH_UP = 6.0;   // how far to look above
        final double SEARCH_DOWN = 0.0; // and below

        // Pre-sample heights
        double[] xs = new double[NX];
        double[] zs = new double[NZ];
        double[][] ys = new double[NX][NZ];
        for (int ix = 0; ix < NX; ix++) {
            double vx = x0 + (x1 - x0) * (ix / (double)(NX - 1));
            xs[ix] = vx;
        }
        for (int iz = 0; iz < NZ; iz++) {
            double vz = z0 + (z1 - z0) * (iz / (double)(NZ - 1));
            zs[iz] = vz;
        }
        for (int ix = 0; ix < NX; ix++) {
            for (int iz = 0; iz < NZ; iz++) {
                double worldX = xs[ix] + camPos.x;
                double worldZ = zs[iz] + camPos.z;
                double yWS = sampleNearestSurfaceY(mc.level, worldX, baseWorldY, worldZ, SEARCH_UP, SEARCH_DOWN);
                // Convert back to camera-relative space and nudge up slightly to avoid z-fighting
                ys[ix][iz] = (yWS - camY) + 0.02;
            }
        }

        // Emit triangles for the grid
        for (int iz = 0; iz < NZ - 1; iz++) {
            for (int ix = 0; ix < NX - 1; ix++) {
                float xA = (float) xs[ix];
                float zA = (float) zs[iz];
                float xB = (float) xs[ix + 1];
                float zB = (float) zs[iz + 1];

                // tri 1: (ix,iz) -> (ix+1,iz) -> (ix+1,iz+1)
                vc.addVertex(I, xA, (float) ys[ix][iz], zA).setUv(0, 0).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
                vc.addVertex(I, xB, (float) ys[ix + 1][iz], zA).setUv(1, 0).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
                vc.addVertex(I, xB, (float) ys[ix + 1][iz + 1], zB).setUv(1, 1).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);

                // tri 2: (ix,iz) -> (ix+1,iz+1) -> (ix,iz+1)
                vc.addVertex(I, xA, (float) ys[ix][iz], zA).setUv(0, 0).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
                vc.addVertex(I, xB, (float) ys[ix + 1][iz + 1], zB).setUv(1, 1).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
                vc.addVertex(I, xA, (float) ys[ix][iz + 1], zB).setUv(0, 1).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
            }
        }

        // Add vertical skirts along height discontinuities to highlight block sides
        final float DROP_EPS = 0.35f; // consider anything steeper than ~1/3 block a "drop"

        // Emit skirts per-cell so they sit at the interface midlines and have proper orientation
       /* for (int iz = 0; iz < NZ - 1; iz++) {
            float zA = (float) zs[iz];
            float zB = (float) zs[iz + 1];
            float zMid = (zA + zB) * 0.5f;
            for (int ix = 0; ix < NX - 1; ix++) {
                float xA = (float) xs[ix];
                float xB = (float) xs[ix + 1];
                float xMid = (xA + xB) * 0.5f;

                // Heights at cell corners
                float y00 = (float) ys[ix][iz];
                float y10 = (float) ys[ix + 1][iz];
                float y01 = (float) ys[ix][iz + 1];
                float y11 = (float) ys[ix + 1][iz + 1];

                // X-edge face (between columns ix and ix+1), plane normal along X, placed at xMid, spans zA..zB
                float dropA = Math.abs(y00 - y10);
                float dropB = Math.abs(y01 - y11);
                if (Math.max(dropA, dropB) > DROP_EPS) {
                    float topA = Math.max(y00, y10);
                    float botA = Math.min(y00, y10);
                    float topB = Math.max(y01, y11);
                    float botB = Math.min(y01, y11);

                    // Nudge slightly toward the higher side to minimize z-fighting with world faces
                    float biasX = ((y00 + y01) > (y10 + y11)) ? -0.002f : 0.002f;
                    float xFace = xMid + biasX;

                    vc.addVertex(I, xFace, topA, zA).setUv(0, 0).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xFace, topB, zB).setUv(1, 0).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xFace, botB, zB).setUv(1, 1).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);

                    vc.addVertex(I, xFace, topA, zA).setUv(0, 0).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xFace, botB, zB).setUv(1, 1).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xFace, botA, zA).setUv(0, 1).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                }

                // Z-edge face (between rows iz and iz+1), plane normal along Z, placed at zMid, spans xA..xB
                float dropC = Math.abs(y00 - y01);
                float dropD = Math.abs(y10 - y11);
                if (Math.max(dropC, dropD) > DROP_EPS) {
                    float topA2 = Math.max(y00, y01);
                    float botA2 = Math.min(y00, y01);
                    float topB2 = Math.max(y10, y11);
                    float botB2 = Math.min(y10, y11);

                    float biasZ = ((y00 + y10) > (y01 + y11)) ? -0.002f : 0.002f;
                    float zFace = zMid + biasZ;

                    vc.addVertex(I, xA, topA2, zFace).setUv(0, 0).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xB, topB2, zFace).setUv(1, 0).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xB, botB2, zFace).setUv(1, 1).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);

                    vc.addVertex(I, xA, topA2, zFace).setUv(0, 0).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xB, botB2, zFace).setUv(1, 1).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                    vc.addVertex(I, xA, botA2, zFace).setUv(0, 1).setColor(1f,1f,1f,1f).setLight(LightTexture.FULL_BRIGHT);
                }
            }
        }*/

        // Baseline Y for vertical cylinder
        double y = hit.y + 0.02;
        // Also render a subtle vertical cylinder (ring color), fading out upwards
        int segments = 48*4;
        float yTop = (float) (y + fadeHeight);
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0) * (double) i / (double) segments;
            double a1 = (Math.PI * 2.0) * (double) (i + 1) / (double) segments;
            float cx0 = (float) (hit.x + Math.cos(a0) * radius);
            float cz0 = (float) (hit.z + Math.sin(a0) * radius);
            float cx1 = (float) (hit.x + Math.cos(a1) * radius);
            float cz1 = (float) (hit.z + Math.sin(a1) * radius);

            // Two triangles per segment (vertical quad)
            vc.addVertex(I, cx0, (float) y,    cz0).setUv(0, 0).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(I, cx1, (float) y,    cz1).setUv(1, 0).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(I, cx1, (float) yTop, cz1).setUv(1, 1).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);

            vc.addVertex(I, cx0, (float) y,    cz0).setUv(0, 0).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(I, cx1, (float) yTop, cz1).setUv(1, 1).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
            vc.addVertex(I, cx0, (float) yTop, cz0).setUv(0, 1).setColor(1f, 1f, 1f, 1f).setLight(LightTexture.FULL_BRIGHT);
        }

        buffers.endBatch(AuraRenderTypes.whistle_ground_overlay());
    }

    private static void updateIncrementalSelection(Level level, Vec3 center, float radius) {
        if (radius <= 0.05f) return;
        double r = Math.max(0.25, radius);
        // Use a vertical cylinder volume: extend upward a few blocks to catch flying entities
        AABB aabb = new AABB(center.x - r, center.y - 1.0, center.z - r, center.x + r, center.y + 4.0, center.z + r).inflate(0.5);
        List<Entity> nearby = level.getEntities((Entity) null, aabb, e -> e.isAlive());
        boolean added = false;
        double r2 = r * r;
        for (Entity e : nearby) {
            Vec3 ep = e.position();
            double dx = ep.x - center.x;
            double dz = ep.z - center.z;
            double dist2 = dx * dx + dz * dz;
            if (dist2 <= r2) {
                int id = e.getId();
                if (!selectedIds.contains(id)) {
                    selectedIds.add(id);
                    added = true;
                }
            }
        }
        if (added) sendSelectionToServer();
    }

    private static void sendSelectionToServer() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        if (selectedIds.isEmpty()) return;
        int[] ids = selectedIds.toIntArray();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()), mc.level.registryAccess());
        WhistleSelectionC2S.STREAM_CODEC.encode(buf, new WhistleSelectionC2S(ids));
        NetworkManager.sendToServer(WhistleSelectionC2S.TYPE.id(), buf);
    }

    public static void sendCancelOrdersToServer() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()), mc.level.registryAccess());
        com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S.STREAM_CODEC.encode(buf, new com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S());
        NetworkManager.sendToServer(com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S.TYPE.id(), buf);
    }

    public static void sendPosOrderAtPlayer(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType type, float radius, boolean persistent) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        net.minecraft.core.BlockPos pos = mc.player.blockPosition();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()), mc.level.registryAccess());
        com.jayemceekay.shadowedhearts.network.payload.IssuePosOrderC2S.STREAM_CODEC.encode(buf, new com.jayemceekay.shadowedhearts.network.payload.IssuePosOrderC2S(type, pos, radius, persistent));
        NetworkManager.sendToServer(com.jayemceekay.shadowedhearts.network.payload.IssuePosOrderC2S.TYPE.id(), buf);
    }

    private static void growRadius() {
        currentRadius = Math.min(MAX_RADIUS, currentRadius + GROW_PER_TICK);
    }

    private static void clear() {
        active = false;
        startTick = 0L;
        currentRadius = 0.0f;
        samples.clear();
        selectedIds.clear();
    }

    /** Returns true if the client-side selection brush is active and the entity is currently selected. */
    public static boolean isSelected(int entityId) {
        return active && selectedIds.contains(entityId);
    }

    /** Finishes selection. If forcedQuick, select single under crosshair; otherwise we've been adding incrementally. */
    private static void finishAndSend(boolean forcedQuick) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) { clear(); return; }
        LocalPlayer player = mc.player;
        long heldTicks = mc.level.getGameTime() - startTick;
        boolean quick = forcedQuick || heldTicks <= QUICK_TAP_TICKS;

        if (quick) {
            Entity e = currentHitEntity(player, 10.0);
            if (e != null) {
                selectedIds.clear();
                selectedIds.add(e.getId());
                sendSelectionToServer();
            }
        } else {
            // Non-quick: we have been sending incrementally; send one last snapshot for safety
            sendSelectionToServer();
        }
        clear();
    }

    private static void findEntitiesInBrush(Level level, IntList out) {
        if (samples.isEmpty()) return;
        // Build a bounding box around all samples inflated by MAX_RADIUS
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 v : samples) {
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(MAX_RADIUS + 0.5);
        List<Entity> nearby = level.getEntities((Entity) null, aabb, e -> e.isAlive());
        if (nearby.isEmpty()) return;
        // Select entities if within disk of any sample (horizontal distance check)
        for (Entity e : nearby) {
            Vec3 ep = e.position();
            for (Vec3 v : samples) {
                double dx = ep.x - v.x;
                double dz = ep.z - v.z;
                double dist2 = dx * dx + dz * dz;
                if (dist2 <= (double) (MAX_RADIUS * MAX_RADIUS)) {
                    out.add(e.getId());
                    break;
                }
            }
        }
    }

    private static Vec3 currentHitPos(LocalPlayer player, double dist) {
        HitResult hr = pick(player, dist, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
        if (hr == null) return null;
        if (hr.getType() == HitResult.Type.BLOCK) {
            return hr.getLocation();
        } else if (hr.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hr).getLocation();
        }
        return null;
    }

    private static Entity currentHitEntity(LocalPlayer player, double dist) {
        HitResult hr = pick(player, dist, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE);
        if (hr instanceof EntityHitResult ehr) {
            return ehr.getEntity();
        }
        return null;
    }

    private static HitResult pick(LocalPlayer player, double dist, ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(dist));
        // Block clip first
        HitResult block = player.level().clip(new ClipContext(eye, end, blockMode, fluidMode, player));
        double best = block != null ? block.getLocation().distanceToSqr(eye) : dist * dist;
        // Entity pick within a small expansion box along the ray
        AABB box = player.getBoundingBox().expandTowards(look.scale(dist)).inflate(1.0);
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, eye, end, box, e -> e.isPickable() && e.isAlive() && !e.is(player), best);
        if (ehr != null) {
            return ehr;
        }
        return block;
    }

    /**
     * Sample the nearest terrain surface Y near a base Y at an XZ location.
     * Performs two short vertical clips: from above down and from below up,
     * then picks whichever intersection is closer to baseY. If none, returns baseY.
     */
    private static double sampleNearestSurfaceY(Level level, double worldX, double baseY, double worldZ, double searchUp, double searchDown) {
        var entity = Minecraft.getInstance().player; // safe for client-side sampling
        // Cast from above down to base
        Vec3 fromAbove = new Vec3(worldX, baseY + Math.max(0.0, searchUp), worldZ);
        Vec3 base = new Vec3(worldX, baseY, worldZ);
        HitResult hrAbove = level.clip(new ClipContext(fromAbove, base, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Double yAbove = (hrAbove != null && hrAbove.getType() == HitResult.Type.BLOCK) ? hrAbove.getLocation().y : null;

        // Cast from below up to base
        Vec3 fromBelow = new Vec3(worldX, baseY - Math.max(0.0, searchDown), worldZ);
        HitResult hrBelow = level.clip(new ClipContext(fromBelow, base, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Double yBelow = (hrBelow != null && hrBelow.getType() == HitResult.Type.BLOCK) ? hrBelow.getLocation().y : null;

        double bestY = baseY;
        double bestDist = Double.POSITIVE_INFINITY;
        if (yAbove != null) {
            double d = Math.abs(baseY - yAbove);
            if (d < bestDist) { bestDist = d; bestY = yAbove; }
        }
        if (yBelow != null) {
            double d = Math.abs(baseY - yBelow);
            if (d < bestDist) { bestDist = d; bestY = yBelow; }
        }
        return bestY;
    }

    private static void spawnRingParticles(Level level, Vec3 center, float radius) {
        if (radius <= 0.05f) radius = 0.5f;
        int count = Math.max(10, (int) (radius * 22));
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0) * (i / (double) count);
            double x = center.x + Math.cos(ang) * radius;
            double z = center.z + Math.sin(ang) * radius;
            double y = center.y + 0.05;
            level.addParticle(GREEN_DUST, x, y, z, 0, 0.0, 0);
        }
        // fill a bit inside
        int fill = (int) (radius * 8);
        for (int i = 0; i < fill; i++) {
            double r = radius * (0.25 + (i / (double) fill) * 0.75);
            double ang = (Math.PI * 2.0) * level.random.nextDouble();
            double x = center.x + Math.cos(ang) * r;
            double z = center.z + Math.sin(ang) * r;
            double y = center.y + 0.02;
            level.addParticle(GREEN_DUST, x, y, z, 0, 0.0, 0);
        }
    }

    // Exposed utility to be called by platform hooks when RMB released unexpectedly
    public static void cancelAndSendQuickIfAny() {
        if (active) finishAndSend(true);
    }

    // === HUD (2D) overlay for the order wheel (tween menu) ===
    public static void onHudRender(net.minecraft.client.gui.GuiGraphics gfx, float partialTick) {
        // Delegate to separated UI classes
        if (com.jayemceekay.shadowedhearts.config.ModConfig.get().tossOrderBarUI) {
            TossOrderBarUI.onHudRender(gfx, partialTick);
        } else {
            TossOrderRadialWheel.onHudRender(gfx, partialTick);
        }
    }

    public static boolean isHoldingWhistle() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        var p = mc.player;
        return p.getMainHandItem().is(ModItems.TRAINERS_WHISTLE.get()) || p.getOffhandItem().is(ModItems.TRAINERS_WHISTLE.get());
    }

    public static boolean isWheelActive() { return com.jayemceekay.shadowedhearts.config.ModConfig.get().tossOrderBarUI ? TossOrderBarUI.isActive() : TossOrderRadialWheel.isActive(); }

    /** Manage wheel state each tick (delegated to TossOrderWheel). */
    private static void updateWheelMouseLock(Minecraft mc) {
        //com.jayemceekay.shadowedhearts.poketoss.client.TossOrderWheel.onTick();
    }
}
