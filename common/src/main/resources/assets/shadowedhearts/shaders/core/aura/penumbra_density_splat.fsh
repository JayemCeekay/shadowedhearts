#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

// ===== Noise functions (matching shadow_aura_fog_cylinder style) =====
float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i + vec3(0, 0, 0));
    float n100 = hash31(i + vec3(1, 0, 0));
    float n010 = hash31(i + vec3(0, 1, 0));
    float n110 = hash31(i + vec3(1, 1, 0));
    float n001 = hash31(i + vec3(0, 0, 1));
    float n101 = hash31(i + vec3(1, 0, 1));
    float n011 = hash31(i + vec3(0, 1, 1));
    float n111 = hash31(i + vec3(1, 1, 1));
    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm3(vec3 p) {
    float a = 0.5, f = 0.0;
    for (int i = 0; i < 4; ++i) {
        f += a * noise3(p);
        p = p * 2.02 + vec3(31.416, 47.0, 19.19);
        a *= 0.5;
    }
    return f;
}

void main() {
    // Sample the soft radial gradient texture from the atlas
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;

    // Use texture alpha as radial mask (1 at center, 0 at edge)
    float mask = texColor.a;

    // Gaussian-like falloff for smooth particle boundaries
    float falloff = exp(-3.0 * (1.0 - mask) * (1.0 - mask));

    // Vertex color alpha carries per-particle lifecycle fade (fade-in/out from Java)
    vec4 vCol = vertexColor * ColorModulator;
    float densityStrength = vCol.a;

    // ===== FBM noise to modulate density shape =====
    // Using world-space coordinates so noise pattern moves WITH particles,
    // eliminating the swimming/jitter caused by screen-space noise.
    float time = GameTime * 1200.0;
    vec2 noiseUV = worldPos.xy / 2.0;

    // Primary FBM — directionally stretched noise for streaky, wispy tendrils
    vec3 pw = vec3(noiseUV.x * 3.0, noiseUV.y * 8.0, time * 0.03);
    // Warp domain for organic curl (shadow_aura_fog_cylinder style)
    float warp = fbm3(vec3(noiseUV * 3.0, time * 0.018));
    pw += (warp - 0.5) * 0.4;
    float n = fbm3(pw);

    // Brightness shaping — push dark regions darker, keep bright peaks
    float shaped = clamp((n - 0.1) / 0.9, 0.0, 1.0);
    shaped = pow(shaped, 1.4);

    // Fine detail — small-scale wisps for smoky texture (2-octave, cheap)
    float fine = noise3(vec3(noiseUV * 8.0, time * 0.05)) * 0.3
               + noise3(vec3(noiseUV * 14.0, time * 0.08)) * 0.15;

    // Combine: noise carves holes and creates wispy gaps in density
    // Mix range [0.25, 1.3] — noise thins edges but doesn't fully erase to avoid flicker
    float noiseMod = mix(0.25, 1.3, shaped + fine * 0.2);
    float outDensity = falloff * densityStrength * noiseMod * 0.35;

    // Heat channel: concentrated in center, also noise-modulated for variation
    float heat = falloff * falloff * densityStrength * noiseMod * 0.25;

    fragColor = vec4(outDensity, heat, 0.0, 1.0);
}
