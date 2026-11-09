package com.jayemceekay.shadowedhearts.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Client key mappings shared across platforms. Platform registration occurs in loader-specific client init.
 */
public final class ModKeybinds {
    private ModKeybinds() {
    }

    public static KeyMapping AURA_SCANNER;
    public static KeyMapping AURA_READER_HELP;
    public static KeyMapping AURA_PULSE;
    public static KeyMapping AURA_LOCK;
    public static KeyMapping AURA_NEXT_SIGNAL;
    public static KeyMapping AURA_PREV_SIGNAL;
    public static KeyMapping DEBUG_REINIT_HUD;
    public static KeyMapping RESCUE_WELCOME;
    public static KeyMapping RESCUE_RELEASE;

    private static final String CAT = "key.categories.shadowedhearts";

    public static void init() {
        if (AURA_SCANNER == null) {
            AURA_SCANNER = new KeyMapping("key.shadowedhearts.aura_scanner", GLFW.GLFW_KEY_V, CAT);
        }

        if (AURA_READER_HELP == null) {
            // Preserve the saved binding ID from the former mode selector.
            AURA_READER_HELP = new KeyMapping("key.shadowedhearts.aura_mode_selector", GLFW.GLFW_KEY_G, CAT);
        }

        if (AURA_PULSE == null) {
            AURA_PULSE = new KeyMapping("key.shadowedhearts.aura_pulse", GLFW.GLFW_KEY_B, CAT);
        }

        if (AURA_LOCK == null) {
            AURA_LOCK = new KeyMapping("key.shadowedhearts.aura_lock", GLFW.GLFW_KEY_N, CAT);
        }

        if (AURA_NEXT_SIGNAL == null) {
            AURA_NEXT_SIGNAL = new KeyMapping("key.shadowedhearts.aura_next_signal", GLFW.GLFW_KEY_RIGHT_BRACKET, CAT);
        }

        if (AURA_PREV_SIGNAL == null) {
            AURA_PREV_SIGNAL = new KeyMapping("key.shadowedhearts.aura_prev_signal", GLFW.GLFW_KEY_LEFT_BRACKET, CAT);
        }

        if (DEBUG_REINIT_HUD == null) {
            DEBUG_REINIT_HUD = new KeyMapping("key.shadowedhearts.debug_reinit_hud", GLFW.GLFW_KEY_F8, CAT);
        }
        // Y/N are local to the dialog. Reserving them globally would collide with Aura Lock and Raid Dens.
        if (RESCUE_WELCOME == null) {
            RESCUE_WELCOME = new KeyMapping("key.shadowedhearts.rescue_welcome", GLFW.GLFW_KEY_UNKNOWN, CAT);
            RESCUE_RELEASE = new KeyMapping("key.shadowedhearts.rescue_release", GLFW.GLFW_KEY_UNKNOWN, CAT);
        }
    }

    public static boolean consumeAuraScannerPress() {
        return AURA_SCANNER != null && AURA_SCANNER.consumeClick();
    }

    public static boolean consumeAuraReaderHelpPress() {
        return AURA_READER_HELP != null && AURA_READER_HELP.consumeClick();
    }

    public static boolean consumeAuraPulsePress() {
        return AURA_PULSE != null && AURA_PULSE.consumeClick();
    }

    public static boolean consumeAuraLockPress() {
        return AURA_LOCK != null && AURA_LOCK.consumeClick();
    }

    public static boolean consumeNextSignal() {
        return AURA_NEXT_SIGNAL != null && AURA_NEXT_SIGNAL.consumeClick();
    }

    public static boolean consumePrevSignal() {
        return AURA_PREV_SIGNAL != null && AURA_PREV_SIGNAL.consumeClick();
    }

    public static boolean consumeDebugReinitHudPress() {
        return DEBUG_REINIT_HUD != null && DEBUG_REINIT_HUD.consumeClick();
    }
}
