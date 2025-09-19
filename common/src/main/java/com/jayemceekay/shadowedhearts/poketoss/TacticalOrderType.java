package com.jayemceekay.shadowedhearts.poketoss;

/**
 * Minimal set of tactical order categories for the initial PokeTOSS framework.
 * This will expand as systems (aggro, AI jobs, pathing, etc.) are implemented.
 */
public enum TacticalOrderType {
    // Positioning
    FOLLOW,
    HOLD_POSITION,
    MOVE_TO,
    REGROUP,

    // Combat
    ATTACK_TARGET,
    GUARD_TARGET,
    DISENGAGE,

    // Support/Utility (stubs for future wiring)
    ASSIST_ALLY,
    INTERACT,
    SCOUT
}
