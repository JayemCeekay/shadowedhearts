package com.jayemceekay.shadowedhearts.poketoss.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.network.payload.IssueTargetOrderC2S;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Client-side controller for the post-order target selection mode.
 * When active, highlights the Pokémon under the crosshair in red and
 * sends a C2S packet on right-click to issue the target order.
 */
@SuppressWarnings("removal")
public final class TargetSelectionClient {
    private TargetSelectionClient() {}

    public static final float HIL_R = 1.00f; // red highlight color
    public static final float HIL_G = 0.30f;
    public static final float HIL_B = 0.30f;

    private static boolean active = false;
    private static TacticalOrderType pendingOrderType = TacticalOrderType.ENGAGE_TARGET;
    private static int highlightedEntityId = -1;
    private static boolean wasUseDown = false;

    /** Begin target selection for the given order type. */
    public static void begin(TacticalOrderType type) {
        pendingOrderType = type;
        active = true;
        highlightedEntityId = -1;
        wasUseDown = false;
    }

    /** Convenience: begin an Attack Target selection. */
    public static void beginEngagement() { begin(TacticalOrderType.ENGAGE_TARGET); }

    public static void cancel() {
        active = false;
        highlightedEntityId = -1;
        wasUseDown = false;
    }

    public static boolean isActive() { return active; }

    /** Returns true if this entity is currently highlighted as a viable target. */
    public static boolean isHighlighted(int entityId) {
        return active && entityId == highlightedEntityId;
    }

    /** Call from client tick (END). */
    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (!active) { wasUseDown = false; return; }
        if (mc.player == null || mc.level == null) { cancel(); return; }
        LocalPlayer player = mc.player;

        // Update current highlighted target under crosshair
        Entity hit = currentHitEntity(player, 64.0);
        if (hit instanceof PokemonEntity) {
            highlightedEntityId = hit.getId();
        } else {
            highlightedEntityId = -1;
        }

        // Edge-detect the Use (right click) key to confirm target
        boolean useDown = mc.options.keyUse.isDown();
        if (useDown && !wasUseDown && highlightedEntityId != -1) {
            sendIssueOrder(highlightedEntityId, pendingOrderType);
            cancel();
        }
        wasUseDown = useDown;

        // If the order wheel is opened, suspend/cancel target mode to avoid conflicts
        if (WhistleSelectionClient.isWheelActive()) {
            cancel();
        }
    }

    private static void sendIssueOrder(int targetEntityId, TacticalOrderType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()), mc.level.registryAccess());
        IssueTargetOrderC2S.STREAM_CODEC.encode(buf, new IssueTargetOrderC2S(type, targetEntityId));
        NetworkManager.sendToServer(IssueTargetOrderC2S.TYPE.id(), buf);
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
        // If we hit a non-collidable (walk-through) block like grass/flowers, ignore it for picking
        if (block instanceof BlockHitResult bhr) {
            var pos = bhr.getBlockPos();
            var state = player.level().getBlockState(pos);
            var shape = state.getCollisionShape(player.level(), pos, CollisionContext.of(player));
            if (shape.isEmpty()) {
                block = null; // treat as no blocking hit
            }
        }
        double best = block != null ? block.getLocation().distanceToSqr(eye) : dist * dist;
        // Entity pick within a small expansion box along the ray
        AABB box = player.getBoundingBox().expandTowards(look.scale(dist)).inflate(1.0);
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(player, eye, end, box, e -> e.isPickable() && e.isAlive() && !e.is(player), best);
        if (ehr != null) {
            return ehr;
        }
        return block;
    }
}
