package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.Cobblemon;

/**
 * Simple debug logger for micro one-turn battles.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture/attack/battle/hurt terms are gameplay mechanics.
 */
public final class MicroDebug {
    private MicroDebug() {}

    public static boolean ENABLED = true; // flip if we later add a config toggle

    public static void log(String msg) {
        if (!ENABLED) return;
        try {
            System.out.println("[SH MicroDebug] " + msg);
        } catch (Throwable t) {
            System.out.println("[SH MicroDebug] " + msg);
        }
    }

    public static void log(String fmt, Object... args) {
        if (!ENABLED) return;
        String s;
        try {
            s = String.format(fmt, args);
        } catch (Throwable t) {
            s = java.util.Arrays.toString(args);
        }
        log(s);
    }
}
