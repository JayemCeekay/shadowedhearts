package com.jayemceekay.shadowedhearts.client;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class ModShadersPlatform {

    @ExpectPlatform
    public static void registerShaders() {
        throw new AssertionError();
    }
}