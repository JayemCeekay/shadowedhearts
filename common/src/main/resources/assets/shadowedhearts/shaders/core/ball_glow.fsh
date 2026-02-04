#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float u_time;

uniform vec3 u_glowTint;
uniform float u_rimStrength;
uniform float u_pulseSpeed;
uniform float u_useMask; // 1.0 = use red channel as mask, 0.0 = luminance

// Orb mode: ignore texture and render a soft radial glow on a billboarded quad
uniform float u_orbMode;      // >0.5 enables procedural orb
uniform float u_orbSoftness;  // edge softness (0..1)
uniform float u_orbIntensity; // overall intensity multiplier

// Diffraction spikes (starburst) controls for orb mode
uniform float u_starStrength;     // additive strength of spikes
uniform float u_starSharpness;    // higher = thinner spikes
uniform float u_starCount;        // N in cos(N*angle); abs(cos) yields 2N spikes
uniform float u_starFalloff;      // radial falloff power from center (0..3)
uniform float u_starRotateSpeed;  // radians/sec rotation speed
uniform float u_starPhase;        // phase offset in radians

// New palette controls to bring in yellows/oranges/violets/white
uniform float u_glowMix;        // 0..1, mix palette vs u_glowTint
uniform float u_paletteSpeed;   // hue scroll speed
uniform float u_paletteShift;   // phase offset
uniform float u_paletteSaturation; // 0..1
uniform float u_voxelsPerRad;

// Match trail palette controls so both can share tuning
uniform vec3 u_c0; // center color
uniform vec3 u_c1; // inner mid
uniform vec3 u_c2; // outer mid
uniform vec3 u_c3; // edge color
uniform float u_t1; // thresholds for segments
uniform float u_t2;
uniform float u_t3; // usually 1.0
uniform vec3 u_lumaCoeff; // optional, defaults applied if unset

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 quantize3D(vec3 p, float voxelsPerRad) {
    if (voxelsPerRad <= 0.0) return p;
    vec3 q = floor(p * voxelsPerRad + 0.5) / max(voxelsPerRad, 1.0);
    return q;
}

// Vertical-style palette used by trail; here we drive it by radial distance (center -> edge)
// Falls back to sensible defaults if uniforms are not provided by JSON.
vec3 palette_vertical_fire(float y) {
    // Default stops (approximate trail look): white -> yellow -> orange -> violet
    const vec3 DEF_c0 = vec3(1.00, 1.00, 1.00);
    const vec3 DEF_c1 = vec3(1.00, 0.92, 0.12);
    const vec3 DEF_c2 = vec3(1.00, 0.55, 0.10);
    const vec3 DEF_c3 = vec3(0.45, 0.00, 0.95);
    const float DEF_t1 = 0.20;
    const float DEF_t2 = 0.65;
    const float DEF_t3 = 1.00;

    // Detect if thresholds are unset (default 0) and use defaults
    float t1u = (u_t3 <= 0.0001) ? DEF_t1 : u_t1;
    float t2u = (u_t3 <= 0.0001) ? DEF_t2 : u_t2;
    float t3u = (u_t3 <= 0.0001) ? DEF_t3 : u_t3;

    // Defensive monotonic enforcement
    float t1b = max(0.0, min(t1u, t2u));
    float t2b = max(t1b, min(t2u, t3u));
    float t3b = max(t2b, max(t3u, 0.00001));

    // Choose colors (fallback if likely unset)
    float cSum = u_c0.x + u_c0.y + u_c0.z + u_c1.x + u_c1.y + u_c1.z + u_c2.x + u_c2.y + u_c2.z + u_c3.x + u_c3.y + u_c3.z;
    vec3 c0u = (cSum <= 0.0001) ? DEF_c0 : u_c0;
    vec3 c1u = (cSum <= 0.0001) ? DEF_c1 : u_c1;
    vec3 c2u = (cSum <= 0.0001) ? DEF_c2 : u_c2;
    vec3 c3u = (cSum <= 0.0001) ? DEF_c3 : u_c3;

    float k1 = smoothstep(0.0, t1b, y);
    vec3 m1 = mix(c0u, c1u, k1);
    float k2 = smoothstep(t1b, t2b, y);
    vec3 m2 = mix(c1u, c2u, k2);
    float k3 = smoothstep(t2b, t3b, y);
    vec3 m3 = mix(c2u, c3u, k3);

    float w1 = step(y, t1b);
    float w2 = step(t1b, y) * step(y, t2b);
    float w3 = step(t2b, y);
    vec3 c = m1 * w1 + m2 * w2 + m3 * w3;

    // Optional saturation control using provided luma coeffs (fallback to standard)
    vec3 lcoef = (u_lumaCoeff.x + u_lumaCoeff.y + u_lumaCoeff.z <= 0.001)
               ? vec3(0.299, 0.587, 0.114)
               : u_lumaCoeff;
    float Y = dot(c, lcoef);
    c = mix(vec3(Y), c, clamp(u_paletteSaturation, 0.0, 1.0));
    return c;
}

void main() {
    vec2 texCoord = texCoord0;
    // Default-facing normal (toward camera). Some entity vertex shaders used here
    // do not provide a normal varying; avoid linking errors by not requiring it.
    vec3 nrm = vec3(0.0, 0.0, 1.0);

    if (u_voxelsPerRad > 0.0) {
        texCoord = quantize3D(vec3(texCoord0, 0.0), u_voxelsPerRad).xy;
    }

    vec4 outCol;

    if (u_orbMode > 0.5) {
        // Procedural round glow on a quad. Center at 0.5, soft edge falloff.
        vec2 d = texCoord - vec2(0.5);
        // r = 0 at center, 1 at the unit circle edge; clamp to guarantee edge alpha 0
        float r = clamp(length(d) * 2.0, 0.0, 1.0);

        // Soft edge: 1 at center -> 0 at edge, with controllable fall distance
        float softness = clamp(u_orbSoftness, 0.05, 1.0);
        float s0 = max(0.0, 1.0 - softness);
        float core = 1.0 - smoothstep(s0, 1.0, r);

        // Shape core for a brighter center without widening the halo
        float coreShaped = pow(core, 0.6);

        // Add a mild pulsation
        float pulse = 0.8 + 0.2 * sin(u_time * u_pulseSpeed);

        // Subtle rim via normal if provided (keeps consistency when using entity shader)
        float rim = pow(max(0.0, 1.0 - abs(nrm.z)), 3.0) * u_rimStrength;

        // Palette by radius to match trail hues
        // Compress warm colors outward more so the white core occupies more area and yellow is subtler
        float yPal = pow(r, 1.85);
        vec3 pal = palette_vertical_fire(yPal);
        vec3 tintMixed = mix(u_glowTint, pal, clamp(u_glowMix, 0.0, 1.0));

        // Force a hot white core: blend towards pure white near the center
        float whiteMix = 1.0 - smoothstep(0.0, 0.35, r); // 1 at center -> 0 past ~0.28
        vec3 colorBase = mix(tintMixed, vec3(1.0), 0.92 * whiteMix);

        // Diffraction spikes: angular starburst shaped by cos(N*angle)
        float ang = atan(d.y, d.x);
        float N = max(1.0, u_starCount);
        float phase = u_starPhase + u_time * u_starRotateSpeed;
        // abs(cos(N*ang + phase)) gives 2N spikes; shape and normalize
        float spikeAngular = pow(abs(cos(N * ang + phase)), max(1.0, u_starSharpness));
        // Radial falloff so spikes are hottest near center and fade outward
        float spikeRadial = pow(1.0 - r, clamp(u_starFalloff, 0.0, 4.0));
        float star = spikeAngular * spikeRadial;

        float intensity = coreShaped * pulse * max(0.0, 1.0 + rim) * max(0.0, u_orbIntensity)
                        + max(0.0, u_starStrength) * star;
        vec3 color = colorBase * intensity;

        // Subtle purple lens halo near the edge (keeps alpha 0 at exact edge)
        float r0 = 0.92; // start of halo band
        float r1 = 0.985; // end of halo band (just before edge)
        float haloBand = smoothstep(r0, r1, r) * (1.0 - smoothstep(r1, 1.0, r));
        vec3 haloColor = vec3(0.70, 0.30, 0.95);
        float haloStrength = 0.95;
        float haloAlpha = 0.30;

        // Create a thin transparent gap (suppress color and alpha) just inside the halo start.
        // This separates the warm yellow region from the purple outer ring.
        float gapWidth = 0.80;             // thin annular gap width (in radial 0..1 units)
        float g0 = max(0.0, r0 - gapWidth); // inner edge of the gap
        // Gap band is active in [g0, r0) with smooth edges
        float gapBand = smoothstep(g0, r0, r) * (1.0 - smoothstep(r0, r0 + 0.0001, r));
        float gapMask = 1.0 - gapBand;      // 1 outside gap, 0 inside gap

        // Suppress core/spike contributions inside the gap
        color *= gapMask;

        // Add halo (it begins at r0, so naturally appears outside the gap)
        color += haloColor * haloBand * haloStrength;

        // Alpha: core plus a little from the lens halo band; still 0 at r=1 by construction
        float alpha = clamp(core + haloBand * haloAlpha, 0.0, 1.0);
        // Also suppress alpha within the gap to ensure a clear separation
        alpha *= gapMask;
        outCol = vec4(color, alpha);
    } else {
        vec4 base = texture(Sampler0, texCoord);
        if (base.a < 0.1) discard;

        // Choose glow source: mask red or luminance of base
        float maskGlow = base.r;
        float lumGlow = luma(base.rgb);
        float glow = mix(lumGlow, maskGlow, clamp(u_useMask, 0.0, 1.0));

        // Pulsing intensity using global game time
        float pulse = 0.6 + 0.4 * sin(u_time * u_pulseSpeed);

        // Rim via view-space normal — stronger at grazing angles
        float rim = pow(max(0.0, 1.0 - abs(nrm.z)), 3.0);

        float intensity = max(0.0, glow * pulse + rim * u_rimStrength);

        // Outward gradient like ball_trail: center -> edges drives the palette
        // radiusFromCenter: 0 at center, ~1 at edges (assuming roughly square UV island)
        vec2 d = texCoord - vec2(0.5);
        float radiusFromCenter = clamp(length(d) * 2.0, 0.0, 1.0);
        float yFromCenter = radiusFromCenter; // match trail's 0..1 mapping
        vec3 pal = palette_vertical_fire(yFromCenter);

        vec3 tintMixed = mix(u_glowTint, pal, clamp(u_glowMix, 0.0, 1.0));
        vec3 color = tintMixed * intensity;

        outCol = vec4(color, intensity);
    }
    // Apply vertex/overlay tinting and ensure additive path respects alpha fades.
    // Default multiplication (outCol *= vertexColor * ColorModulator) doesn't dim RGB when vertexColor.rgb = 1.
    // With additive blending (ONE, ONE), color channels are independent of alpha, so explicitly scale RGB by alpha.
    float alphaMod = vertexColor.a * ColorModulator.a;
    // Apply color tints to RGB
    outCol.rgb *= (vertexColor.rgb * ColorModulator.rgb * overlayColor.rgb);
    // Apply alpha normally
    outCol.a *= alphaMod * overlayColor.a;
    // Also attenuate RGB by the combined alpha so fades are smooth under additive blending
    outCol.rgb *= alphaMod;

    // Apply linear fog fade on alpha to reduce harsh cut in distance
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    outCol.rgb = mix(FogColor.rgb, outCol.rgb, fogFade);
    outCol.a *= fogFade;

    fragColor = outCol;
}
