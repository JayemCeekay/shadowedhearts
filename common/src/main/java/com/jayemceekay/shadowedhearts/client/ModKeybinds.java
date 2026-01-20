package com.jayemceekay.shadowedhearts.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Client key mappings shared across platforms. Platform registration occurs in ModKeybindsPlatform.
 */
public final class ModKeybinds {
    private ModKeybinds() {}

    public static KeyMapping ORDER_WHEEL;
    public static KeyMapping AURA_TRAIL_TOGGLE;
    public static KeyMapping AURA_SCANNER;
    public static KeyMapping AURA_PULSE;

    private static final String CAT = "key.categories.shadowedhearts";
    public static void init() {
        if (ORDER_WHEEL == null) {
            ORDER_WHEEL = new KeyMapping(
                    "key.shadowedhearts.order_wheel",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    CAT
            );
        }

        if (AURA_SCANNER == null) {
            AURA_SCANNER = new KeyMapping(
                    "key.shadowedhearts.aura_scanner",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    CAT
            );
        }

        if (AURA_PULSE == null) {
            AURA_PULSE = new KeyMapping(
                    "key.shadowedhearts.aura_pulse",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    CAT
            );
        }

    }

    public static boolean isOrderWheelDown() {
        return ORDER_WHEEL != null && ORDER_WHEEL.isDown();
    }

    public static boolean consumeOrderWheelPress() {
        return ORDER_WHEEL != null && ORDER_WHEEL.consumeClick();
    }

    public static boolean consumeAuraScannerPress() {
        return AURA_SCANNER != null && AURA_SCANNER.consumeClick();
    }

    public static boolean consumeAuraPulsePress() {
        return AURA_PULSE != null && AURA_PULSE.consumeClick();
    }
}
