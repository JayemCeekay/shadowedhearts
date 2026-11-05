#version 150

uniform vec2 ViewportSize;
uniform float FlowTime;
uniform float Energy;
uniform float Activity;
uniform float Occupied;
uniform float PulseAge;

in vec2 texCoord0;
out vec4 fragColor;

float line(float d, float width) {
    return 1.0 - smoothstep(width, width + 0.006, abs(d));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float cloud(vec2 p) {
    return 0.57 * noise(p) + 0.28 * noise(p * 2.07) + 0.15 * noise(p * 4.1);
}

void main() {
    // GuiGraphics UVs start at the top; the accepted concept's UVs start at the bottom.
    vec2 uv = vec2(texCoord0.x, 1.0 - texCoord0.y);
    vec2 p = (uv - 0.5) * vec2(ViewportSize.x / max(ViewportSize.y, 1.0), 1.0);
    float t = FlowTime;
    float power = (0.32 + Energy * 0.68) * 1.4;
    vec3 col = vec3(0.032, 0.034, 0.044);
    vec3 violet = vec3(0.47, 0.20, 0.76);
    vec3 pale = vec3(0.53, 0.45, 0.72);
    float quiet = 0.32 + 0.68 * smoothstep(0.10, 0.35,
        length((p - vec2(0.0, 0.04)) * vec2(1.1, 0.75)));
    float glow = 0.0;
    float detail = 0.0;

    // A subtle elliptical pool under the cyan platform, with currents rising at its sides.
    vec2 base = p - vec2(0.0, -0.29);
    float pool = length(base * vec2(1.0, 3.2));
    float waves = 0.5 + 0.5 * sin(pool * 64.0 + t * 1.5 + cloud(p * 8.0) * 3.0);
    glow += pow(waves, 7.0) * exp(-pool * 6.0) * 0.22;
    for (int i = 0; i < 5; i++) {
        float k = float(i);
        float side = mod(k, 2.0) * 2.0 - 1.0;
        float x = side * (0.18 + k * 0.021 + sin(p.y * 9.0 + t * 0.65 + k * 2.1) * 0.035);
        float stream = exp(-abs(p.x - x) * (40.0 + k * 7.0));
        float textureNoise = cloud(vec2(p.x * 18.0 + k * 5.0, p.y * 7.0 - t * 0.6));
        glow += stream * textureNoise * (0.34 + 0.16 * Activity)
            * (1.0 - smoothstep(-0.36, 0.48, p.y));
    }
    glow += cloud(p * 5.0 - vec2(0.0, t * 0.15)) * 0.07 * quiet;

    for (int i = 0; i < 9; i++) {
        float k = float(i);
        float x = (hash(vec2(k, 3.0)) - 0.5) * 0.76;
        float y = fract(hash(vec2(k, 9.0)) + t * (0.013 + 0.002 * k)) - 0.5;
        x += sin(y * 8.0 + k) * 0.012;
        float d = length(p - vec2(x, y));
        detail += (1.0 - smoothstep(0.001, 0.005, d)) * 0.23 * quiet * (0.3 + 0.7 * Activity);
    }

    float floorGlow = exp(-length((p - vec2(0.0, -0.30)) * vec2(3.0, 11.0)) * 3.0);
    col += violet * glow * power + pale * detail * power;
    col += vec3(0.12, 0.22, 0.25) * floorGlow * 0.17 * mix(0.5, 1.0, Occupied);

    // Brief outward ripple on a visible completion transition, never a persistent flashing ring.
    if (PulseAge >= 0.0 && PulseAge < 1.4) {
        float phase = PulseAge / 1.4;
        float fade = sin(phase * 3.14159265);
        float radius = mix(0.08, 0.48, phase);
        float ring = line(length((p - vec2(0.0, -0.25)) * vec2(1.0, 0.8)) - radius, 0.002);
        col += vec3(0.27, 0.37, 0.36) * ring * fade * 0.30;
    }
    float vignette = 1.0 - 0.45 * smoothstep(0.25, 0.65, length(p));
    fragColor = vec4(col * vignette, 1.0);
}
