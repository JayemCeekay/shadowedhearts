package com.jayemceekay.shadowedhearts.client;

import net.minecraft.client.renderer.ShaderInstance;

public final class ModShaders {

    // Active shaders used by RenderTypes (dynamic supplier reads these each draw)
    //public static ShaderInstance SHADOW_AURA_FOG;
    //public static ShadowFogUniforms SHADOW_AURA_FOG_UNIFORMS;

    // Cylinder variant for vertical pillar auras
    public static ShaderInstance SHADOW_AURA_FOG_CYLINDER;
    public static ShadowFogUniforms SHADOW_AURA_FOG_CYLINDER_UNIFORMS;

    public static ShaderInstance SHADOW_AURA_XD_CYLINDER;
    public static ShadowFogUniforms SHADOW_AURA_XD_CYLINDER_UNIFORMS;

    public static ShaderInstance SHADOW_POOL;
    public static ShaderInstance WHISTLE_GROUND_OVERLAY;
    public static ShaderInstance PURIFICATION_CHAMBER_BACKGROUND;

    // New: Poké Ball glow overlay shader (additive + fullbright)
    public static ShaderInstance BALL_GLOW;

    // Trail ribbon shader (uses UV scrolling texture)
    public static ShaderInstance BALL_TRAIL;

    public static ShaderInstance AURA_PULSE;

    private ModShaders() {}

    /** Called from each platform's client init to trigger shader registration. */
    public static void initClient() {}

}
