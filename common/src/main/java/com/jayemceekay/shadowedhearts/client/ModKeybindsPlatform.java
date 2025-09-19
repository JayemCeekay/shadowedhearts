package com.jayemceekay.shadowedhearts.client;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.KeyMapping;

public final class ModKeybindsPlatform {
    private ModKeybindsPlatform() {}

    @ExpectPlatform
    public static void register(KeyMapping mapping) {
        throw new AssertionError();
    }
}
