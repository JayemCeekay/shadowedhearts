package com.jayemceekay.shadowedhearts.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * Client key mappings shared across platforms. Platform registration occurs in ModKeybindsPlatform.
 */
public final class ModKeybinds {
    private ModKeybinds() {}

    public static KeyMapping ORDER_WHEEL;
    public static KeyMapping AURA_TRAIL_TOGGLE;

    private static final String CAT = "key.categories.shadowedhearts";

    /** Call during client init to construct + register key mappings. */
    public static void init() {
        if (ORDER_WHEEL == null) {
            ORDER_WHEEL = new KeyMapping(
                    "key.shadowedhearts.order_wheel",
                    InputConstants.Type.KEYSYM,
                    // Default to G
                    org.lwjgl.glfw.GLFW.GLFW_KEY_G,
                    CAT
            );
        }
        ModKeybindsPlatform.register(ORDER_WHEEL);

    }

    /** Returns true while the order wheel key is currently held down. */
    public static boolean isOrderWheelDown() {
        return ORDER_WHEEL != null && ORDER_WHEEL.isDown();
    }

    /** Returns true once when the order wheel key was pressed since last poll. */
    public static boolean consumeOrderWheelPress() {
        return ORDER_WHEEL != null && ORDER_WHEEL.consumeClick();
    }
}
