#version 150

uniform sampler2D Sampler0;
uniform float GameTime;
uniform vec2 ScreenSize;

in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * vnoise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return v;
}

void main() {
    vec4 data = texture(Sampler0, texCoord0);
    float broad = data.r;
    float heat = data.g;
    float wisp = data.b;
    float coverage = data.a;

    if (broad < 0.001 && heat < 0.001 && wisp < 0.001 && coverage < 0.001) discard;

    float broadFog = smoothstep(0.016, 0.54, broad);
    broadFog = pow(broadFog, 0.86);
    float interiorMask = smoothstep(0.48, 1.12, broad);
    broadFog = broadFog * (1.0 - interiorMask * 0.48) + broadFog * interiorMask * 0.18;
    float wispFog = smoothstep(0.012, 0.38, wisp);
    wispFog = pow(wispFog, 0.74);
    float fog = max(broadFog, wispFog * 0.72);
    float hot = smoothstep(0.14, 0.62, heat);
    float whiteFleck = smoothstep(0.68, 1.18, heat) * smoothstep(0.05, 0.42, wisp + coverage * 0.20);

    if (fog < 0.004 && hot < 0.01) discard;

    float combinedDensity = broad + wisp * 0.72;
    float dx = dFdx(combinedDensity);
    float dy = dFdy(combinedDensity);
    float gradient = length(vec2(dx, dy));
    float rimStrength = smoothstep(0.0, 0.14, gradient);

    float densityNorm = smoothstep(0.05, 0.58, min(combinedDensity, 0.66));
    vec2 noiseUV = texCoord0 * ScreenSize / 62.0 + GameTime * vec2(240.0, -165.0);
    float patch = fbm(noiseUV);
    float patchMask = smoothstep(0.28, 0.78, patch);

    vec3 blackViolet = vec3(0.015, 0.003, 0.028);
    vec3 deepPurple = vec3(0.065, 0.014, 0.115);
    vec3 smokePurple = vec3(0.18, 0.055, 0.28);
    vec3 colosseumPurple = vec3(0.48, 0.16, 0.72);
    vec3 magentaHot = vec3(0.92, 0.38, 1.00);
    vec3 whiteHot = vec3(1.00, 0.92, 1.00);

    vec3 broadColor = mix(blackViolet, deepPurple, smoothstep(0.0, 0.48, densityNorm));
    broadColor = mix(broadColor, smokePurple, smoothstep(0.18, 0.68, broadFog) * 0.42);
    vec3 wispColor = mix(smokePurple, colosseumPurple, smoothstep(0.02, 0.42, wispFog));
    vec3 edgeColor = mix(smokePurple, colosseumPurple, smoothstep(0.0, 0.32, 1.0 - densityNorm));

    vec3 color = mix(edgeColor, broadColor, densityNorm * 0.76);
    color = mix(color, wispColor, clamp(wispFog * 0.58 + rimStrength * 0.16, 0.0, 0.78));
    color = mix(color, colosseumPurple, patchMask * wispFog * (1.0 - densityNorm) * 0.32);
    color = mix(color, magentaHot, hot * 0.26);
    color = mix(color, whiteHot, whiteFleck * 0.44);

    color += colosseumPurple * rimStrength * 0.26;

    float rim = 1.0 - smoothstep(0.12, 0.55, fog);
    color += colosseumPurple * rim * 0.15;

    float voidNoise = fbm(texCoord0 * ScreenSize / 90.0 + GameTime * vec2(90.0, -70.0));
    float voidMask = smoothstep(0.36, 0.86, densityNorm * voidNoise) * (1.0 - hot * 0.55);
    color = mix(color, blackViolet, voidMask * 0.30);

    float darkenFactor = broadFog * 0.22;
    color *= (1.0 - darkenFactor * 0.28);
    color *= 1.04;

    float coverageFog = smoothstep(0.02, 0.58, coverage);
    float alpha = min(broadFog * 0.54 + wispFog * 0.42 + coverageFog * 0.12 + hot * 0.06 + whiteFleck * 0.07 + darkenFactor * 0.34, 0.74);
    if (alpha < 0.005) discard;

    fragColor = vec4(color, alpha);
}
