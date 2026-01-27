package com.jayemceekay.shadowedhearts.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Client key mappings shared across platforms. Platform registration occurs in ModKeybindsPlatform.
 */
public final class ModKeybinds {
    private ModKeybinds() {}

    public static KeyMapping AURA_SCANNER;
    public static KeyMapping AURA_PULSE;

    private static final String CAT = "key.categories.shadowedhearts";
    public static void init() {
        if (AURA_SCANNER == null) {
            AURA_SCANNER = new KeyMapping(
                    "key.shadowedhearts.aura_scanner",
                    GLFW.GLFW_KEY_V,
                    CAT
            );
        }

        if (AURA_PULSE == null) {
            AURA_PULSE = new KeyMapping(
                    "key.shadowedhearts.aura_pulse",
                    GLFW.GLFW_KEY_B,
                    CAT
            );
        }
    }


    public static boolean consumeAuraScannerPress() {
        return AURA_SCANNER != null && AURA_SCANNER.consumeClick();
    }

    public static boolean consumeAuraPulsePress() {
        return AURA_PULSE != null && AURA_PULSE.consumeClick();
    }
}
