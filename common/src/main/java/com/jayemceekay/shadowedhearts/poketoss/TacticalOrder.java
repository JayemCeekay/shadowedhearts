package com.jayemceekay.shadowedhearts.poketoss;

import net.minecraft.core.BlockPos;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a concrete tactical order issued to a single receiver.
 */
public final class TacticalOrder {
    public final TacticalOrderType type;
    public final Optional<UUID> targetEntity;
    public final Optional<BlockPos> targetPos;
    public final float radius;
    public final boolean persistent; // whether the order should be kept across simple interruptions
    public final long issuedAtEpochSeconds;

    private TacticalOrder(
            TacticalOrderType type,
            Optional<UUID> targetEntity,
            Optional<BlockPos> targetPos,
            float radius,
            boolean persistent,
            long issuedAtEpochSeconds
    ) {
        this.type = type;
        this.targetEntity = targetEntity;
        this.targetPos = targetPos;
        this.radius = radius;
        this.persistent = persistent;
        this.issuedAtEpochSeconds = issuedAtEpochSeconds;
    }

    public static TacticalOrder follow(float radius, boolean persistent) {
        return new TacticalOrder(TacticalOrderType.FOLLOW, Optional.empty(), Optional.empty(), radius, persistent, now());
    }

    public static TacticalOrder holdAt(BlockPos pos, float radius, boolean persistent) {
        return new TacticalOrder(TacticalOrderType.HOLD_POSITION, Optional.empty(), Optional.of(pos), radius, persistent, now());
    }

    public static TacticalOrder moveTo(BlockPos pos, float radius) {
        return new TacticalOrder(TacticalOrderType.MOVE_TO, Optional.empty(), Optional.of(pos), radius, false, now());
    }

    public static TacticalOrder attack(UUID target) {
        return new TacticalOrder(TacticalOrderType.ATTACK_TARGET, Optional.of(target), Optional.empty(), 0f, false, now());
    }

    public static TacticalOrder guard(UUID target, float radius, boolean persistent) {
        return new TacticalOrder(TacticalOrderType.GUARD_TARGET, Optional.of(target), Optional.empty(), radius, persistent, now());
    }

    private static long now() { return Instant.now().getEpochSecond(); }
}
