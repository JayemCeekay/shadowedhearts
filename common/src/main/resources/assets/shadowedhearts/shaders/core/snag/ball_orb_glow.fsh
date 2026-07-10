#version 150

#moj_import <fog.glsl>
#moj_import <shadowedhearts:palette_fire.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float u_time;

uniform vec3 u_glowTint;
uniform float u_rimStrength;
uniform float u_pulseSpeed;

uniform float u_orbSoftness;  // edge softness (0..1)
uniform float u_orbIntensity; // overall intensity multiplier

// Diffraction spikes (starburst) controls
uniform float u_starStrength;     // additive strength of spikes
uniform float u_starSharpness;    // higher = thinner spikes
uniform float u_starCount;        // N in cos(N*angle); abs(cos) yields 2N spikes
uniform float u_starFalloff;      // radial falloff power from center (0..3)
uniform float u_starRotateSpeed;  // radians/sec rotation speed
uniform float u_starPhase;        // phase offset in radians

// Palette controls
uniform float u_glowMix;        // 0..1, mix palette vs u_glowTint
uniform float u_paletteSaturation; // 0..1
uniform float u_voxelsPerRad;

// Palette stops
uniform vec3 u_c0; // center color
uniform vec3 u_c1; // inner mid
uniform vec3 u_c2; // outer mid
uniform vec3 u_c3; // edge color
uniform float u_t1;
uniform float u_t2;
uniform float u_t3;
uniform vec3 u_lumaCoeff;

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 texCoord = texCoord0;
    vec3 nrm = vec3(0.0, 0.0, 1.0);

    if (u_voxelsPerRad > 0.0) {
        texCoord = sh_quantize3D(vec3(texCoord0, 0.0), u_voxelsPerRad).xy;
    }

    // Procedural round glow on a quad. Center at 0.5, soft edge falloff.
    vec2 d = texCoord - vec2(0.5);
    float r = clamp(length(d) * 2.0, 0.0, 1.0);

    // Soft edge: 1 at center -> 0 at edge, with controllable fall distance
    float softness = clamp(u_orbSoftness, 0.05, 1.0);
    float s0 = max(0.0, 1.0 - softness);
    float core = 1.0 - smoothstep(s0, 1.0, r);

    // Shape core for a brighter center without widening the halo
    float coreShaped = pow(core, 0.6);

    // Add a mild pulsation
    float pulse = 0.8 + 0.2 * sin(u_time * u_pulseSpeed);

    // Subtle rim
    float rim = pow(max(0.0, 1.0 - abs(nrm.z)), 3.0) * u_rimStrength;

    // Palette by radius to match trail hues
    float yPal = pow(r, 1.85);

    // Detect if palette uniforms are unset and use defaults
    const vec3 DEF_c0 = vec3(1.00, 1.00, 1.00);
    const vec3 DEF_c1 = vec3(1.00, 0.92, 0.12);
    const vec3 DEF_c2 = vec3(1.00, 0.55, 0.10);
    const vec3 DEF_c3 = vec3(0.45, 0.00, 0.95);
    const float DEF_t1 = 0.20;
    const float DEF_t2 = 0.65;
    const float DEF_t3 = 1.00;

    float cSum = u_c0.x + u_c0.y + u_c0.z + u_c1.x + u_c1.y + u_c1.z
               + u_c2.x + u_c2.y + u_c2.z + u_c3.x + u_c3.y + u_c3.z;
    vec3 c0 = (cSum <= 0.0001) ? DEF_c0 : u_c0;
    vec3 c1 = (cSum <= 0.0001) ? DEF_c1 : u_c1;
    vec3 c2 = (cSum <= 0.0001) ? DEF_c2 : u_c2;
    vec3 c3 = (cSum <= 0.0001) ? DEF_c3 : u_c3;
    float t1v = (u_t3 <= 0.0001) ? DEF_t1 : u_t1;
    float t2v = (u_t3 <= 0.0001) ? DEF_t2 : u_t2;
    float t3v = (u_t3 <= 0.0001) ? DEF_t3 : u_t3;

    vec3 pal = palette_vertical_fire(yPal, c0, c1, c2, c3, t1v, t2v, t3v, u_lumaCoeff, u_paletteSaturation);
    vec3 tintMixed = mix(u_glowTint, pal, clamp(u_glowMix, 0.0, 1.0));

    // Force a hot white core
    float whiteMix = 1.0 - smoothstep(0.0, 0.35, r);
    vec3 colorBase = mix(tintMixed, vec3(1.0), 0.92 * whiteMix);

    // Diffraction spikes: angular starburst shaped by cos(N*angle)
    float ang = atan(d.y, d.x);
    float N = max(1.0, u_starCount);
    float phase = u_starPhase + u_time * u_starRotateSpeed;
    float spikeAngular = pow(abs(cos(N * ang + phase)), max(1.0, u_starSharpness));
    float spikeRadial = pow(1.0 - r, clamp(u_starFalloff, 0.0, 4.0));
    float star = spikeAngular * spikeRadial;

    float intensity = coreShaped * pulse * max(0.0, 1.0 + rim) * max(0.0, u_orbIntensity)
                    + max(0.0, u_starStrength) * star;
    vec3 color = colorBase * intensity;

    // Subtle purple lens halo near the edge
    float r0 = 0.92;
    float r1 = 0.985;
    float haloBand = smoothstep(r0, r1, r) * (1.0 - smoothstep(r1, 1.0, r));
    vec3 haloColor = vec3(0.70, 0.30, 0.95);
    float haloStrength = 0.95;
    float haloAlpha = 0.30;

    // Thin transparent gap inside the halo start
    float gapWidth = 0.80;
    float g0 = max(0.0, r0 - gapWidth);
    float gapBand = smoothstep(g0, r0, r) * (1.0 - smoothstep(r0, r0 + 0.0001, r));
    float gapMask = 1.0 - gapBand;

    color *= gapMask;
    color += haloColor * haloBand * haloStrength;

    float alpha = clamp(core + haloBand * haloAlpha, 0.0, 1.0);
    alpha *= gapMask;
    vec4 outCol = vec4(color, alpha);

    // Apply vertex/overlay tinting
    float alphaMod = vertexColor.a * ColorModulator.a;
    outCol.rgb *= (vertexColor.rgb * ColorModulator.rgb * overlayColor.rgb);
    outCol.a *= alphaMod * overlayColor.a;
    outCol.rgb *= alphaMod;

    // Fog fade
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    outCol.rgb = mix(FogColor.rgb, outCol.rgb, fogFade);
    outCol.a *= fogFade;

    fragColor = outCol;
}
