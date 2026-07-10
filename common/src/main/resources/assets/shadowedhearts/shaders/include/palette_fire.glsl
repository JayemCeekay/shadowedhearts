// Shared palette utilities used by ball_glow, ball_orb_glow, and ball_trail shaders.

float sh_luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 sh_quantize3D(vec3 p, float voxelsPerRad) {
    if (voxelsPerRad <= 0.0) return p;
    vec3 q = floor(p * voxelsPerRad + 0.5) / max(voxelsPerRad, 1.0);
    return q;
}

// Vertical-style 4-stop palette gradient.
// y is 0 at center and 1 at edge. Segments:
//  [0..t1]: c0 -> c1
//  [t1..t2]: c1 -> c2
//  [t2..t3]: c2 -> c3
vec3 palette_vertical_fire(
    float y,
    vec3 c0, vec3 c1, vec3 c2, vec3 c3,
    float t1, float t2, float t3,
    vec3 lumaCoeff, float paletteSaturation
) {
    // Defensive monotonic enforcement
    float t1b = max(0.0, min(t1, t2));
    float t2b = max(t1b, min(t2, t3));
    float t3b = max(t2b, max(t3, 0.00001));

    float k1 = smoothstep(0.0, t1b, y);
    vec3 m1 = mix(c0, c1, k1);
    float k2 = smoothstep(t1b, t2b, y);
    vec3 m2 = mix(c1, c2, k2);
    float k3 = smoothstep(t2b, t3b, y);
    vec3 m3 = mix(c2, c3, k3);

    float w1 = step(y, t1b);
    float w2 = step(t1b, y) * step(y, t2b);
    float w3 = step(t2b, y);
    vec3 c = m1 * w1 + m2 * w2 + m3 * w3;

    // Optional saturation control
    vec3 lcoef = (lumaCoeff.x + lumaCoeff.y + lumaCoeff.z <= 0.001)
               ? vec3(0.299, 0.587, 0.114)
               : lumaCoeff;
    float Y = dot(c, lcoef);
    c = mix(vec3(Y), c, clamp(paletteSaturation, 0.0, 1.0));
    return c;
}
