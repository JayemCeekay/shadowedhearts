#version 150

uniform sampler2D Sampler0;
uniform vec2 Direction;
uniform int ResolveMode;
uniform float AccumulationGain;

in vec2 texCoord0;
out vec4 fragColor;

const float AURA_DEPTH_TAG_BIAS = 2.0;
const float MIN_ACCUMULATION = 0.0005;
// A single Gaussian billboard must not become a visible energy marble.
// Require overlapping surfel support before decoding the accumulation field
// into visible body material. Coherent silhouette regions remain dense while
// isolated late-collapse footprints disappear before the style composite.
const float MIN_FUSION_SUPPORT = 0.95;
const float FULL_FUSION_SUPPORT = 1.50;

vec4 filteredAccumulation() {
    // A compact three-tap tent still fuses adjacent surfels, but grows the
    // decoded silhouette by at most one source pixel per axis. The former
    // five-tap kernel made a large on-screen Pokemon look globally inflated.
    vec4 result = texture(Sampler0, texCoord0) * 0.50;
    result += texture(Sampler0, texCoord0 + Direction) * 0.25;
    result += texture(Sampler0, texCoord0 - Direction) * 0.25;
    return max(result, vec4(0.0));
}

void main() {
    vec4 accumulation = filteredAccumulation();
    if (ResolveMode == 0) {
        fragColor = accumulation;
        return;
    }

    float currentWeight = accumulation.r;
    if (currentWeight < MIN_ACCUMULATION) {
        discard;
    }

    float fusionSupport = smoothstep(
            MIN_FUSION_SUPPORT,
            FULL_FUSION_SUPPORT,
            currentWeight);
    if (fusionSupport <= 0.0001) {
        discard;
    }

    float safeGain = max(AccumulationGain, 0.0001);
    float currentCoverage = 1.0
            - exp(-currentWeight * safeGain);
    float remainingCoverage = 1.0
            - exp(-accumulation.g * safeGain);
    currentCoverage *= fusionSupport;
    remainingCoverage *= fusionSupport;

    if (ResolveMode == 2) {
        float encodedAmplitude = clamp(
                accumulation.a
                        / max(currentWeight, MIN_ACCUMULATION),
                0.0,
                1.0);
        // R retains resolved silhouette coverage and A carries the weighted
        // signed-amplitude encoding. B is intentionally zero so reference
        // storage cannot be mistaken for heatmap coverage.
        fragColor = vec4(
                clamp(currentCoverage, 0.0, 1.0),
                0.0,
                0.0,
                encodedAmplitude);
        return;
    }

    // Recover a front-biased generalized harmonic depth from the bounded
    // inverse-square moment. Unlike sum(weight * distance), this cannot
    // overflow RGBA16F when many surfels overlap during terminal collapse.
    float inverseDepthMean = max(
            accumulation.b / max(currentWeight, MIN_ACCUMULATION),
            0.0000001);
    float surfaceDistance = max(
            inversesqrt(inverseDepthMean) - 1.0,
            0.0001);
    float deformationRatio = accumulation.a
            / max(currentWeight, MIN_ACCUMULATION);
    float depthPayload = deformationRatio > 0.035
            ? -(AURA_DEPTH_TAG_BIAS + surfaceDistance)
            : surfaceDistance;

    // Decode into the established direct-material contract only after both
    // accumulation axes have been filtered:
    // R body coverage, G siphon coverage, B remaining/reference body,
    // A resolved depth or negative deformation tag.
    fragColor = vec4(
            clamp(currentCoverage, 0.0, 1.0),
            0.0,
            clamp(remainingCoverage, 0.0, 1.0),
            depthPayload);
}
