#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
in float vTime;

out vec4 fragColor;

// ===== Simplified noise for billboard fog puffs =====
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

    // Radial mask from texture (0 at edge, 1 at center)
    float mask = texColor.a;

    // Screen-space base coordinates for noise (avoids atlas UV issues)
    vec2 screenUV = gl_FragCoord.xy / 512.0;

    // === Pseudo-raymarching: 3 depth layers front-to-back ===
    // Simulates looking "into" the puff at increasing depths.
    // Inner layers are darker (black core), outer layers are purple highlights.
    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;

    // Color palette
    vec3 colorBlack = vec3(0.02, 0.005, 0.03);  // near-black inner core
    vec3 colorDeep  = vec3(0.08, 0.02, 0.12);   // very dark purple
    vec3 colorMid   = vec3(0.20, 0.06, 0.28);   // dark purple (uColorA match)
    vec3 colorHot   = vec3(0.50, 0.18, 0.65);   // muted highlight (uColorB match)

    #define DEPTH_LAYERS 3
    for (int layer = 0; layer < DEPTH_LAYERS; ++layer) {
        float depthT = float(layer) / float(DEPTH_LAYERS - 1); // 0=front, 1=back

        // Offset noise domain per layer to simulate depth
        float depthOffset = depthT * 1.5;
        vec3 noiseCoord = vec3(screenUV * 3.0 + depthOffset, vTime * 0.4 + depthT * 7.7);
        float n = fbm3(noiseCoord);

        // Secondary warp for organic curl
        vec3 warpCoord = vec3(screenUV * 2.0 + n * 0.5, vTime * 0.25 + depthT * 13.1 + 17.3);
        float n2 = fbm3(warpCoord);

        float combined = mix(n, n2, 0.5);

        // Inner layers need higher mask threshold (only visible near center)
        // depthT=0 (front/outer): full mask. depthT=1 (back/inner): tighter mask
        float layerMask = mask * smoothstep(0.0, 0.3 + 0.4 * (1.0 - depthT), mask);

        // Density from noise, shaped per layer
        float density = layerMask * combined;
        density = smoothstep(0.05, 0.40, density);

        // Color: inner layers → black core, outer layers → purple highlights
        vec3 layerCol;
        if (depthT > 0.6) {
            // Innermost: dark black core with subtle purple tinge
            layerCol = mix(colorBlack, colorDeep, smoothstep(0.2, 0.6, combined));
        } else if (depthT > 0.3) {
            // Mid layer: dark purple
            layerCol = mix(colorDeep, colorMid, smoothstep(0.15, 0.5, combined));
        } else {
            // Front/outer layer: purple highlights visible at bright noise spots
            layerCol = mix(colorMid, colorHot, smoothstep(0.4, 0.8, combined));
        }

        // Per-layer alpha contribution
        float layerA = density * (1.0 / float(DEPTH_LAYERS)) * 1.5;

        // Front-to-back accumulation (same as shadow_aura_fog_cylinder)
        float premul = layerA * (1.0 - accumA);
        accumRGB += layerCol * premul;
        accumA   += premul;
    }

    // Apply vertex color (per-particle tint and alpha fade from Java)
    vec4 vCol = vertexColor * ColorModulator;
    float alpha = accumA * mask * vCol.a;

    if (alpha < 0.005) discard;

    // Premultiply for additive blending
    vec3 col = accumRGB * vCol.rgb;
    //col *= alpha;

    // Subtle emission boost
    col *= 1.3;

    fragColor = vec4(col, alpha);
}
