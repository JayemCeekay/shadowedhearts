#version 150

uniform sampler2D Sampler0;
uniform float GameTime;

// Overall trail strength (0..1) supplied by CPU based on motion
uniform float uStrength;

in vec4 vColor;
in vec2 vUv;
in float vTime;

out vec4 fragColor;

// ===== Simplified noise for shadow fog =====
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

// Edge feathering constants
const float EDGE_FEATHER = 0.20;
const float TAIL_POWER   = 1.4;

void main() {
    // Convention:
    //  vUv.x = 0 at head (near ball) -> 1 at tail
    //  vUv.y = 0/1 across ribbon width

    // Edge fade across width (soft sides)
    float v = vUv.y;
    float edgeIn  = smoothstep(0.0, EDGE_FEATHER, v);
    float edgeOut = 1.0 - smoothstep(1.0 - EDGE_FEATHER, 1.0, v);
    float edge = edgeIn * edgeOut;

    // Tail fade (bright at head, dim at tail)
    float headToTail = clamp(1.0 - vUv.x, 0.0, 1.0);
    float tailFade = pow(headToTail, TAIL_POWER);

    // Radial mask: distance from ribbon center (0 at center, 1 at edge)
    float yFromCenter = abs(vUv.y - 0.5) * 2.0;
    float mask = (1.0 - yFromCenter) * edge * tailFade;

    if (mask < 0.01) discard;

    // Use ribbon UVs as noise domain — these are clean 0..1, no atlas issues
    // Scroll noise along the trail for animated fog effect
    vec2 noiseBase = vec2(vUv.x * 4.0 - vTime * 0.15, vUv.y * 2.0);

    // === Pseudo-raymarching: 3 depth layers front-to-back ===
    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;

    // Color palette (matches penumbra_trail particle shader)
    vec3 colorBlack = vec3(0.02, 0.005, 0.03);  // near-black inner core
    vec3 colorDeep  = vec3(0.08, 0.02, 0.12);   // very dark purple
    vec3 colorMid   = vec3(0.20, 0.06, 0.28);   // dark purple
    vec3 colorHot   = vec3(0.50, 0.18, 0.65);   // muted highlight

    #define DEPTH_LAYERS 3
    for (int layer = 0; layer < DEPTH_LAYERS; ++layer) {
        float depthT = float(layer) / float(DEPTH_LAYERS - 1); // 0=front, 1=back

        // Offset noise domain per layer to simulate depth
        float depthOffset = depthT * 1.5;
        vec3 noiseCoord = vec3(noiseBase + depthOffset, vTime * 0.4 + depthT * 7.7);
        float n = fbm3(noiseCoord);

        // Secondary warp for organic curl
        vec3 warpCoord = vec3(noiseBase * 0.7 + n * 0.5, vTime * 0.25 + depthT * 13.1 + 17.3);
        float n2 = fbm3(warpCoord);

        float combined = mix(n, n2, 0.5);

        // Inner layers use tighter mask (only visible near center of ribbon)
        float layerMask = mask * smoothstep(0.0, 0.3 + 0.4 * (1.0 - depthT), mask);

        // Density from noise
        float density = layerMask * combined;
        density = smoothstep(0.05, 0.35, density);

        // Color: inner layers → black core, outer layers → purple highlights
        vec3 layerCol;
        if (depthT > 0.6) {
            layerCol = mix(colorBlack, colorDeep, smoothstep(0.2, 0.6, combined));
        } else if (depthT > 0.3) {
            layerCol = mix(colorDeep, colorMid, smoothstep(0.15, 0.5, combined));
        } else {
            layerCol = mix(colorMid, colorHot, smoothstep(0.4, 0.8, combined));
        }

        // Per-layer alpha contribution
        float layerA = density * (1.0 / float(DEPTH_LAYERS)) * 2.0;

        // Front-to-back accumulation
        float premul = layerA * (1.0 - accumA);
        accumRGB += layerCol * premul;
        accumA   += premul;
    }

    // Apply vertex color and strength
    float alpha = accumA * mask * vColor.a * uStrength;
    if (alpha < 0.005) discard;

    vec3 col = accumRGB * vColor.rgb;

    // Subtle emission boost
    col *= 1.3;

    fragColor = vec4(col, alpha);
}
