package com.jayemceekay.shadowedhearts.snag;

/**
 * Static config defaults for Snag mechanics. Future: load from JSON/datapack.
 */
public final class SnagConfig {
    private SnagConfig() {}

    public static boolean ENABLED = true;
    public static boolean OVERWORLD_ALLOWED = false;
    public static boolean NPC_ONLY = true;
    public static boolean PVP_ALLOWED = false;

    public static double BALL_GREAT = 1.5;
    public static double BALL_ULTRA = 2.0;
    public static double BALL_SNAG = 2.5;

    public static double DEVICE_ARMED = 1.15;
    public static double DEVICE_CHARGED = 1.25; // reserved for future energy tiers

    public static int ENERGY_PER_ATTEMPT = 50;
    public static int TOGGLE_COOLDOWN_TICKS = 20;
}
