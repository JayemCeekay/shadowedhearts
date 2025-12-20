#version 150

uniform sampler2D Sampler0; // trail gradient texture (soft center, soft edges)
uniform float GameTime;

// Palette controls (JSON defaults provided in ball_trail.json)
uniform float u_paletteMix;     // 0..1, how much to use the palette vs texture*vColor
uniform float u_paletteSpeed;   // scroll speed for palette hue
uniform float u_paletteShift;   // static offset for hue phase
uniform float u_paletteSaturation; // 0..1, desaturate if needed
// Optional saturation control coefficients (for luminance calculation)
uniform vec3 u_lumaCoeff; // expected to be roughly vec3(0.299, 0.587, 0.114)
uniform float u_voxelsPerRad;

// Dynamic palette stops and thresholds (tweakable from Java)
uniform vec3 u_c0; // center color
uniform vec3 u_c1; // inner mid
uniform vec3 u_c2; // outer mid
uniform vec3 u_c3; // edge color
uniform float u_t1; // 0.0 -> t1 : c0->c1
uniform float u_t2; // t1 -> t2 : c1->c2
uniform float u_t3; // t2 -> t3 : c2->c3 (usually 1.0)

// Overall trail strength (0..1) supplied by CPU based on motion
uniform float uStrength;

// Tunables (kept as consts for cheap math; change only if you want different feel)
// Note: per request, the trail color is a constant vertical gradient; no time-based pulsing.
const float EDGE_FEATHER = 0.24;  // 0..0.5 (bigger = softer edges)
const float TAIL_POWER   = 1.25;  // fade curve toward tail (smaller = longer/brighter tail)

in vec4 vColor;
in vec2 vUv;

out vec4 fragColor;

float saturate(float x) { return clamp(x, 0.0, 1.0); }

float bayer4x4(vec2 frag) {
    ivec2 ip = ivec2(mod(frag, 4.0));
    int idx = (ip.y << 2) | ip.x;
    const float M[16] = float[16](
    0.0, 8.0, 2.0, 10.0,
    12.0, 4.0, 14.0, 6.0,
    3.0, 11.0, 1.0, 9.0,
    15.0, 7.0, 13.0, 5.0
    );
    return (M[idx] + 0.5) / 16.0;
}

float posterize(float v, float steps, float ditherAmt) {
    if (steps <= 0.5) return v;
    float dither = (bayer4x4(gl_FragCoord.xy) - 0.5) * ditherAmt;
    return floor(v * steps + dither + 0.5) / steps;
}

vec3 quantize3D(vec3 p, float voxelsPerRad) {
    if (voxelsPerRad <= 0.0) return p;
    vec3 q = floor(p * voxelsPerRad + 0.5) / max(voxelsPerRad, 1.0);
    return q;
}

// Vertical gradient palette driven by uniforms:
// y is 0 at center and 1 at the edges. Segments:
//  [0..u_t1]: u_c0 -> u_c1
//  [u_t1..u_t2]: u_c1 -> u_c2
//  [u_t2..u_t3]: u_c2 -> u_c3
vec3 palette_vertical_fire(float y) {
    // Ensure monotonic thresholds in-shader defensively
    float t1b = max(0.0, min(u_t1, u_t2));
    float t2b = max(t1b, min(u_t2, u_t3));
    float t3b = max(t2b, max(u_t3, 0.00001));

    float k1 = smoothstep(0.0, t1b, y);
    vec3 m1 = mix(u_c0, u_c1, k1);
    float k2 = smoothstep(t1b, t2b, y);
    vec3 m2 = mix(u_c1, u_c2, k2);
    float k3 = smoothstep(t2b, t3b, y);
    vec3 m3 = mix(u_c2, u_c3, k3);

    // Segment pick weights
    float w1 = step(y, t1b);
    float w2 = step(t1b, y) * step(y, t2b);
    float w3 = step(t2b, y);
    vec3 c = m1 * w1 + m2 * w2 + m3 * w3;

    // Optional saturation control
    float lum = dot(c, u_lumaCoeff);
    c = mix(vec3(lum), c, saturate(u_paletteSaturation));
    return c;
}

void main() {
    vec2 uv = vUv;

    // Convention:
    //  uv.x = 0 at head (near ball) -> 1 at tail
    //  uv.y = 0/1 across ribbon width

    // Edge fade across width (so ribbon has soft sides)
    float v = uv.y;
    float edgeIn  = smoothstep(0.0, EDGE_FEATHER, v);
    float edgeOut = 1.0 - smoothstep(1.0 - EDGE_FEATHER, 1.0, v);
    float edge = edgeIn * edgeOut;

    // Tail fade (bright at head, dim at tail)
    float headToTail = saturate(1.0 - uv.x);
    float tailFade = pow(headToTail, TAIL_POWER);

    // Sample the base texture without scroll; we only use its alpha/shape and a touch of brightness.
    vec4 tex = texture(Sampler0, uv);

    // Inside main()
    float yFromCenter = abs(uv.y - 0.5) * 2.0;

    // Snapping the palette lookup to discrete steps (e.g., 8 steps)
    float steps = 8.0;
    float ySnapped = floor(yFromCenter * steps) / steps;
    vec3 pal = palette_vertical_fire(ySnapped);

    // Apply posterization to the mask to get "stepped" alpha/intensity
    float mask = tex.a * edge * tailFade * uStrength;
    mask = posterize(mask, 3.0, 0.5); // Snap to 6 levels of intensity

    // Keep a touch of texture brightness and vertex tint only as amplitude, not hue.
    float texBright = max(tex.r, max(tex.g, tex.b));
    vec3 baseAmp = vColor.rgb * mix(vec3(1.0), vec3(texBright), 0.35);
    vec3 mixed = pal * baseAmp; // fully adopt the vertical palette hue

    // Additive blend should be set in your RenderType.
    // We output color * mask; alpha is mostly ignored in additive but kept for consistency.
    vec3 rgb = mixed * mask;

    // Optional: kill near-zero fragments to reduce overdraw
    if (mask < 0.003) discard;

    fragColor = vec4(rgb, mask);
}
