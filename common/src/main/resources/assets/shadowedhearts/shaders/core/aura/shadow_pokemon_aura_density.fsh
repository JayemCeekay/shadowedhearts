#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

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
        p = p * 2.03 + vec3(29.1, 43.7, 17.9);
        a *= 0.5;
    }
    return f;
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;

    float mask = texColor.a;
    float falloff = exp(-4.20 * (1.0 - mask) * (1.0 - mask));

    vec4 vCol = vertexColor * ColorModulator;
    float densityStrength = vCol.a;
    float broadWeight = clamp(vCol.r, 0.0, 1.0);
    float sparkWeight = clamp(vCol.g, 0.0, 1.0);
    float wispWeight = clamp(vCol.b, 0.0, 1.0);

    float time = GameTime * 1200.0;
    vec2 noiseUV = worldPos.xz * 0.75 + worldPos.yy * vec2(0.20, 0.48);
    vec3 p = vec3(noiseUV * 3.2, time * 0.052);
    float warp = fbm3(vec3(noiseUV * 2.4, time * 0.036));
    p.xy += (warp - 0.5) * 0.72;
    float n = fbm3(p);
    float shaped = pow(clamp((n - 0.13) / 0.87, 0.0, 1.0), 1.38);
    float fine = noise3(vec3(noiseUV * 12.0, time * 0.13)) * 0.20;
    float breakup = smoothstep(0.18, 0.68, shaped + fine);

    float broadNoise = mix(0.48, 1.08, shaped + fine);
    float wispNoise = mix(0.28, 1.46, shaped + fine);
    float sparkNoise = mix(0.38, 1.22, shaped + fine);

    float coverageBase = falloff * densityStrength;
    float broadDensity = coverageBase * broadWeight * broadNoise * mix(0.56, 1.0, breakup);
    float wispDensity = coverageBase * wispWeight * wispNoise * smoothstep(0.14, 0.66, shaped + fine);
    float sparkDensity = falloff * falloff * densityStrength * sparkWeight * sparkNoise * breakup;
    float coverage = coverageBase * clamp(broadWeight * 0.76 + wispWeight * 0.86 + sparkWeight * 0.42, 0.0, 1.15);

    fragColor = vec4(broadDensity, sparkDensity, wispDensity, coverage);
}
