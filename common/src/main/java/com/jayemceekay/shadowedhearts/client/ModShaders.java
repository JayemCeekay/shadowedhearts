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

    // Poké Ball glow overlay shader — texture-based glow only (additive + fullbright)
    public static ShaderInstance BALL_GLOW;

    // Procedural radial orb glow + starburst + halo (split from ball_glow)
    public static ShaderInstance BALL_ORB_GLOW;

    // Trail ribbon shader (uses UV scrolling texture)
    public static ShaderInstance BALL_TRAIL;

    // Lens flare shader (procedural streak + spike pattern)
    public static ShaderInstance SNAG_FLARE;

    // Orb shell shader (sphere-projected noise with shell band)
    public static ShaderInstance SNAG_ORB;

    // Pokémon dissolve shader (noise-based dissolution with glowing edge)
    public static ShaderInstance SNAG_DISSOLVE;

    // Bloom pipeline shaders (simple Gaussian blur + additive composite)
    public static ShaderInstance SNAG_BLOOM_BLUR;
    public static ShaderInstance SNAG_BLOOM_COMPOSITE;

    // Snag beam density metaball pipeline (replaces ribbon beams)
    public static ShaderInstance SNAG_BEAM_DENSITY;
    public static ShaderInstance SNAG_BEAM_COMPOSITE;

    // Snag trail orange smoke density pipeline (FBM noise + warm orange composite)
    public static ShaderInstance SNAG_TRAIL_DENSITY;
    public static ShaderInstance SNAG_TRAIL_COMPOSITE;

    // Snag trail purple mote density pipeline (separate from capture beam colors/effects)
    public static ShaderInstance SNAG_MOTE_DENSITY;
    public static ShaderInstance SNAG_MOTE_COMPOSITE;

    // Shadow aura trail tube shader (lightweight 2-octave noise on 6-sided tube)
    public static ShaderInstance SHADOW_AURA_TRAIL;

    public static ShaderInstance AURA_PULSE;
    public static ShaderInstance LUMINOUS_MOTE;
    public static ShaderInstance PENUMBRA_TRAIL;
    public static ShaderInstance PENUMBRA_TRAIL_RIBBON;

    // Penumbra density metaball pipeline
    public static ShaderInstance PENUMBRA_DENSITY_SPLAT;
    public static ShaderInstance PENUMBRA_BLUR;
    public static ShaderInstance PENUMBRA_COMPOSITE;

    // Shadow Pokemon aura density pipeline
    public static ShaderInstance SHADOW_POKEMON_AURA_MASK;
    public static ShaderInstance SHADOW_POKEMON_AURA_DENSITY;
    public static ShaderInstance SHADOW_POKEMON_AURA_COMPOSITE;

    // Screen-space electromagnetic static overlay for Aura Scanner
    public static ShaderInstance AURA_STATIC_INTERFERENCE;
    public static ShaderInstance HEAT_HAZE_INTERFERENCE;


    // Screen-space HUD shaders
    public static ShaderInstance HUD_BARREL_DISTORTION;

    private ModShaders() {}

    /** Called from each platform's client init to trigger shader registration. */
    public static void initClient() {}

}
