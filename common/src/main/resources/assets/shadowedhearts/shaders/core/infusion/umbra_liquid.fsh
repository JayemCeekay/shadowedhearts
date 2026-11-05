#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in vec3 localPosition;
in vec3 localRay;
in vec3 viewNormal;
in vec3 viewPosition;
in vec3 lightColor;
flat in vec3 boxSize;
flat in vec3 localEye;
flat in float tankSeed;

out vec4 fragColor;

const float TAU = 6.28318530718;
const int VOLUME_STEPS = 20;

vec3 safe_normalize(vec3 value, vec3 fallback) {
    float magnitude2 = dot(value, value);
    return magnitude2 > 0.00000001 ? value * inversesqrt(magnitude2) : fallback;
}

vec4 blob_field(vec3 point, vec3 center, vec3 radius) {
    vec3 delta = point - center;
    vec3 scaled = delta / radius;
    float kernel = max(1.0 - dot(scaled, scaled), 0.0);
    // Compact quadratic kernels join and separate smoothly. Their analytic
    // gradient avoids extra field samples for the curved, wet blob highlight.
    return vec4(-4.0 * kernel * delta / (radius * radius), kernel * kernel);
}

uint mix_bits(uint value) {
    value ^= value >> 16u;
    value *= 0x7feb352du;
    value ^= value >> 15u;
    value *= 0x846ca68bu;
    return value ^ (value >> 16u);
}

vec4 random_channels(uint seed) {
    // Keep the low 24 bits: they convert to a float exactly on GLSL 150.
    return vec4(mix_bits(seed ^ 0x68bc21ebu) & 0x00ffffffu,
            mix_bits(seed ^ 0x02e5be93u) & 0x00ffffffu,
            mix_bits(seed ^ 0x967a889bu) & 0x00ffffffu,
            mix_bits(seed ^ 0x368cc8b7u) & 0x00ffffffu) / 16777216.0;
}

void blob_layout(uint liquidSeed, uint blobIndex, float heightRatio,
        out vec3 origin, out vec3 radius, out vec4 phases, out vec4 rates,
        out vec4 motion, out vec3 pulse) {
    // Every blob gets independent channels for its persistent layout and
    // motion. No random state changes with time or with the ray sample index.
    uint seed = mix_bits(liquidSeed ^ ((blobIndex + 1u) * 0x9e3779b9u));
    vec4 placement = random_channels(seed ^ 0x243f6a88u);
    vec4 shaping = random_channels(seed ^ 0x85a308d3u);
    vec4 timing = random_channels(seed ^ 0x13198a2eu);
    vec4 wandering = random_channels(seed ^ 0x03707344u);
    vec4 breathing = random_channels(seed ^ 0xa4093822u);

    origin = vec3(mix(0.24, 0.76, placement.x), 0.50, mix(0.24, 0.76, placement.y));
    float baseRadius = mix(0.18, 0.29, placement.z);
    radius = vec3(baseRadius * mix(0.84, 1.16, shaping.x),
            min(baseRadius * heightRatio * mix(1.05, 1.65, placement.w), 0.65),
            baseRadius * mix(0.84, 1.16, shaping.y));
    phases = vec4(shaping.zw, wandering.xy) * TAU;
    // Integer frequencies preserve the 1200-second Minecraft time wrap.
    rates = floor(mix(vec4(28.0, 64.0, 6.0, 6.0), vec4(61.0, 121.0, 25.0, 25.0), timing));
    motion = vec4(mix(vec2(0.035), vec2(0.13), wandering.zw),
            mix(0.31, 0.40, breathing.w), mix(0.022, 0.045, breathing.z));
    // Keep center paths within the filled box, including the shallow fill.
    motion.xy = min(motion.xy, min(origin.xz - vec2(0.13), vec2(0.87) - origin.xz));
    pulse = vec3(breathing.x * TAU, floor(mix(12.0, 37.0, breathing.y)), mix(0.05, 0.12, breathing.z));
}

void moving_blob(uint liquidSeed, uint blobIndex, float cycle, float heightRatio,
        out vec3 center, out vec3 radius) {
    vec3 origin;
    vec4 phases;
    vec4 rates;
    vec4 motion;
    vec3 pulse;
    blob_layout(liquidSeed, blobIndex, heightRatio, origin, radius, phases, rates, motion, pulse);
    center = origin + vec3(motion.x * sin(cycle * rates.z + phases.z),
            motion.z * sin(cycle * rates.x + phases.x) + motion.w * sin(cycle * rates.y + phases.y),
            motion.y * cos(cycle * rates.w + phases.w));
    radius *= 1.0 + pulse.z * sin(cycle * pulse.y + pulse.x);
    radius.y = min(radius.y, 0.65);
}

float filtered_sine(float phase) {
    return sin(phase) * (1.0 - smoothstep(0.0, TAU, fwidth(phase)));
}

float bubble_ring(vec2 point, vec2 center, float phase, float footprint) {
    float life = fract(phase);
    float radius = mix(0.025, 0.18, life);
    float distanceToRing = abs(length(point - center) - radius);
    float thickness = max(0.009, footprint);
    float ring = 1.0 - smoothstep(thickness, thickness * 2.0, distanceToRing);
    // The ring is invisible at its phase wrap, including derivative filtering.
    return ring * smoothstep(0.0, 0.15, life) * (1.0 - smoothstep(0.45, 0.92, life));
}

void main() {
    vec3 ray = safe_normalize(localRay, vec3(0.0, 0.0, 1.0));
    vec3 safeRay = vec3(
            abs(ray.x) > 0.000001 ? ray.x : (ray.x < 0.0 ? -0.000001 : 0.000001),
            abs(ray.y) > 0.000001 ? ray.y : (ray.y < 0.0 ? -0.000001 : 0.000001),
            abs(ray.z) > 0.000001 ? ray.z : (ray.z < 0.0 ? -0.000001 : 0.000001));
    vec3 nearPlane = -localEye / safeRay;
    vec3 farPlane = (boxSize - localEye) / safeRay;
    vec3 intervalMin = min(nearPlane, farPlane);
    vec3 intervalMax = max(nearPlane, farPlane);
    float entry = max(max(intervalMin.x, intervalMin.y), max(intervalMin.z, 0.0));
    float exit = min(min(intervalMax.x, intervalMax.y), intervalMax.z);
    float travel = max(exit - entry, 0.0);

    vec3 normalizedPosition = localPosition / boxSize;
    vec3 footprint3 = fwidth(normalizedPosition);
    float footprint = max(footprint3.x, max(footprint3.y, footprint3.z));
    float detail = 1.0 - smoothstep(0.025, 0.18, footprint);
    // Minecraft wraps GameTime every 1200 seconds. Integer cycle counts keep
    // every blob, pulse and bubble continuous at that wrap.
    float cycle = GameTime * TAU;
    uint liquidSeed = uint(clamp(tankSeed, 0.0, 1.0) * 16777216.0);
    // XZ radii are fractions of the physical tank width. Convert their height
    // into normalized fill coordinates so filling the tank does not stretch
    // tall columns into flat, face-specific UV patterns.
    float heightRatio = boxSize.x / boxSize.y;
    vec3 center0;
    vec3 center1;
    vec3 center2;
    vec3 center3;
    vec3 shape0;
    vec3 shape1;
    vec3 shape2;
    vec3 shape3;
    moving_blob(liquidSeed, 0u, cycle, heightRatio, center0, shape0);
    moving_blob(liquidSeed, 1u, cycle, heightRatio, center1, shape1);
    moving_blob(liquidSeed, 2u, cycle, heightRatio, center2, shape2);
    moving_blob(liquidSeed, 3u, cycle, heightRatio, center3, shape3);

    vec3 localView = -ray;
    vec3 blobLight = safe_normalize(vec3(-0.45, 0.80, 0.50), vec3(0.0, 1.0, 0.0));
    vec3 blobHalfway = safe_normalize(blobLight + localView, blobLight);
    float stepLength = travel / float(VOLUME_STEPS);
    float opticalStep = stepLength / boxSize.x;
    vec3 accumulated = vec3(0.0);
    float transmittance = 1.0;
    for (int i = 0; i < VOLUME_STEPS; i++) {
        vec3 point = (localEye + ray * (entry + (float(i) + 0.5) * stepLength)) / boxSize;
        vec4 field = blob_field(point, center0, shape0)
                + blob_field(point, center1, shape1)
                + blob_field(point, center2, shape2)
                + blob_field(point, center3, shape3);
        float body = smoothstep(0.09, 0.40, field.w);
        float edge = smoothstep(0.035, 0.13, field.w) * (1.0 - smoothstep(0.24, 0.55, field.w));
        // Gradient coordinates were normalized independently in each axis.
        vec3 blobNormal = safe_normalize(-field.xyz / boxSize, vec3(0.0, 1.0, 0.0));
        float gloss = pow(max(dot(blobNormal, blobHalfway), 0.0), 20.0) * edge * detail;
        float rim = pow(1.0 - abs(dot(blobNormal, localView)), 3.0) * edge * detail;
        vec3 sampleColor = vec3(0.009, 0.005, 0.019)
                + vec3(0.125, 0.025, 0.230) * edge
                + vec3(0.034, 0.010, 0.064) * body
                + vec3(0.32, 0.17, 0.47) * gloss
                + vec3(0.10, 0.030, 0.19) * rim;
        float sampleAlpha = 1.0 - exp(-(0.68 + body * 2.8) * opticalStep);
        accumulated += transmittance * sampleAlpha * sampleColor;
        transmittance *= 1.0 - sampleAlpha;
    }

    float volumeAlpha = 1.0 - transmittance;
    vec3 color = accumulated / max(volumeAlpha, 0.0001);
    vec3 surfaceNormal = safe_normalize(viewNormal, vec3(0.0, 1.0, 0.0));
    vec3 surfaceView = safe_normalize(-viewPosition, vec3(0.0, 0.0, 1.0));
    vec3 surfaceLight = safe_normalize(vec3(-0.4, 0.75, 0.55), vec3(0.0, 1.0, 0.0));
    vec3 surfaceHalfway = safe_normalize(surfaceLight + surfaceView, surfaceLight);
    float reflected = max(dot(surfaceNormal, surfaceHalfway), 0.0);
    float wetGloss = pow(reflected, 64.0) / (1.0 + fwidth(reflected) * 64.0);
    float fresnel = pow(1.0 - abs(dot(surfaceNormal, surfaceView)), 4.0);

    float top = smoothstep(0.975, 1.0, normalizedPosition.y);
    float ripple = filtered_sine(normalizedPosition.x * 18.0 + normalizedPosition.z * 11.0 + cycle * 48.0)
            * filtered_sine(normalizedPosition.z * 15.0 - normalizedPosition.x * 9.0 - cycle * 40.0);
    vec4 bubbleLayout0 = random_channels(liquidSeed ^ 0x082efa98u);
    vec4 bubbleLayout1 = random_channels(liquidSeed ^ 0xec4e6c89u);
    vec4 bubbleMotion = random_channels(liquidSeed ^ 0x452821e6u);
    vec2 bubble0 = mix(vec2(0.24), vec2(0.76), bubbleLayout0.xy)
            + 0.055 * vec2(sin(cycle * floor(mix(8.0, 21.0, bubbleMotion.x)) + bubbleLayout0.z * TAU),
                    cos(cycle * floor(mix(8.0, 21.0, bubbleMotion.y)) + bubbleLayout0.w * TAU));
    vec2 bubble1 = mix(vec2(0.24), vec2(0.76), bubbleLayout1.xy)
            + 0.055 * vec2(sin(cycle * floor(mix(8.0, 21.0, bubbleMotion.z)) + bubbleLayout1.z * TAU),
                    cos(cycle * floor(mix(8.0, 21.0, bubbleMotion.w)) + bubbleLayout1.w * TAU));
    float bubbles = bubble_ring(normalizedPosition.xz, bubble0,
            GameTime * floor(mix(100.0, 181.0, bubbleLayout0.w)) + bubbleLayout0.z, footprint)
            + bubble_ring(normalizedPosition.xz, bubble1,
                    GameTime * floor(mix(100.0, 181.0, bubbleLayout1.w)) + bubbleLayout1.z, footprint);
    color += vec3(0.10, 0.065, 0.16) * wetGloss
            + vec3(0.035, 0.010, 0.065) * fresnel
            + vec3(0.055, 0.017, 0.11) * top * (0.3 + 0.3 * ripple + bubbles * detail);
    color *= mix(vec3(0.65), clamp(lightColor, 0.0, 1.0), 0.35) * ColorModulator.rgb;

    // Give the shadow liquid a dense base, with a little translucency retained
    // for the floating contents even along long diagonal rays.
    float alpha = clamp(0.65 + volumeAlpha * 0.22 + fresnel * 0.025, 0.65, 0.94) * ColorModulator.a;
    fragColor = linear_fog(vec4(color, alpha), vertexDistance, FogStart, FogEnd, FogColor);
}
