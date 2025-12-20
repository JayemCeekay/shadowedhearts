package com.jayemceekay.shadowedhearts.snag;


public final class SnagConfig {
    private SnagConfig() {}

    public static boolean ENABLED = true;
    public static boolean NPC_ONLY = true;
    public static boolean PVP_ALLOWED = false;

    public static double DEVICE_ARMED = 1.15;

    public static int ENERGY_PER_ATTEMPT = 50;
    public static int TOGGLE_COOLDOWN_TICKS = 20;

    // Victory recharge tuning (02 §5, aligns with difficulty scaling idea)
    public static boolean RECHARGE_ON_VICTORY = true;
    public static boolean RECHARGE_IN_PVP = false; // no recharge from PvP by default
    public static int RECHARGE_BASE = 20;          // base energy granted for any trainer victory
    public static double RECHARGE_PER_LEVEL = 1.5; // added per average defeated NPC level
    public static int RECHARGE_PER_NPC = 10;       // bonus per NPC opponent defeated (team count)
    public static int RECHARGE_MIN = 5;            // clamp minimum
    public static int RECHARGE_MAX = 400;          // clamp maximum
}
