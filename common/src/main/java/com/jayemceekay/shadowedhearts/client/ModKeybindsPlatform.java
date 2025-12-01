package com.jayemceekay.shadowedhearts.client;

import net.minecraft.client.KeyMapping;

public final class ModKeybindsPlatform {
    private ModKeybindsPlatform() {}

    public static void register(KeyMapping mapping) {
        throw new AssertionError();
    }
}
