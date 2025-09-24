package com.jayemceekay.shadowedhearts.poketoss;

/**
 * Minimal set of tactical order categories for the initial PokeTOSS framework.
 * This will expand as systems (aggro, AI jobs, pathing, etc.) are implemented.
 * Context: This is a Minecraft Cobblemon mod; all ‘orders’ are gameplay AI directives.
 */
public enum TacticalOrderType {
    CANCEL,

    // Positioning
    FOLLOW, // follow the commander/anchor
    HOLD_POSITION, // hold position
    MOVE_TO, // travel to the target position

    // Combat
    ENGAGE_TARGET, // focus attacks on the target entity (in-game combat)
    GUARD_TARGET, // stay near and defend the anchor entity
    DISENGAGE, // cancel combat behaviors and clear current orders

    // Support/Utility
    SCOUT, // explore ahead briefly

    // Resource Collection (gameplay chores)
    FORAGE_PLANTS, // pick berries, herbs, mushrooms in area
    MINE_ORES, // break mineable stone/ore blocks using allowed field skills
    FISH, // perform fishing/surf hunting behaviors
    WOODCUTTING, // fell/log trees
    HARVEST_CROPS, // harvest mature crops
    LOOT_ITEMS, // pick up dropped items in area
    AUTO_COLLECT // continuously grab nearby items and bring to commander
}
