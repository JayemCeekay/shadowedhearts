package com.jayemceekay.shadowedhearts.common.tracking;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Manages per-player trail sessions and tier-aware hunt generation.
 */
public final class TrailManager {
    private static final Map<UUID, TrailSession> SESSIONS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private TrailManager() {}

    public static Optional<TrailSession> get(UUID playerId) {
        return Optional.ofNullable(SESSIONS.get(playerId));
    }

    /**
     * Start or reset a trail session using the legacy step count (backward compatible).
     * Defaults to FAINT tier with a random seed.
     */
    public static TrailSession startOrReset(ServerPlayer player, int steps) {
        return startOrReset(player, ShadowSignalTier.FAINT, RANDOM.nextLong());
    }

    /**
     * Start or reset a trail session with a specific tier and seed.
     * The hunt layout is generated from the tier, and trail nodes are placed in the world
     * with event types assigned from the layout.
     */
    public static TrailSession startOrReset(ServerPlayer player, ShadowSignalTier tier, long seed) {
        return startOrReset(player, tier, seed, ShadowSpeciesTrait.NEUTRAL);
    }

    /**
     * Start or reset a trail session with a specific tier, seed, and species trait.
     * The species trait influences hunt layout event selection.
     */
    public static TrailSession startOrReset(ServerPlayer player, ShadowSignalTier tier, long seed, ShadowSpeciesTrait trait) {
        TrailSession session = new TrailSession(player.getUUID());
        session.setSpeciesTrait(trait);
        session.initHunt(tier, seed);

        HuntLayout layout = session.getHuntLayout();
        List<TrailNode> nodes = generatePath(
                (ServerLevel) player.level(),
                player.blockPosition(),
                layout
        );
        session.setNodes(nodes);
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    /**
     * Generate trail nodes in the world based on a HuntLayout.
     * Each node gets the event type from the layout and a world position.
     */
    private static List<TrailNode> generatePath(ServerLevel level, BlockPos start, HuntLayout layout) {
        int totalNodes = layout.getTotalNodes();
        List<TrailNode> out = new ArrayList<>(totalNodes);
        BlockPos cursor = start;

        for (int i = 0; i < totalNodes; i++) {
            // Pick a direction and distance
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            int minDist = ShadowedHeartsConfigs.getInstance().getShadowConfig().trailMinNodeDistance();
            int maxDist = ShadowedHeartsConfigs.getInstance().getShadowConfig().trailMaxNodeDistance();

            // Scale distance by tier — higher tiers can have farther nodes
            float distScale = 1.0f + (layout.getTier().getTier() - 1) * 0.1f;
            int dist = (int) ((minDist + RANDOM.nextInt(Math.max(1, maxDist - minDist + 1))) * distScale);

            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            BlockPos candidate = cursor.offset(dx, 0, dz);

            // Find surface height around candidate
            BlockPos surface = findSurface(level, candidate);

            // Assign event type from the layout
            NodeEventType eventType = layout.getEventAt(i);
            out.add(new TrailNode(surface, eventType));
            cursor = surface;
        }
        return out;
    }

    private static BlockPos findSurface(Level level, BlockPos around) {
        int x = around.getX();
        int z = around.getZ();
        // Use world height query if available, else simple downward scan
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= 0) {
            // Fallback: scan downward from near world top
            y = Math.min(level.getMaxBuildHeight() - 1, around.getY() + 16);
            while (y > level.getMinBuildHeight() && level.getBlockState(new BlockPos(x, y, z)).isAir()) y--;
        }
        // Place node slightly above ground for visibility
        return new BlockPos(x, Math.max(y + 1, level.getMinBuildHeight() + 1), z);
    }
}
