package com.jayemceekay.shadowedhearts.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Client-side registry holder for shader instances loaded by platform-specific
 * client bootstrap code.
 *
 * <p>RenderTypes and VFX systems read these fields dynamically because shader
 * instances can be recreated during resource reloads. A {@code null} value means
 * the shader has not been registered yet or failed to load; callers should keep
 * their existing fallback behavior rather than caching these references.
 */
public final class ModShaders {

    /**
     * Private clone of Minecraft's 36-byte entity layout for manually packed
     * Dark Ball geometry.
     *
     * <p>Iris identifies {@code DefaultVertexFormat.NEW_ENTITY} by object
     * identity and replaces its VAO state with a 54-byte extended layout while
     * a shader pack is active. Dark Ball writes these buffers directly at the
     * vanilla 36-byte stride, so using the shared vanilla instance makes the
     * GPU walk across record boundaries. A distinct but structurally identical
     * format keeps the six shader attributes and prevents that substitution.</p>
     */
    public static final VertexFormat DARK_BALL_MANUAL_ENTITY_FORMAT =
            VertexFormat.builder()
                    .add("Position", VertexFormatElement.POSITION)
                    .add("Color", VertexFormatElement.COLOR)
                    .add("UV0", VertexFormatElement.UV0)
                    .add("UV1", VertexFormatElement.UV1)
                    .add("UV2", VertexFormatElement.UV2)
                    .add("Normal", VertexFormatElement.NORMAL)
                    .padding(1)
                    .build();

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

    // Dark Ball captured-mask, fused-surface-splat, and siphon pipeline
    public static ShaderInstance DARK_BALL_MASK;
    public static ShaderInstance DARK_BALL_PROXY_DEPTH;
    public static ShaderInstance DARK_BALL_VOLUME_COMPOSITE;
    public static ShaderInstance DARK_BALL_SILHOUETTE_DISTANCE_SEED;
    public static ShaderInstance DARK_BALL_SILHOUETTE_DISTANCE_JUMP;
    public static ShaderInstance DARK_BALL_REDUCED_UPSAMPLE;
    public static ShaderInstance DARK_BALL_SURFACE_SPLAT;
    public static ShaderInstance DARK_BALL_SURFACE_SPLAT_RESOLVE;
    public static ShaderInstance DARK_BALL_SIPHON_SURFACE_MESH;
    public static ShaderInstance DARK_BALL_EDGE_TONGUES;
    public static ShaderInstance DARK_BALL_FBO_PREVIEW;

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
    public static ShaderInstance SHADOW_POKEMON_AURA_DIRECT;

    // Screen-space electromagnetic static overlay for Aura Scanner
    public static ShaderInstance AURA_STATIC_INTERFERENCE;
    public static ShaderInstance HEAT_HAZE_INTERFERENCE;


    // Screen-space HUD shaders
    public static ShaderInstance HUD_BARREL_DISTORTION;

    private ModShaders() {}

    /**
     * Platform hook used to force this common class to load before the platform
     * shader-registration event wires actual {@link ShaderInstance} values.
     */
    public static void initClient() {}

}
