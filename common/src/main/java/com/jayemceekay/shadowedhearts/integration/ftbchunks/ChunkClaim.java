package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import java.util.UUID;

/** Immutable result which keeps unavailable protection distinct from unclaimed terrain. */
public final class ChunkClaim {
    private enum State { ABSENT, UNCLAIMED, CLAIMED, UNAVAILABLE }

    private static final ChunkClaim ABSENT = new ChunkClaim(State.ABSENT, null);
    private static final ChunkClaim UNCLAIMED = new ChunkClaim(State.UNCLAIMED, null);
    private static final ChunkClaim UNAVAILABLE = new ChunkClaim(State.UNAVAILABLE, null);

    private final State state;
    private final UUID teamId;

    private ChunkClaim(State state, UUID teamId) {
        this.state = state;
        this.teamId = teamId;
    }

    public static ChunkClaim absent() { return ABSENT; }
    public static ChunkClaim unclaimed() { return UNCLAIMED; }
    public static ChunkClaim unavailable() { return UNAVAILABLE; }

    /** Missing ownership information must never authorize sharing or terrain changes. */
    public static ChunkClaim claimed(UUID teamId) {
        return teamId == null ? unavailable() : new ChunkClaim(State.CLAIMED, teamId);
    }

    public boolean isUnavailable() { return state == State.UNAVAILABLE; }
    public boolean isAbsent() { return state == State.ABSENT; }
    public boolean isClaimed() { return state == State.CLAIMED; }

    public boolean canShareEnergyWith(ChunkClaim other) {
        if (other == null || state != other.state) return false;
        return switch (state) {
            case ABSENT, UNCLAIMED -> true;
            case CLAIMED -> teamId.equals(other.teamId);
            case UNAVAILABLE -> false;
        };
    }
}
