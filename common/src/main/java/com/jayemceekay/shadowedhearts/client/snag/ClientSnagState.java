package com.jayemceekay.shadowedhearts.client.snag;

public final class ClientSnagState {
    private static volatile boolean armed;
    private static volatile boolean eligible;
    private static volatile int rechargeTurnsRemaining;

    private ClientSnagState() {}

    public static boolean isArmed() {
        return armed;
    }

    public static void setArmed(boolean value) {
        armed = value;
    }

    public static boolean isEligible() {
        return eligible;
    }

    public static void setEligible(boolean value) {
        eligible = value;
        if (!value) rechargeTurnsRemaining = 0;
    }

    public static int getRechargeTurnsRemaining() {
        return rechargeTurnsRemaining;
    }

    public static boolean isRecharging() {
        return rechargeTurnsRemaining > 0;
    }

    public static void setRechargeTurnsRemaining(int value) {
        rechargeTurnsRemaining = eligible ? Math.max(0, value) : 0;
    }
}
