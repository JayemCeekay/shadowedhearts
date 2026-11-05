#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec2 materialUv;
in vec4 vertexColor;
in vec3 lightColor;
flat in int materialKind;
flat in float materialSeed;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 fraction = fract(point);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), fraction.x),
            mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0)), fraction.x), fraction.y);
}

float looping_noise(vec2 point) {
    // Eight-cell periodic noise keeps the flowing motion continuous across
    // Minecraft's 1200-second GameTime wrap. Each time displacement is 8*N.
    vec2 cell = floor(point);
    vec2 fraction = fract(point);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    return mix(mix(hash(mod(cell, 8.0)), hash(mod(cell + vec2(1.0, 0.0), 8.0)), fraction.x),
            mix(hash(mod(cell + vec2(0.0, 1.0), 8.0)), hash(mod(cell + vec2(1.0), 8.0)), fraction.x), fraction.y);
}

vec4 aperture(vec2 uv, float cycle) {
    // These fixed 16x16 cells and the irregular edge exactly match the baked
    // core texture. Animation cannot repaint the surrounding casing pixels.
    vec2 grid = (floor(clamp(uv, 0.0, 0.999999) * 16.0) + 0.5) / 16.0;
    vec2 point = grid - vec2(0.49, 0.51);
    float angle = atan(point.y, point.x);
    float radius = length(point);
    float boundary = 0.196 + 0.023 * sin(angle * 3.0 + 0.7) + 0.015 * sin(angle * 5.0 - 1.1);
    float distanceToHole = radius - boundary;
    float phase = materialSeed * 0.73;

    if (distanceToHole < 0.0) {
        vec2 q = uv - vec2(0.49, 0.51);
        vec2 flow = q * 9.0 + vec2(sin(cycle * 25.0 + phase + q.y * 8.0) * 0.9,
                GameTime * 8.0 * 10.0 + phase);
        // Keep the second octave's temporal component independent: scaling a
        // wrapped displacement by a fractional frequency would break its loop.
        float field = looping_noise(flow) + 0.38 * looping_noise(q * 18.9
                + vec2(-GameTime * 8.0 * 17.0 + phase, GameTime * 8.0 * 21.0));
        float veil = smoothstep(0.66, 1.25, field) * (0.25 + 0.75 * smoothstep(0.025, 0.2, radius));
        vec3 color = vec3(0.014, 0.010, 0.025) + vec3(0.070, 0.025, 0.106) * veil;
        color += vec3(0.055, 0.019, 0.082) * pow(clamp(radius / boundary, 0.0, 1.0), 7.0)
                * (0.45 + 0.25 * sin(angle * 4.0 + cycle * 52.0 + phase));
        return vec4(color, 1.0);
    }

    float broken = smoothstep(-0.25, 0.65, sin(angle * 3.2 + 0.7) + 0.42 * sin(angle * 7.0 - 1.8));
    float rim = (1.0 - smoothstep(0.015, 0.068, abs(distanceToHole - 0.018))) * broken;
    if (rim <= 0.001) discard;
    float breathe = 0.78 + 0.15 * sin(cycle * 92.0 + phase + angle * 2.0)
            + 0.07 * sin(cycle * 151.0 + phase - angle * 3.0);
    vec3 purple = mix(vec3(0.22, 0.13, 0.30), vec3(0.43, 0.25, 0.57), noise(grid * 23.0) * 0.48) * breathe;
    // The opaque model already contains the quiet violet rim; this thin layer
    // only supplies its breathing movement rather than doubling its strength.
    return vec4(purple, rim * 0.18);
}

vec4 plume(vec2 uv) {
    vec2 point = (uv - 0.5) * 2.0;
    // Faster billowing still uses integer cycles to preserve the GameTime wrap.
    float low = looping_noise(point * 2.3 + vec2(materialSeed * 0.73, GameTime * 8.0 * 9.0));
    float turn = 1.4 * low + 0.65 * length(point);
    mat2 rotation = mat2(cos(turn), -sin(turn), sin(turn), cos(turn));
    vec2 curled = rotation * point;
    vec2 warp = vec2(noise(curled * 3.0 + materialSeed), noise(curled * 3.2 - materialSeed + 8.0)) - 0.5;
    float billow = looping_noise(curled * 3.5 + warp * 1.2 + vec2(materialSeed, -GameTime * 8.0 * 16.0));
    float detail = noise(curled * 7.0 + warp * 1.8 + materialSeed);
    float envelope = 1.0 - smoothstep(0.36, 0.98, length(point) + 0.19 * (billow - 0.5));
    float density = envelope * (0.27 + 0.56 * billow + 0.17 * detail);
    float fold = smoothstep(0.42, 0.74, billow) * (1.0 - smoothstep(0.75, 0.94, billow));
    vec3 color = mix(vec3(0.018, 0.010, 0.031), vec3(0.15, 0.065, 0.23), fold * 0.64 + detail * 0.13);
    return vec4(color, density);
}

vec4 filament(vec2 uv) {
    // Geometry grows from a pointed leading end. UV x follows its full
    // tail-to-tip length; alpha never reveals a completed strand all at once.
    float along = uv.x;
    float across = abs(uv.y * 2.0 - 1.0);
    float field = looping_noise(vec2(along * 14.0 - GameTime * 8.0 * 89.0, materialSeed * 7.0));
    float contour = 0.71 + 0.21 * field;
    float alpha = 1.0 - smoothstep(contour - 0.18, contour, across);
    alpha *= smoothstep(0.0, 0.035, along) * (1.0 - smoothstep(0.97, 1.0, along));
    float border = smoothstep(contour - 0.37, contour - 0.17, across)
            * (1.0 - smoothstep(contour - 0.12, contour, across));
    float highlight = 0.38 + 0.62 * looping_noise(vec2(along * 8.0 - GameTime * 8.0 * 56.0, materialSeed * 4.7));
    vec3 color = mix(vec3(0.025, 0.013, 0.044), vec3(0.31, 0.135, 0.43), border * highlight);
    color += vec3(0.03, 0.009, 0.047) * field;
    return vec4(color, alpha * 0.90);
}

void main() {
    vec4 material;
    if (materialKind == 0) material = aperture(materialUv, GameTime * TAU);
    else if (materialKind == 1) material = plume(materialUv);
    else if (materialKind == 2) material = filament(materialUv);
    else discard;

    material *= vertexColor * ColorModulator;
    if (material.a <= 0.002) discard;
    // A restrained visibility floor keeps dark ink readable in the excavated
    // cavity while the lightmap still controls its response to local lighting.
    material.rgb *= 0.65 + 0.35 * clamp(lightColor, 0.0, 1.0);
    fragColor = linear_fog(material, vertexDistance, FogStart, FogEnd, FogColor);
}
