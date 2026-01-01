package com.jayemceekay.shadowedhearts.client.snag;

public final class ClientSnagState {
    private static volatile boolean armed;
    private static volatile boolean eligible;

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
    }
}
