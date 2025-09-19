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
    private static WheelAction pendingAction = WheelAction.NONE;
    // Debounce flag to prevent synthetic re-presses from toggling submenus back immediately
    private static boolean suppressUntilMouseUp = false;

    private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.6f, 1.0f, 0.6f), 1.25f);

    private enum WheelAction {
        NONE,
        COMBAT_ATTACK,
        COMBAT_GUARD,
        POSITION_MOVE_TO,
        POSITION_HOLD,
        UTILITY_REGROUP_TO_ME,
        CONTEXT_HOLD_AT_ME,
        CANCEL_ALL
    }

    public static void init() {
        // no common event bus here; platform-specific clients will call onTick() and onRender() from their hooks
    }

    /** Called by TrainersWhistleItem on client when use begins. */
    public static void begin() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
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

        // Handle mouse grab/release for the order wheel regardless of selection brush state
        updateWheelMouseLock(mc);

        // When the wheel opens, suppress any current left-click press to avoid spurious toggles
        if (wheelActive && !prevWheelActive) {
            suppressUntilMouseUp = true;
        }

        // When the wheel closes (key released), execute any pending action
        if (!wheelActive && prevWheelActive) {
            if (pendingAction != WheelAction.NONE) {
                switch (pendingAction) {
                    case COMBAT_ATTACK -> com.jayemceekay.shadowedhearts.poketoss.client.TargetSelectionClient.beginAttack();
                    case COMBAT_GUARD -> com.jayemceekay.shadowedhearts.poketoss.client.TargetSelectionClient.begin(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.GUARD_TARGET);
                    case POSITION_MOVE_TO -> com.jayemceekay.shadowedhearts.poketoss.client.PositionSelectionClient.begin(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.MOVE_TO);
                    case POSITION_HOLD -> com.jayemceekay.shadowedhearts.poketoss.client.PositionSelectionClient.begin(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.HOLD_POSITION);
                    case UTILITY_REGROUP_TO_ME -> sendPosOrderAtPlayer(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.MOVE_TO, 2.0f, false);
                    case CONTEXT_HOLD_AT_ME -> sendPosOrderAtPlayer(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType.HOLD_POSITION, 2.5f, true);
                    case CANCEL_ALL -> sendCancelOrdersToServer();
                    default -> {}
                }
                pendingAction = WheelAction.NONE;
            }
            // Close any open submenus
            combatSubOpen = false;
            combatTween = 0f;
            posSubOpen = false; posTween = 0f;
            utilSubOpen = false; utilTween = 0f;
            ctxSubOpen = false; ctxTween = 0f;
            // Lift any click suppression when the wheel closes
            suppressUntilMouseUp = false;
        }
        prevWheelActive = wheelActive;

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

    private static void sendCancelOrdersToServer() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()), mc.level.registryAccess());
        com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S.STREAM_CODEC.encode(buf, new com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S());
        NetworkManager.sendToServer(com.jayemceekay.shadowedhearts.network.payload.CancelOrdersC2S.TYPE.id(), buf);
    }

    private static void sendPosOrderAtPlayer(com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType type, float radius, boolean persistent) {
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
        if (!wheelActive) { wasLeftDown = false; return; }
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) { wasLeftDown = false; return; }
        if (mc.screen != null) { wasLeftDown = false; return; }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int baseDim = Math.min(w, h);
        int cx = w / 2;
        int cy = h / 2;

        // Convert absolute mouse position to GUI-scaled coordinates using the actual window size (not screen size)
        double mx = mc.mouseHandler.xpos() * (double) w / (double) mc.getWindow().getWidth();
        double my = mc.mouseHandler.ypos() * (double) h / (double) mc.getWindow().getHeight();
        int mouseX = (int) Math.round(mx);
        int mouseY = (int) Math.round(my);

        // Dim the background slightly
        gfx.fill(0, 0, w, h, 0x66000000);

        // Layout constants scaled to window size
        int centerSize = Math.max(48, (int)Math.round(baseDim * 0.0833));
        int gap = Math.max(8, (int)Math.round(baseDim * 0.0203));
        int catHScaled = Math.max(24, (int)Math.round(baseDim * 0.0390));
        int catWScaled = Math.max(140, (int)Math.round(baseDim * 0.2040));
        // Constrain category sizes so they always fit around the center
        int maxCatW = Math.max(50, (w - centerSize - 2 * gap) / 2);
        int maxCatH = Math.max(20, (h - centerSize - 2 * gap) / 2);
        int catW = Math.min(catWScaled, maxCatW);
        int catH = Math.min(catHScaled, maxCatH);

        // Rects
        int cX0 = cx - centerSize / 2, cY0 = cy - centerSize / 2, cX1 = cX0 + centerSize, cY1 = cY0 + centerSize;
        int topX0 = cx - catW / 2, topY0 = cy - (centerSize / 2 + gap + catH), topX1 = topX0 + catW, topY1 = topY0 + catH;
        int leftX0 = cx - (centerSize / 2 + gap + catW), leftY0 = cy - catH / 2, leftX1 = leftX0 + catW, leftY1 = leftY0 + catH;
        int rightX0 = cx + (centerSize / 2 + gap), rightY0 = cy - catH / 2, rightX1 = rightX0 + catW, rightY1 = rightY0 + catH;
        int bottomX0 = cx - catW / 2, bottomY0 = cy + (centerSize / 2 + gap), bottomX1 = bottomX0 + catW, bottomY1 = bottomY0 + catH;

        boolean hovCenter = mouseX >= cX0 && mouseX <= cX1 && mouseY >= cY0 && mouseY <= cY1;
        boolean hovTop = mouseX >= topX0 && mouseX <= topX1 && mouseY >= topY0 && mouseY <= topY1;
        boolean hovLeft = mouseX >= leftX0 && mouseX <= leftX1 && mouseY >= leftY0 && mouseY <= leftY1;
        boolean hovRight = mouseX >= rightX0 && mouseX <= rightX1 && mouseY >= rightY0 && mouseY <= rightY1;
        boolean hovBottom = mouseX >= bottomX0 && mouseX <= bottomX1 && mouseY >= bottomY0 && mouseY <= bottomY1;

        // Colors
        int base = 0xAA1E1E1E;
        int hi = 0xFFC8FFC8; // light green highlight
        int white = 0xFFFFFFFF;

        // Draw categories
        gfx.fill(cX0, cY0, cX1, cY1, hovCenter ? hi : 0xFFAA4444); // center cancel
        gfx.fill(topX0, topY0, topX1, topY1, hovTop ? hi : base);
        gfx.fill(leftX0, leftY0, leftX1, leftY1, hovLeft ? hi : base);
        gfx.fill(rightX0, rightY0, rightX1, rightY1, hovRight ? hi : base);
        gfx.fill(bottomX0, bottomY0, bottomX1, bottomY1, hovBottom ? hi : base);

        // Labels
        var font = mc.font;
        var tCancel = net.minecraft.network.chat.Component.literal("Cancel");
        var tCombat = net.minecraft.network.chat.Component.literal("Combat");
        var tPos = net.minecraft.network.chat.Component.literal("Position");
        var tUtil = net.minecraft.network.chat.Component.literal("Utility");
        var tCtx = net.minecraft.network.chat.Component.literal("Context");
        gfx.drawString(font, tCancel, cx - font.width(tCancel) / 2, cy - 4, white, false);
        gfx.drawString(font, tCombat, cx - font.width(tCombat) / 2, topY0 + 12, white, false);
        gfx.drawString(font, tPos, leftX0 + catW / 2 - font.width(tPos) / 2, leftY0 + 12, white, false);
        gfx.drawString(font, tUtil, rightX0 + catW / 2 - font.width(tUtil) / 2, rightY0 + 12, white, false);
        gfx.drawString(font, tCtx, bottomX0 + catW / 2 - font.width(tCtx) / 2, bottomY0 + 12, white, false);

        // Tween the combat submenu
        float target = combatSubOpen && hovTop ? 1.0f : (combatSubOpen ? 0.9f : 0.0f);
        if (combatSubOpen) target = 1.0f;
        if (!combatSubOpen && !hovTop) target = 0.0f;
        combatTween += (target - combatTween) * 0.35f;

        // Submenu items above the top category (scale-aware)
        int subCount = 3;
        int spacing = Math.max(6, (int)Math.round(baseDim * 0.0093));
        int minSubW = Math.max(50, (int)Math.round(baseDim * 0.0650));
        int maxSubWAllowed = Math.max(20, (w - 8 - spacing * (subCount - 1)) / subCount);
        int baseSubW = Math.max(minSubW, (catW - 2 * spacing) / subCount);
        int subW = (int) (Math.min(baseSubW, maxSubWAllowed) * Math.max(0.5f, combatTween));
        int baseSubH = Math.max(22, (int)Math.round(baseDim * 0.0296));
        int subH = (int) (baseSubH * Math.max(0.5f, combatTween));
        // Clamp height to available space above the category
        int maxAbove = Math.max(4, topY0 - gap - 4);
        subH = Math.min(subH, maxAbove);
        int subY = topY0 - gap - subH;
        int firstX = cx - (subW * subCount + spacing * (subCount - 1)) / 2;

        boolean hovAtk = false, hovGrd = false, hovDsg = false;
        if (combatTween > 0.05f) {
            int x = firstX;
            // Attack
            int aX0 = x, aY0 = subY, aX1 = x + subW, aY1 = subY + subH; x += subW + spacing;
            hovAtk = mouseX >= aX0 && mouseX <= aX1 && mouseY >= aY0 && mouseY <= aY1;
            gfx.fill(aX0, aY0, aX1, aY1, hovAtk ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Attack"), (aX0 + aX1) / 2, aY0 + 10, white);
            // Guard
            int gX0 = x, gY0 = subY, gX1 = x + subW, gY1 = subY + subH; x += subW + spacing;
            hovGrd = mouseX >= gX0 && mouseX <= gX1 && mouseY >= gY0 && mouseY <= gY1;
            gfx.fill(gX0, gY0, gX1, gY1, hovGrd ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Guard"), (gX0 + gX1) / 2, gY0 + 10, white);
            // Disengage (placeholder)
            int dX0 = x, dY0 = subY, dX1 = x + subW, dY1 = subY + subH;
            hovDsg = mouseX >= dX0 && mouseX <= dX1 && mouseY >= dY0 && mouseY <= dY1;
            gfx.fill(dX0, dY0, dX1, dY1, hovDsg ? 0xFF666666 : 0xFF1E1E1E);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Disengage"), (dX0 + dX1) / 2, dY0 + 10, 0xFFAAAAAA);
        }

        // Tween and render Position submenu (left)
        float posTarget = posSubOpen && hovLeft ? 1.0f : (posSubOpen ? 0.9f : 0.0f);
        if (posSubOpen) posTarget = 1.0f;
        if (!posSubOpen && !hovLeft) posTarget = 0.0f;
        posTween += (posTarget - posTween) * 0.35f;
        boolean hovMove = false, hovHold = false;
        if (posTween > 0.05f) {
            int minPSW = Math.max(70, (int)Math.round(baseDim * 0.0740));
            int basePSW = Math.max(minPSW, catW / 2);
            int pSubW = (int)(basePSW * Math.max(0.5f, posTween));
            int basePSH = Math.max(22, (int)Math.round(baseDim * 0.0259));
            int pSubH = (int)(basePSH * Math.max(0.5f, posTween));
            // Clamp to screen edges
            int allowedPSubW = Math.max(4, leftX0 - gap - 4);
            pSubW = Math.min(pSubW, allowedPSubW);
            int maxPH = Math.max(4, Math.min(leftY0 - 4, h - (leftY0 + 6) - 4));
            pSubH = Math.min(pSubH, maxPH);

            int pX0 = leftX0 - gap - pSubW;
            int pY0a = Math.max(4, leftY0 - pSubH - 6);
            int pY0b = Math.min(h - 4 - pSubH, leftY0 + 6);
            int pX1 = pX0 + pSubW;
            int pY1a = pY0a + pSubH;
            int pY1b = pY0b + pSubH;
            // Move To
            hovMove = mouseX >= pX0 && mouseX <= pX1 && mouseY >= pY0a && mouseY <= pY1a;
            gfx.fill(pX0, pY0a, pX1, pY1a, hovMove ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Move To"), (pX0 + pX1) / 2, pY0a + 8, white);
            // Hold Position
            hovHold = mouseX >= pX0 && mouseX <= pX1 && mouseY >= pY0b && mouseY <= pY1b;
            gfx.fill(pX0, pY0b, pX1, pY1b, hovHold ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Hold Position"), (pX0 + pX1) / 2, pY0b + 8, white);
        }

        // Tween and render Utility submenu (right)
        float utilTarget = utilSubOpen && hovRight ? 1.0f : (utilSubOpen ? 0.9f : 0.0f);
        if (utilSubOpen) utilTarget = 1.0f;
        if (!utilSubOpen && !hovRight) utilTarget = 0.0f;
        utilTween += (utilTarget - utilTween) * 0.35f;
        boolean hovRegroup = false;
        if (utilTween > 0.05f) {
            int minUSW = Math.max(90, (int)Math.round(baseDim * 0.1020));
            int baseUSW = Math.max(minUSW, catW / 2);
            int uX0 = rightX1 + gap;
            int uMaxW = Math.max(4, w - 4 - uX0);
            int uSubW = (int)(Math.min(baseUSW, uMaxW) * Math.max(0.5f, utilTween));
            int baseUSH = Math.max(22, (int)Math.round(baseDim * 0.0259));
            int uSubH = (int)(baseUSH * Math.max(0.5f, utilTween));
            int uY0 = rightY0 + (catH - uSubH) / 2;
            int uX1 = uX0 + uSubW;
            int uY1 = uY0 + uSubH;
            hovRegroup = mouseX >= uX0 && mouseX <= uX1 && mouseY >= uY0 && mouseY <= uY1;
            gfx.fill(uX0, uY0, uX1, uY1, hovRegroup ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Regroup to Me"), (uX0 + uX1) / 2, uY0 + 8, white);
        }

        // Tween and render Context submenu (bottom)
        float ctxTarget = ctxSubOpen && hovBottom ? 1.0f : (ctxSubOpen ? 0.9f : 0.0f);
        if (ctxSubOpen) ctxTarget = 1.0f;
        if (!ctxSubOpen && !hovBottom) ctxTarget = 0.0f;
        ctxTween += (ctxTarget - ctxTween) * 0.35f;
        boolean hovHoldHere = false;
        if (ctxTween > 0.05f) {
            int minCSW = Math.max(90, (int)Math.round(baseDim * 0.1020));
            int baseCSW = Math.max(minCSW, catW / 2);
            int cSubW = (int)(Math.min(baseCSW, catW - 8) * Math.max(0.5f, ctxTween));
            int baseCSH = Math.max(22, (int)Math.round(baseDim * 0.0259));
            int cSubH = (int)(baseCSH * Math.max(0.5f, ctxTween));
            int ctxY0 = bottomY1 + gap;
            cSubH = Math.min(cSubH, Math.max(12, h - 4 - ctxY0));
            int ctxX0 = bottomX0 + (catW - cSubW) / 2;
            int ctxX1 = ctxX0 + cSubW;
            int ctxY1 = ctxY0 + cSubH;
            hovHoldHere = mouseX >= ctxX0 && mouseX <= ctxX1 && mouseY >= ctxY0 && mouseY <= ctxY1;
            gfx.fill(ctxX0, ctxY0, ctxX1, ctxY1, hovHoldHere ? 0xFF66AAFF : 0xFF2A2A2A);
            gfx.drawCenteredString(font, net.minecraft.network.chat.Component.literal("Hold At Me"), (ctxX0 + ctxX1) / 2, ctxY0 + 8, white);
        }

        // Draw a simple custom cursor (small quad) on top of the wheel UI
        // Scale the size slightly with window, but clamp to a reasonable range
        int curSize = Math.max(6, Math.min(12, (int)Math.round(baseDim * 0.0125)));
        int half = curSize / 2;
        int bx0 = mouseX - half - 1;
        int by0 = mouseY - half - 1;
        int bx1 = mouseX + half + 1;
        int by1 = mouseY + half + 1;
        // Black border
        gfx.fill(bx0, by0, bx1, by1, 0xFF000000);
        // White fill
        gfx.fill(mouseX - half, mouseY - half, mouseX + half, mouseY + half, 0xFFFFFFFF);

        // Click handling with debounce
        boolean leftDown = mc.mouseHandler.isLeftPressed();

        // While holding left, allow switching directly to another category (drag-switch), even without a new press edge
        if (leftDown) {
            boolean anyOpenNow = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
            if (anyOpenNow) {
                if (hovTop && !combatSubOpen) {
                    // Switch to Combat submenu; let previous submenu tween animate closed
                    combatSubOpen = true; posSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                } else if (hovLeft && !posSubOpen) {
                    // Switch to Position submenu; let previous submenu tween animate closed
                    posSubOpen = true; combatSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                } else if (hovRight && !utilSubOpen) {
                    // Switch to Utility submenu; let previous submenu tween animate closed
                    utilSubOpen = true; combatSubOpen = false; posSubOpen = false; ctxSubOpen = false;
                } else if (hovBottom && !ctxSubOpen) {
                    // Switch to Context submenu; let previous submenu tween animate closed
                    ctxSubOpen = true; combatSubOpen = false; posSubOpen = false; utilSubOpen = false;
                }
            }
        }

        if (suppressUntilMouseUp) {
            // Lift suppression once the button is released
            if (!leftDown) suppressUntilMouseUp = false;
        } else if (leftDown && !wasLeftDown) {
            if (combatSubOpen && combatTween > 0.8f) {
                if (hovAtk) { pendingAction = WheelAction.COMBAT_ATTACK; suppressUntilMouseUp = true; wheelActive = false; }
                else if (hovGrd) { pendingAction = WheelAction.COMBAT_GUARD; suppressUntilMouseUp = true; wheelActive = false; }
            } else if (posSubOpen && posTween > 0.8f) {
                if (hovMove) { pendingAction = WheelAction.POSITION_MOVE_TO; suppressUntilMouseUp = true; wheelActive = false; }
                else if (hovHold) { pendingAction = WheelAction.POSITION_HOLD; suppressUntilMouseUp = true; wheelActive = false; }
            } else if (utilSubOpen && utilTween > 0.8f) {
                if (hovRegroup) { pendingAction = WheelAction.UTILITY_REGROUP_TO_ME; suppressUntilMouseUp = true; wheelActive = false; }
            } else if (ctxSubOpen && ctxTween > 0.8f) {
                if (hovHoldHere) { pendingAction = WheelAction.CONTEXT_HOLD_AT_ME; suppressUntilMouseUp = true; wheelActive = false;}
            } else if (hovTop) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !combatSubOpen) {
                    // Switch directly to Combat submenu; let previous submenu animate closed
                    combatSubOpen = true; posSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                } else {
                    // Toggle Combat submenu (animate open/close)
                    combatSubOpen = !combatSubOpen; posSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                }
            } else if (hovLeft) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !posSubOpen) {
                    // Switch directly to Position submenu; let previous submenu animate closed
                    posSubOpen = true; combatSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                } else {
                    // Toggle Position submenu (animate open/close)
                    posSubOpen = !posSubOpen; combatSubOpen = false; utilSubOpen = false; ctxSubOpen = false;
                }
            } else if (hovRight) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !utilSubOpen) {
                    // Switch directly to Utility submenu; let previous submenu animate closed
                    utilSubOpen = true; combatSubOpen = false; posSubOpen = false; ctxSubOpen = false;
                } else {
                    // Toggle Utility submenu (animate open/close)
                    utilSubOpen = !utilSubOpen; combatSubOpen = false; posSubOpen = false; ctxSubOpen = false;
                }
            } else if (hovBottom) {
                boolean anyOpen = combatSubOpen || posSubOpen || utilSubOpen || ctxSubOpen;
                if (anyOpen && !ctxSubOpen) {
                    // Switch directly to Context submenu; let previous submenu animate closed
                    ctxSubOpen = true; combatSubOpen = false; posSubOpen = false; utilSubOpen = false;
                } else {
                    // Toggle Context submenu (animate open/close)
                    ctxSubOpen = !ctxSubOpen; combatSubOpen = false; posSubOpen = false; utilSubOpen = false;
                }
            } else if (hovCenter) {
                pendingAction = WheelAction.CANCEL_ALL;
                suppressUntilMouseUp = true;
                wheelActive = false;
            }
        }
        wasLeftDown = leftDown;
    }

    private static boolean isHoldingWhistle() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        var p = mc.player;
        return p.getMainHandItem().is(ModItems.TRAINERS_WHISTLE.get()) || p.getOffhandItem().is(ModItems.TRAINERS_WHISTLE.get());
    }

    public static boolean isWheelActive() { return wheelActive; }

    /** Manage mouse capture while the order wheel is active. Shows cursor and locks camera when active. */
    private static void updateWheelMouseLock(Minecraft mc) {
        // Toggle the wheel on key press instead of hold
        boolean pressed = com.jayemceekay.shadowedhearts.client.ModKeybinds.consumeOrderWheelPress();
        if (pressed) {
            if (isHoldingWhistle()) {
                wheelActive = !wheelActive;
                // When opening the wheel, recenter the mouse to the middle of the window so
                // our tween menu starts from a consistent cursor position.
                if (wheelActive) {
                    try {
                        int winW = mc.getWindow().getWidth();
                        int winH = mc.getWindow().getHeight();
                        double cx = winW / 2.0;
                        double cy = winH / 2.0;
                        // Move OS cursor
                        long handle = mc.getWindow().getWindow();
                        org.lwjgl.glfw.GLFW.glfwSetCursorPos(handle, cx, cy);
                        // Also update MouseHandler's internal fields via accessor so onHudRender sees it immediately
                        ((com.jayemceekay.shadowedhearts.mixin.MouseHandlerAccessor)(Object)mc.mouseHandler).shadowedhearts$setXpos(cx);
                        ((com.jayemceekay.shadowedhearts.mixin.MouseHandlerAccessor)(Object)mc.mouseHandler).shadowedhearts$setYpos(cy);
                    } catch (Throwable ignored) {}
                }
            } else {
                // Ignore presses when not holding the whistle; ensure closed
                wheelActive = false;
            }
        }
        // Auto-close if the player is no longer holding the whistle
        if (wheelActive && !isHoldingWhistle()) {
            wheelActive = false;
        }
        // Close the wheel if any GUI screen opens (ESC/pause menu, inventory, etc.)
        if (wheelActive && mc.screen != null) {
            wheelActive = false;
        }
    }
}
