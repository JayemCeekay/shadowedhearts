package com.jayemceekay.shadowedhearts.poketoss.client;

import com.jayemceekay.shadowedhearts.network.payload.IssuePosOrderC2S;
import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side controller for selecting a ground position for a positional order (MOVE_TO / HOLD_POSITION).
 * When active, waits for right-click to confirm and sends a C2S packet with the chosen BlockPos.
 */
public final class PositionSelectionClient {
    private PositionSelectionClient() {}

    private static boolean active = false;
    private static TacticalOrderType pendingOrderType = TacticalOrderType.MOVE_TO;
    private static boolean wasUseDown = false;

    public static void begin(TacticalOrderType type) {
        if (type != TacticalOrderType.MOVE_TO && type != TacticalOrderType.HOLD_POSITION) {
            return;
        }
        pendingOrderType = type;
        active = true;
        wasUseDown = false;
    }

    public static void cancel() {
        active = false;
        wasUseDown = false;
    }

    public static boolean isActive() { return active; }

    /** Call from client tick (END). */
    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (!active) { wasUseDown = false; return; }
        if (mc.player == null || mc.level == null) { cancel(); return; }

        // If the order wheel opens, cancel to avoid conflicts
        if (WhistleSelectionClient.isWheelActive()) {
            cancel();
            return;
        }

        boolean useDown = mc.options.keyUse.isDown();
        if (useDown && !wasUseDown) {
            BlockPos pos = currentHitBlockPos(mc.player, 128.0);
            if (pos != null) {
                // Default parameters
                float radius = pendingOrderType == TacticalOrderType.MOVE_TO ? 2.0f : 2.5f;
                boolean persistent = pendingOrderType == TacticalOrderType.HOLD_POSITION;
                sendIssueOrder(pendingOrderType, pos, radius, persistent);
                cancel();
            }
        }
        wasUseDown = useDown;
    }

    private static BlockPos currentHitBlockPos(LocalPlayer player, double dist) {
        HitResult hr = pick(player, dist, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
        if (hr == null) return null;
        Vec3 p = hr.getLocation();
        return new BlockPos((int)Math.floor(p.x), (int)Math.floor(p.y), (int)Math.floor(p.z));
    }

    private static HitResult pick(LocalPlayer player, double dist, ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(dist));
        // Block-only clip for ground position selection
        return player.level().clip(new ClipContext(eye, end, blockMode, fluidMode, player));
    }

    private static void sendIssueOrder(TacticalOrderType type, BlockPos pos, float radius, boolean persistent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()), mc.level.registryAccess());
        IssuePosOrderC2S.STREAM_CODEC.encode(buf, new IssuePosOrderC2S(type, pos, radius, persistent));
        NetworkManager.sendToServer(IssuePosOrderC2S.TYPE.id(), buf);
    }
}
