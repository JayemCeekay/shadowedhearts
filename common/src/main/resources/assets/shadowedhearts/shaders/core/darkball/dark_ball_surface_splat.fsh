#version 150

in vec2 splatCoordinate;
in float bodyCoverage;
in float undeformedCoverage;
in vec3 cameraRelativePosition;
in float deformedSurface;
in float spikeTipStrength;
in float accumulationWeightScale;
in float hookStrength;
in float hookReach;
in float hookBend;
flat in int splatIdentity;

uniform float SplatDiagnosticMode;

out vec4 fragColor;

const float SPLAT_WEIGHT_CUTOFF = 0.0005;
const float MAX_ACCUMULATION_WEIGHT_SCALE = 4.0;
const float HIGH_SPIKE_TIP_WIDTH = 0.50;
const float PI = 3.14159265359;

float compactCoverageKernel(vec2 coordinate) {
    float radiusSquared = dot(coordinate, coordinate);
    if (radiusSquared >= 1.0) {
        return 0.0;
    }
    float remaining = 1.0 - radiusSquared;
    return remaining * remaining;
}

vec3 diagnosticIdentityColor(int identity) {
    float seed = float(identity) + 1.0;
    return 0.22 + 0.78 * fract(sin(
            vec3(seed * 12.9898,
                    seed * 78.233 + 11.17,
                    seed * 39.425 + 37.91))
            * 43758.5453);
}

void main() {
    float outwardTipProgress = smoothstep(
            -0.10, 1.0, splatCoordinate.y);
    float tipWidth = mix(
            1.0,
            HIGH_SPIKE_TIP_WIDTH,
            clamp(spikeTipStrength, 0.0, 1.0)
                    * outwardTipProgress);
    vec2 coreCoordinate = vec2(
            splatCoordinate.x / max(tipWidth, 0.0001),
            splatCoordinate.y);
    float coreKernel = compactCoverageKernel(coreCoordinate);

    // The hook is a connected curved capsule inside the same billboard. Its
    // center remains the monotonic sink center; only accumulated coverage
    // bends. Keeping the original core in the max union prevents holes and
    // detached surfels even at full collapse violence.
    float safeReach = max(hookReach, 0.0001);
    float closestLongitudinal = clamp(
            splatCoordinate.x, 0.0, safeReach);
    float hookProgress = closestLongitudinal / safeReach;
    float easedHookProgress = hookProgress
            * hookProgress
            * (3.0 - 2.0 * hookProgress);
    // A sine arch turns back toward the attachment axis at the tip, producing
    // a true crescent instead of the old diagonal tongue. The small signed
    // terminal drift keeps adjacent crescents from ending on one hard line.
    float crescentArc = sin(PI * hookProgress);
    float terminalCurl = smoothstep(
            0.58, 1.0, hookProgress);
    float curvedCenter = hookBend * (
            1.18 * crescentArc
                    + 0.18 * easedHookProgress
                    - 0.34 * terminalCurl);
    float hookWidth = mix(
            0.72, 0.16,
            smoothstep(0.18, 1.0, hookProgress));
    vec2 hookCoordinate = vec2(
            (splatCoordinate.x - closestLongitudinal)
                    / max(hookWidth * 0.82, 0.08),
            (splatCoordinate.y - curvedCenter)
                    / max(hookWidth, 0.08));
    float hookKernel = compactCoverageKernel(hookCoordinate)
            * clamp(hookStrength, 0.0, 1.0);
    float kernel = max(coreKernel, hookKernel);
    if (kernel <= 0.0) {
        discard;
    }

    // Emit an accumulation field, never final visible material. A later
    // bounded separable resolve combines overlapping surfels and only then
    // decodes coverage, remaining mass, and depth. This is what prevents each
    // curved footprint from becoming its own purple/gray outlined particle.
    float compensatedKernel = kernel
            * clamp(
                    accumulationWeightScale,
                    0.0,
                    MAX_ACCUMULATION_WEIGHT_SCALE);
    float currentWeight = bodyCoverage * compensatedKernel;
    if (currentWeight < SPLAT_WEIGHT_CUTOFF) {
        discard;
    }

    if (SplatDiagnosticMode > 1.5) {
        // This pass writes scalar linear ray distance into an R16F target while
        // the attached depth buffer selects the frontmost oriented footprint.
        // Reject feather-only fragments so an unsupported splat fringe cannot
        // become the depth authority for a fused interior pixel.
        float frontDepthSupport = smoothstep(
                0.018, 0.10, currentWeight);
        if (frontDepthSupport <= 0.01) {
            discard;
        }
        fragColor = vec4(
                max(length(cameraRelativePosition), 0.0001),
                0.0,
                0.0,
                1.0);
        return;
    }

    if (SplatDiagnosticMode > 0.5) {
        // One splat contributes 1/8 alpha at its center. RGB carries the same
        // premultiplied weight, allowing the preview to recover the average
        // stable identity color while alpha independently exposes overlap.
        float diagnosticWeight = pow(
                clamp(currentWeight, 0.0, 1.0), 0.35) * 0.125;
        fragColor = vec4(
                diagnosticIdentityColor(splatIdentity)
                        * diagnosticWeight,
                diagnosticWeight);
        return;
    }

    float remainingWeight =
            undeformedCoverage * compensatedKernel;
    float surfaceDistance =
            max(length(cameraRelativePosition), 0.0001);
    float inverseDepthMoment =
            1.0 / ((1.0 + surfaceDistance)
            * (1.0 + surfaceDistance));

    // Accumulation contract consumed only by
    // dark_ball_surface_splat_resolve:
    // R current weight, G remaining weight,
    // B bounded front-biased inverse-depth moment,
    // A deformed current weight.
    fragColor = vec4(
            currentWeight,
            remainingWeight,
            currentWeight * inverseDepthMoment,
            currentWeight * deformedSurface);
}
