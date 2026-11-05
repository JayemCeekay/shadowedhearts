package com.jayemceekay.shadowedhearts.client.voidshadow;

import net.minecraft.client.renderer.ShaderInstance;

/** Reloadable native material. A missing shader keeps the native black fallback. */
public final class VoidShadowShaders {
    public static ShaderInstance LIQUID;

    private VoidShadowShaders() {
    }

    /** Material values are installed at RenderType setup, after buffered submission. */
    static ShaderInstance liquid(boolean opaque, float alphaCutoff) {
        ShaderInstance shader = LIQUID;
        if (shader != null) {
            shader.safeGetUniform("OpaqueMaterial").set(opaque ? 1 : 0);
            shader.safeGetUniform("AlphaCutoff").set(alphaCutoff);
        }
        return shader;
    }
}
