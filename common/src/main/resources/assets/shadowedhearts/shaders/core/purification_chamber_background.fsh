// dome_background.fsh
#version 150

// === Minecraft core shader uniforms ===
// Provided by the game:
//  - ScreenSize: current framebuffer dimensions in pixels
//  - GameTime: global world time in *days* (fractional)
uniform vec2 ScreenSize;
uniform float GameTime;
in vec2 texCoord0;

out vec4 fragColor;

// =====================
// Utility / noise
// =====================
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// =====================
// Effects helpers
// =====================

float nodeGlow(float meridians, float rings, float u, float v, float time) {
    float nodeMask = meridians * rings; // where both are strong

    // small per-node flicker
    float rnd = hash21(vec2(floor(u * 24.0), floor(v * 12.0)));
    float flicker = 0.6 + 0.4 * sin(time * 6.0 + rnd * 10.0);

    return nodeMask * flicker;
}

float scanBand(float v, float time) {
    float pos  = fract(time * 0.03); // slow sweep
    float w    = 0.08;               // band width
    float d    = abs(v - pos);
    return smoothstep(w, 0.0, d);    // 0..1, sharp in the center
}

float segmentFlicker(float u, float v, float time) {
    // quantize space into a grid
    vec2 cell = floor(vec2(u * 24.0, v * 12.0));
    float rnd = hash21(cell);

    // each cell has its own timer offset
    float t = fract(time * 0.5 + rnd * 10.0);

    // only light up briefly when t < threshold
    float on = step(t, 0.12); // ~12% duty cycle
    return on * rnd;          // random brightness
}

float pulseAlongMeridians(float u, float v, float time) {
    float speed = 0.2;
    float width = 0.04;

    // Moving band in v, offset per-meridian by u
    float phase = fract(time * speed + u * 3.0);
    float d = abs(v - phase);

    // Gaussian-ish falloff
    float pulse = exp(-d * d / (width * width));
    return pulse;
}

float breathing(float time) {
    return 0.8 + 0.2 * sin(time * 0.7);
}

// =====================
// Pattern in spherical (u,v)
// =====================

vec3 paintPattern(float u, float v, float time) {
    const float N_MERIDIANS = 24.0;
    const float N_RINGS     = 8.0;
    const float WIDTH       = 0.05;

    // === base lines ===
    float mer = 0.5 - abs(fract(u * N_MERIDIANS) - 0.5);
    mer = smoothstep(WIDTH, 0.0, mer);

    float rings = 0.5 - abs(fract(v * N_RINGS) - 0.5);
    rings = smoothstep(WIDTH, 0.0, rings);

    float linesBase = clamp(mer + rings, 0.0, 1.0);

    // === effects ===
    float merPulse   = pulseAlongMeridians(u, v, time);
    float flicker    = segmentFlicker(u, v, time);
    float nodes      = nodeGlow(mer, rings, u, v, time);
    float sweep      = scanBand(v, time);
    float breatheVal = breathing(time);

    // combine them into a modulation factor
    float dynamicBoost =
    0.5 * merPulse +
    0.7 * flicker +
    0.7 * nodes +
    0.6 * sweep;

    // clamp for sanity
    dynamicBoost = clamp(dynamicBoost, 0.0, 2.0);

    vec3 base      = vec3(0.01, 0.0, 0.05);
    vec3 lineColor = vec3(0.4, 0.2, 0.8);

    float lineIntensity = linesBase * (1.5 + dynamicBoost);
    lineIntensity *= breatheVal;

    vec3 col = base;
    col += lineColor * lineIntensity;

    // optional: darken near poles to keep “dome” read
    float edgeDark = smoothstep(0.0, 0.2, v) * smoothstep(1.0, 0.8, v);
    col *= mix(1.2, 0.5, edgeDark);

    return col;
}

// =====================
// Ray into sphere interior
// =====================

vec3 renderBackground(vec2 fragCoord, float time) {
    // Normalized [-1,1] screen coords
    vec2 uv = fragCoord / ScreenSize;
    uv = uv * 2.0 - 1.0;
    //uv.x *= ScreenSize.x / ScreenSize.y;

    // Camera just inside the front of the sphere
    vec3 ro = vec3(0.0, 0.0, 1.25);
    vec3 rd = normalize(vec3(uv, -1.0)); // looking down -Z

    float R = 1.5; // sphere radius

    // Ray-sphere intersection
    float b = dot(ro, rd);
    float c = dot(ro, ro) - R * R;
    float h = b * b - c;
    if (h < 0.0) {
        return vec3(0.0); // no hit (shouldn't really happen if you're inside)
    }

    // since we are inside the sphere, pick the far hit
    float t = -b + sqrt(h);

    vec3 p = ro + t * rd;   // point on inner wall
    vec3 n = normalize(p);  // normal on sphere

    // Spherical coords on the inside surface
    float theta = acos(clamp(n.y, -1.0, 1.0)); // 0 = top, pi = bottom
    float phi   = atan(n.z, n.x);              // -pi..pi

    float v = theta / 3.14159265;              // 0..1, pole to pole
    float u = phi / (2.0 * 3.14159265) + 0.5;  // 0..1 around

    vec3 col = paintPattern(u, v, time);

    // Extra bowl-like shading
    float edgeDark = smoothstep(0.0, 0.2, v) * smoothstep(1.0, 0.8, v);
    col *= mix(1.3, 0.5, edgeDark);

    return col;
}

// =====================
// Main
// =====================

void main() {
    // GameTime is in days; multiply to get ~seconds range
    float time = fract(GameTime) * 1200.0; // 1200 sec = 20 minutes

    // Use local UVs from the POSITION_TEX vertex shader, scaled to pixel space of the quad
    vec2 fragCoord = texCoord0 * ScreenSize;
    vec3 col = renderBackground(fragCoord, time);
    fragColor = vec4(col, 1.0);
}
