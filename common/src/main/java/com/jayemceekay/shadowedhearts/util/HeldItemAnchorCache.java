package com.jayemceekay.shadowedhearts.util;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

public final class HeldItemAnchorCache {
    public static int frameId = 0;

    public record Anchor(Vec3 worldPos, float approxScale, int frameId, long lastSeenMs) {}

    private static final Object2ObjectMap<UUID, Anchor> CACHE = new Object2ObjectOpenHashMap<>();
    private static volatile Object currentLevelIdentity = null; // identity check; do not deref
    private static long lastPruneMs = 0L;
    private static final int MAX_SIZE = 128;           // hard cap to avoid unbounded growth
    private static final long STALE_MS = 10_000L;      // drop entries not touched for 10s

    public static void capture(Player p, PoseStack ps, int frameId) {
        // Clear on world change (client swaps worlds/dimensions)
        var level = Minecraft.getInstance().level;
        if (level != currentLevelIdentity) {
            CACHE.clear();
            currentLevelIdentity = level;
        }

        long nowMs = System.currentTimeMillis();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        // item origin in camera-relative coordinates
        Matrix4f m = new Matrix4f(ps.last().pose());
        Vector3f o = m.transformPosition(new Vector3f(0, 0, 0));

        Vec3 world = camPos.add(o.x, o.y, o.z);
        float scale = extractApproxUniformScale(m);

        CACHE.put(p.getUUID(), new Anchor(world, scale, frameId, nowMs));
        pruneIfNeeded(nowMs);
    }

    public static @Nullable Anchor get(Player p, int frameId) {
        // Return the most recently captured anchor for this player.
        // Avoid strict frame-id matching because different call sites may compute
        // slightly different frame ids (ns vs tick), causing sporadic misses.
        // We rely on frequent captures around the held-item render to keep this fresh.
        long nowMs = System.currentTimeMillis();
        pruneIfNeeded(nowMs);
        Anchor a = CACHE.get(p.getUUID());
        if (a == null) return null;
        // refresh lastSeen if we happen to read it (cheap write)
        CACHE.put(p.getUUID(), new Anchor(a.worldPos(), a.approxScale(), a.frameId(), nowMs));
        return a;
    }

    private static float extractApproxUniformScale(Matrix4f m) {
        Vector3f x = m.transformDirection(new Vector3f(1, 0, 0));
        Vector3f y = m.transformDirection(new Vector3f(0, 1, 0));
        Vector3f z = m.transformDirection(new Vector3f(0, 0, 1));
        return (x.length() + y.length() + z.length()) / 3f;
    }

    private static void pruneIfNeeded(long nowMs) {
        // Throttle pruning work
        if (nowMs - lastPruneMs < 1000L && CACHE.size() <= MAX_SIZE) return;
        lastPruneMs = nowMs;

        var level = Minecraft.getInstance().level;
        if (level == null) {
            CACHE.clear();
            return;
        }

        // Build current player set for quick membership checks
        java.util.HashSet<UUID> livePlayers = new java.util.HashSet<>();
        for (var pl : level.players()) livePlayers.add(pl.getUUID());

        // Remove stale or non-present players
        var it = CACHE.object2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            Anchor a = e.getValue();
            if (!livePlayers.contains(id) || (nowMs - a.lastSeenMs()) > STALE_MS) {
                it.remove();
            }
        }

        // Enforce hard cap (remove oldest first)
        if (CACHE.size() > MAX_SIZE) {
            java.util.ArrayList<AnchorWithId> list = new java.util.ArrayList<>(CACHE.size());
            for (var e : CACHE.object2ObjectEntrySet()) {
                list.add(new AnchorWithId(e.getKey(), e.getValue()));
            }
            list.sort(java.util.Comparator.comparingLong(x -> x.anchor.lastSeenMs()));
            int toRemove = CACHE.size() - MAX_SIZE;
            for (int i = 0; i < toRemove; i++) {
                CACHE.remove(list.get(i).id);
            }
        }
    }

    private record AnchorWithId(UUID id, Anchor anchor) {}
}
