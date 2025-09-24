package com.jayemceekay.shadowedhearts.poketoss.client;

import com.jayemceekay.shadowedhearts.poketoss.TacticalOrderType;

/**
 * Common, modularized pieces shared by TossOrderWheel variants.
 * - Canonical labels for categories and orders
 * - WheelAction enum
 * - Centralized action dispatch when a wheel closes
 */
public final class TossOrderCommon {
    private TossOrderCommon() {}

    // Canonical label sets
    public static final String[] CATEGORY_LABELS = new String[] { "Combat", "Position", "Utility", "Gathering", "Cancel" };
    public static final String[] CANCEL_LABELS   = new String[] { "Cancel All" };

    public static final String[] COMBAT_LABELS   = new String[] { "Attack Target", "Guard Target", "Suppress Area", "Defend Objective", "Retreat"};
    public static final String[] POSITION_LABELS = new String[] { "Move To", "Stay", "Follow", "Patrol"};
    public static final String[] UTILITY_LABELS  = new String[] { "Assist", "Interact", "Scout", "Rest", "Transport", "Illuminate"};
    public static final String[] GATHERING_LABELS = new String[] { "Mine Ores", "Harvest Crops", "Go Fishing", "Chop Wood", "Gather Loot"};

    /**
     * Supported wheel actions. Keep stable across UI variants.
     */
    public enum WheelAction {
        NONE,
        COMBAT_ENGAGE,
        COMBAT_GUARD,
        POSITION_MOVE_TO,
        POSITION_HOLD,
        UTILITY_REGROUP_TO_ME,
        CONTEXT_HOLD_AT_ME,
        CANCEL_ALL
    }

    /**
     * Execute the chosen action when the wheel confirms/finishes.
     * This centralizes behavior so both UI variants stay in sync.
     */
    public static void dispatch(WheelAction action) {
        if (action == null || action == WheelAction.NONE) return;
        switch (action) {
            case COMBAT_ENGAGE -> TargetSelectionClient.beginEngagement();
            case COMBAT_GUARD -> TargetSelectionClient.begin(TacticalOrderType.GUARD_TARGET);
            case POSITION_MOVE_TO -> PositionSelectionClient.begin(TacticalOrderType.MOVE_TO);
            case POSITION_HOLD -> PositionSelectionClient.begin(TacticalOrderType.HOLD_POSITION);
            case UTILITY_REGROUP_TO_ME -> WhistleSelectionClient.sendPosOrderAtPlayer(TacticalOrderType.MOVE_TO, 2.0f, false);
            case CONTEXT_HOLD_AT_ME -> WhistleSelectionClient.sendPosOrderAtPlayer(TacticalOrderType.HOLD_POSITION, 2.5f, true);
            case CANCEL_ALL -> WhistleSelectionClient.sendCancelOrdersToServer();
            default -> {}
        }
    }

    /** Utility: wrap an index into [0, size). */
    public static int wrap(int idx, int size) {
        if (size <= 0) return 0;
        int m = idx % size;
        return m < 0 ? m + size : m;
    }
}
