package com.jayemceekay.shadowedhearts.client.snag;

public final class ClientSnagState {
    private static volatile boolean armed;

    private ClientSnagState() {}

    public static boolean isArmed() {
        return armed;
    }

    public static void setArmed(boolean value) {
        armed = value;
    }
}
