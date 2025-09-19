package com.jayemceekay.shadowedhearts.client;

import net.minecraft.client.renderer.ShaderInstance;

public final class ModShaders {

    public static ShaderInstance SHADOW_AURA_FOG;
    public static ShaderInstance SHADOW_POOL;
    public static ShaderInstance SHADOW_DARKEN_LAYER;

    private ModShaders() {}

    /** Called from each platform's client init. */
    public static void initClient() {
        ModShadersPlatform.registerShaders();
    }


}
