#version 150

uniform sampler2D Sampler0;
uniform sampler2D MaskSampler;
uniform sampler2D DistanceSampler;
uniform float DeformationBlend;
uniform float DepthHighlightBlend;
uniform float BodyGrayTransitionFraction;
uniform float BodyContourSheenStrength;
uniform float BodyDepthSpecularStrength;
uniform float GameTime;
uniform int DeformationDebugMode;
uniform int DistanceFieldAvailable;
uniform vec2 ScreenSize;
uniform float ProjectedBodyRadiusPixels;
uniform mat4 InvProjMat;
uniform int SurfaceDepthAvailable;
uniform float SurfaceDepthScale;

in vec2 texCoord0;
out vec4 fragColor;

const float PURPLE_RIM_REACH_OUTPUT_PIXELS = 4.0;
const float PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS = 1.5;
const float SIPHON_GRAY_TRANSITION_FRACTION = 0.40;
const float MIN_GRAY_TRANSITION_OUTPUT_PIXELS = 1.15;
const float MAX_GRAY_TRANSITION_OUTPUT_PIXELS = 96.0;
const float THIN_FEATURE_MAX_GRAY_TRANSITION_OUTPUT_PIXELS = 5.0;
const float THIN_FEATURE_MIN_INSIDE_RIM_OUTPUT_PIXELS = 0.85;
const float SIPHON_FALLBACK_GRAY_TRANSITION_OUTPUT_PIXELS = 1.05;

float displayedBodyCoverage(vec4 material) {
    // The edge resolve has already produced the exact texture-clipped core and
    // unioned the legitimate depth-resolved 3D aura shell into R. Never blend
    // raw voxel channel B back into visible body coverage here.
    return material.r;
}

float bodyStorageCoverage(float encodedStorage) {
    // Values above the signed body-storage range carry a siphon-only radius.
    return encodedStorage > 1.5 ? 0.0 : encodedStorage;
}

float coverageAt(vec2 uv) {
    vec2 bounded = clamp(uv, vec2(0.0), vec2(1.0));
    vec4 material = texture(Sampler0, bounded);
    return max(displayedBodyCoverage(material), material.g);
}

vec3 safeNormalize3(vec3 value, vec3 fallback) {
    float lengthSquared = dot(value, value);
    return lengthSquared > 0.00000000000000000001
            ? value * inversesqrt(lengthSquared) : fallback;
}

vec3 projectionEyeView() {
    vec4 eye = InvProjMat * vec4(0.0, 0.0, 1.0, 0.0);
    return abs(eye.w) > 0.000001 ? eye.xyz / eye.w : vec3(0.0);
}

vec3 viewRayAt(vec2 uv) {
    vec4 view = InvProjMat * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    view.xyz *= abs(view.w) > 0.000001 ? 1.0 / view.w : 1.0;
    return safeNormalize3(view.xyz - projectionEyeView(),
            vec3(0.0, 0.0, -1.0));
}

vec2 texelCenterUv(ivec2 texelCoord, ivec2 textureExtent) {
    return (vec2(texelCoord) + vec2(0.5)) / vec2(textureExtent);
}

float rawSurfaceDepthValidity(vec4 rawMaterial) {
    float available = SurfaceDepthAvailable != 0 ? 1.0 : 0.0;
    // A is negative for conservative body coverage without an SDF crossing.
    // Positive first-hit depth may belong to either the body in R or siphon in
    // G; B-only storage remains excluded.
    return available * step(0.002, max(rawMaterial.r, rawMaterial.g))
            * step(0.0001, rawMaterial.a);
}

float connectedSurfaceDepthValidity(vec4 rawMaterial,
        float centerDepth, float continuityLimit) {
    float depthDelta = abs(rawMaterial.a - centerDepth);
    float continuity = 1.0 - smoothstep(continuityLimit,
            continuityLimit * 1.65, depthDelta);
    return rawSurfaceDepthValidity(rawMaterial) * continuity;
}

vec4 connectedSurfacePosition(ivec2 texelCoord, ivec2 textureExtent,
        ivec2 maximumCoord, float centerDepth, float continuityLimit) {
    ivec2 boundedCoord = clamp(texelCoord, ivec2(0), maximumCoord);
    vec4 rawMaterial = texelFetch(Sampler0, boundedCoord, 0);
    float validity = connectedSurfaceDepthValidity(rawMaterial,
            centerDepth, continuityLimit);
    if (validity <= 0.0001) {
        return vec4(0.0);
    }
    vec3 viewPosition = projectionEyeView()
            + viewRayAt(texelCenterUv(boundedCoord,
                    textureExtent)) * max(rawMaterial.a, 0.0);
    return vec4(viewPosition * validity, validity);
}

vec4 connectedBodySurfacePosition(ivec2 texelCoord, ivec2 textureExtent,
        ivec2 maximumCoord, float centerDepth, float continuityLimit) {
    ivec2 boundedCoord = clamp(texelCoord, ivec2(0), maximumCoord);
    vec4 rawMaterial = texelFetch(Sampler0, boundedCoord, 0);
    float bodyAuthority = smoothstep(0.055, 0.20, rawMaterial.r)
            * (1.0 - smoothstep(0.02, 0.12, rawMaterial.g));
    float validity = connectedSurfaceDepthValidity(rawMaterial,
            centerDepth, continuityLimit) * bodyAuthority;
    if (validity <= 0.0001) {
        return vec4(0.0);
    }
    vec3 viewPosition = projectionEyeView()
            + viewRayAt(texelCenterUv(boundedCoord,
                    textureExtent)) * max(rawMaterial.a, 0.0);
    return vec4(viewPosition * validity, validity);
}

float silhouetteBoundaryDistanceOutputPixels(vec2 uv,
        out float seedFound, out vec2 nearestSeedUv) {
    ivec2 fieldExtent = textureSize(DistanceSampler, 0);
    vec2 fieldTexel = 1.0 / max(vec2(fieldExtent), vec2(1.0));
    float bestDistance = 1.0e30;
    seedFound = 0.0;
    nearestSeedUv = vec2(-1.0);

    // The jump-flood result is half resolution. Inspect the local Voronoi
    // neighborhood so full-resolution pixels choose the closest propagated
    // seed rather than inheriting one nearest-neighbor cell wholesale.
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 sampleUv = clamp(uv
                    + vec2(float(x), float(y)) * fieldTexel,
                    vec2(0.0), vec2(1.0));
            vec2 seedUv = texture(DistanceSampler, sampleUv).rg;
            float validSeed = step(0.0, min(seedUv.x, seedUv.y));
            if (validSeed > 0.5) {
                float candidateDistance = length(
                        (seedUv - uv) * ScreenSize);
                if (candidateDistance < bestDistance) {
                    bestDistance = candidateDistance;
                    seedFound = 1.0;
                    nearestSeedUv = seedUv;
                }
            }
        }
    }
    return seedFound > 0.5 ? bestDistance : 1.0e30;
}

float boundaryDistanceAlongDirection(vec2 originUv, vec2 direction,
        vec2 texel, float searchReachPixels, float centerCoverage,
        out float boundaryFound) {
    // The visible exact mask is a projected union, so coverage along a ray is
    // not guaranteed to be monotonic. A horn can exit into empty space and
    // then enter an overlapping face before the old far probe. Bracket the
    // first exit explicitly; later re-entry must not erase the real contour.
    if (centerCoverage < 0.5) {
        boundaryFound = 1.0;
        return 0.0;
    }

    float insideDistancePixels = 0.0;
    float outsideDistancePixels = searchReachPixels;
    float insideCoverage = centerCoverage;
    float outsideCoverage = centerCoverage;
    float previousDistancePixels = 0.0;
    float previousCoverage = centerCoverage;
    boundaryFound = 0.0;
    for (int probeIndex = 1; probeIndex <= 8; probeIndex++) {
        float probeDistancePixels = searchReachPixels
                * (float(probeIndex) / 8.0);
        float probeCoverage = coverageAt(originUv
                + direction * texel * probeDistancePixels);
        if (boundaryFound < 0.5
                && previousCoverage >= 0.5
                && probeCoverage < 0.5) {
            insideDistancePixels = previousDistancePixels;
            outsideDistancePixels = probeDistancePixels;
            insideCoverage = previousCoverage;
            outsideCoverage = probeCoverage;
            boundaryFound = 1.0;
        }
        previousDistancePixels = probeDistancePixels;
        previousCoverage = probeCoverage;
    }
    if (boundaryFound < 0.5) {
        return searchReachPixels;
    }

    for (int searchIndex = 0; searchIndex < 4; searchIndex++) {
        float midpointDistancePixels = (insideDistancePixels
                + outsideDistancePixels) * 0.5;
        float midpointCoverage = coverageAt(originUv
                + direction * texel * midpointDistancePixels);
        if (midpointCoverage >= 0.5) {
            insideDistancePixels = midpointDistancePixels;
            insideCoverage = midpointCoverage;
        } else {
            outsideDistancePixels = midpointDistancePixels;
            outsideCoverage = midpointCoverage;
        }
    }
    float crossingBlend = clamp((insideCoverage - 0.5)
            / max(insideCoverage - outsideCoverage, 0.0001), 0.0, 1.0);
    return mix(insideDistancePixels, outsideDistancePixels, crossingBlend);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float animatedGrain(vec2 seed) {
    float phase = GameTime * 5.0;
    float frame = floor(phase);
    float blend = smoothstep(0.08, 0.92, fract(phase));
    float first = hash12(seed + vec2(frame * 13.7, frame * 7.9));
    float second = hash12(seed + vec2((frame + 1.0) * 13.7,
            (frame + 1.0) * 7.9));
    return mix(first, second, blend);
}

vec3 signedDebugRamp(float encoded) {
    float signedValue = clamp(encoded * 2.0 - 1.0, -1.0, 1.0);
    vec3 neutral = vec3(0.86);
    vec3 negative = vec3(0.08, 0.34, 1.0);
    vec3 positive = vec3(1.0, 0.16, 0.06);
    return signedValue < 0.0
            ? mix(neutral, negative, -signedValue)
            : mix(neutral, positive, signedValue);
}

vec3 scalarDebugRamp(float value) {
    value = clamp(value, 0.0, 1.0);
    vec3 low = vec3(0.015, 0.025, 0.060);
    vec3 middle = vec3(0.08, 0.78, 0.86);
    vec3 high = vec3(1.0, 0.86, 0.10);
    return value < 0.5
            ? mix(low, middle, value * 2.0)
            : mix(middle, high, (value - 0.5) * 2.0);
}

vec3 spikeIndentAmplitudeDebugColor(float encodedAmplitude) {
    float amplitude = clamp(
            encodedAmplitude * 2.0 - 1.0,
            -1.0,
            1.0);
    float magnitude = smoothstep(0.015, 1.0, abs(amplitude));
    vec3 neutral = vec3(0.018, 0.020, 0.026);
    vec3 indentation = vec3(0.02, 0.68, 1.00);
    vec3 spike = vec3(1.00, 0.10, 0.015);
    vec3 polarity = amplitude < 0.0 ? indentation : spike;
    return mix(neutral, polarity, magnitude);
}

vec3 fieldOwnershipDebugColor(float encodedOwner) {
    if (encodedOwner < 0.25) {
        // Fully warped detailed Pokemon SDF.
        return vec3(0.00, 0.78, 1.00);
    }
    if (encodedOwner < 0.50) {
        // Exact, unwarped captured-model core SDF.
        return vec3(1.00, 0.02, 0.52);
    }
    if (encodedOwner < 0.75) {
        // Fully warped depletion/release intersection.
        return vec3(1.00, 0.82, 0.03);
    }
    // No first crossing was resolved; coverage came from proximity fallback.
    return vec3(0.46, 0.48, 0.52);
}

void main() {
    vec4 material = texture(Sampler0, texCoord0);
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    if (DeformationDebugMode > 0) {
        // Mode 12 excludes B: that channel is the undeformed formation-handoff
        // projection and would be mistaken for one of the current SDF owners.
        float storedBodyCoverage = bodyStorageCoverage(material.b);
        float bodyDebugCoverage = (DeformationDebugMode == 12
                || DeformationDebugMode == 13)
                ? material.r : max(material.r, storedBodyCoverage);
        float debugOpacity = max(bodyDebugCoverage, material.g);
        if (debugOpacity < 0.002) {
            discard;
        }

        vec3 debugColor;
        if (DeformationDebugMode == 1) {
            debugColor = vec3(storedBodyCoverage);
        } else if (DeformationDebugMode == 2) {
            debugColor = vec3(material.r);
        } else if (DeformationDebugMode == 12) {
            debugColor = fieldOwnershipDebugColor(material.a);
        } else if (DeformationDebugMode == 13) {
            debugColor = spikeIndentAmplitudeDebugColor(material.a);
        } else if (DeformationDebugMode == 4
                || DeformationDebugMode == 5
                || DeformationDebugMode == 10
                || DeformationDebugMode == 11) {
            debugColor = scalarDebugRamp(material.a);
        } else {
            debugColor = signedDebugRamp(material.a);
        }
        float siphonOnly = smoothstep(bodyDebugCoverage,
                bodyDebugCoverage + 0.08, material.g);
        debugColor = mix(debugColor, vec3(0.08, 1.0, 0.28), siphonOnly);
        fragColor = vec4(debugColor, clamp(debugOpacity, 0.0, 0.98));
        return;
    }

    // A is a linear camera-ray distance written by a resolved crossing of the
    // final deformed/depleted body SDF or the siphon surface. Fetch it without
    // filtering: linear interpolation would blend the negative invalid sentinel
    // into plausible depths along conservative-fill and silhouette boundaries.
    ivec2 depthExtent = textureSize(Sampler0, 0);
    ivec2 maximumDepthCoord = max(depthExtent - ivec2(1), ivec2(0));
    ivec2 centerDepthCoord = clamp(
            ivec2(texCoord0 * vec2(depthExtent)), ivec2(0),
            maximumDepthCoord);

    vec4 centerDepthMaterial = texelFetch(Sampler0, centerDepthCoord, 0);
    float surfaceDepth = centerDepthMaterial.a;
    float centerDepthValid = rawSurfaceDepthValidity(centerDepthMaterial);
    float depthHighlight = 0.0;
    // Body depth remains authoritative for ordering, but its reconstructed
    // screen-space normal used to turn broad Pokemon surfaces into moving
    // specular/Fresnel facets. Evaluate the expensive view-dependent lighting
    // only for the narrow siphon, whose restrained glint remains readable.
    if (centerDepthValid > 0.001
            && centerDepthMaterial.g > centerDepthMaterial.r + 0.02) {
    bool siphonDominant = centerDepthMaterial.g
            > centerDepthMaterial.r + 0.02;
    // A siphon can double back over itself within the body's broad depth
    // tolerance. Keep its normal neighborhood tighter so separate tube spans
    // cannot manufacture a false cross-surface highlight.
    float depthContinuityLimit = siphonDominant
            ? max(SurfaceDepthScale * 0.05, 0.04)
            : max(SurfaceDepthScale * 0.18, 0.06);
    int derivativeOffset = siphonDominant ? 1 : 2;
    vec3 eyeView = projectionEyeView();
    vec3 centerViewPosition = eyeView
            + viewRayAt(texelCenterUv(centerDepthCoord,
                    depthExtent)) * max(surfaceDepth, 0.0);

    // Estimate each side from a three-sample strip two texels away. This is a
    // compact bilateral cross filter: invalid depths and discontinuous lobes
    // contribute no weight, while coherent neighbors are averaged before the
    // central difference is formed. It avoids the previous pixel-to-pixel
    // winner flip between left/right or up/down one-sided derivatives. Thin
    // siphons use a one-texel derivative while the broader body retains the
    // smoother two-texel stencil.
    vec4 leftSurface = connectedSurfacePosition(centerDepthCoord
            + ivec2(-derivativeOffset, -1), depthExtent, maximumDepthCoord,
            surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(-derivativeOffset, 0),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(-derivativeOffset, 1),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit);
    vec4 rightSurface = connectedSurfacePosition(centerDepthCoord
            + ivec2(derivativeOffset, -1), depthExtent, maximumDepthCoord,
            surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(derivativeOffset, 0),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(derivativeOffset, 1),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit);
    vec4 downSurface = connectedSurfacePosition(centerDepthCoord
            + ivec2(-1, -derivativeOffset), depthExtent, maximumDepthCoord,
            surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(0, -derivativeOffset),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(1, -derivativeOffset),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit);
    vec4 upSurface = connectedSurfacePosition(centerDepthCoord
            + ivec2(-1, derivativeOffset), depthExtent, maximumDepthCoord,
            surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(0, derivativeOffset),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit)
            + connectedSurfacePosition(centerDepthCoord
            + ivec2(1, derivativeOffset),
            depthExtent, maximumDepthCoord, surfaceDepth,
            depthContinuityLimit);

    vec3 leftViewPosition = leftSurface.xyz / max(leftSurface.w, 0.001);
    vec3 rightViewPosition = rightSurface.xyz / max(rightSurface.w, 0.001);
    vec3 downViewPosition = downSurface.xyz / max(downSurface.w, 0.001);
    vec3 upViewPosition = upSurface.xyz / max(upSurface.w, 0.001);
    float leftDepthValid = clamp(leftSurface.w / 1.5, 0.0, 1.0);
    float rightDepthValid = clamp(rightSurface.w / 1.5, 0.0, 1.0);
    float downDepthValid = clamp(downSurface.w / 1.5, 0.0, 1.0);
    float upDepthValid = clamp(upSurface.w / 1.5, 0.0, 1.0);

    vec3 tangentX = vec3(0.0);
    if (leftDepthValid > 0.001 && rightDepthValid > 0.001) {
        tangentX = rightViewPosition - leftViewPosition;
    } else if (rightDepthValid > 0.001) {
        tangentX = rightViewPosition - centerViewPosition;
    } else if (leftDepthValid > 0.001) {
        tangentX = centerViewPosition - leftViewPosition;
    }
    vec3 tangentY = vec3(0.0);
    if (downDepthValid > 0.001 && upDepthValid > 0.001) {
        tangentY = upViewPosition - downViewPosition;
    } else if (upDepthValid > 0.001) {
        tangentY = upViewPosition - centerViewPosition;
    } else if (downDepthValid > 0.001) {
        tangentY = centerViewPosition - downViewPosition;
    }

    vec3 rawSurfaceNormal = cross(tangentX, tangentY);
    float normalAreaValid = step(0.00000000000001,
            dot(rawSurfaceNormal, rawSurfaceNormal));
    vec3 viewDirection = safeNormalize3(eyeView - centerViewPosition,
            vec3(0.0, 0.0, 1.0));
    vec3 surfaceNormal = safeNormalize3(rawSurfaceNormal, viewDirection);
    surfaceNormal *= dot(surfaceNormal, viewDirection) < 0.0 ? -1.0 : 1.0;
    float normalValidity = centerDepthValid
            * max(leftDepthValid, rightDepthValid)
            * max(downDepthValid, upDepthValid)
            * normalAreaValid;

    // A cool view-space key produces restrained interior crescents and
    // reserves the strongest response for upper-left-facing surfaces.
    vec3 lightDirection = safeNormalize3(vec3(-0.52, 0.68, 0.56),
            vec3(0.0, 0.0, 1.0));
    vec3 halfDirection = safeNormalize3(lightDirection + viewDirection,
            viewDirection);
    float normalDotView = clamp(dot(surfaceNormal, viewDirection), 0.0, 1.0);
    float normalDotHalf = clamp(dot(surfaceNormal, halfDirection), 0.0, 1.0);
    float keyedSide = smoothstep(-0.12, 0.58,
            dot(surfaceNormal, lightDirection));
    float broadSpecular = pow(normalDotHalf, 14.0) * keyedSide;
    float crescentSpecular = pow(1.0 - normalDotView, 1.85) * keyedSide;
    depthHighlight = clamp(broadSpecular * 0.70
            + crescentSpecular * 0.78, 0.0, 1.0) * normalValidity;
    }

    // Resolve a single body+siphon union. Because the inlet is already merged
    // into this mask, no independent purple cap can be drawn over the body.
    float center = coverageAt(texCoord0);
    float nearMaximum = center;
    float nearMinimum = center;
    float innerMinimum = center;
    float nearSupport = 0.0;
    float innerSupport = 0.0;
    float escapeWeights[12];
    float strongestEscapeWeight = 0.0;
    vec2 strongestEscapeDirection = vec2(1.0, 0.0);
    // Define all fixed contour widths in final display pixels. The density
    // target may run below the main framebuffer resolution on LOW (or a future
    // adaptive profile), so convert them into source-pixel units before using
    // texture-size-relative UV steps. This keeps the shared body+siphon rim at
    // four visible pixels instead of accidentally doubling it after upsampling.
    float outputPixelsToSourcePixels =
            float(textureSize(Sampler0, 0).y) / max(ScreenSize.y, 1.0);
    float rimReachPixels = PURPLE_RIM_REACH_OUTPUT_PIXELS
            * outputPixelsToSourcePixels;
    float outsideRimReachPixels = PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS
            * outputPixelsToSourcePixels;
    float insideRimReachPixels = max(
            rimReachPixels - outsideRimReachPixels, 0.0);
    float minimumGrayTransitionPixels =
            MIN_GRAY_TRANSITION_OUTPUT_PIXELS * outputPixelsToSourcePixels;
    float maximumGrayTransitionPixels =
            MAX_GRAY_TRANSITION_OUTPUT_PIXELS * outputPixelsToSourcePixels;
    float thinFeatureMaximumGrayTransitionPixels =
            THIN_FEATURE_MAX_GRAY_TRANSITION_OUTPUT_PIXELS
                    * outputPixelsToSourcePixels;
    float siphonFallbackGrayTransitionPixels =
            SIPHON_FALLBACK_GRAY_TRANSITION_OUTPUT_PIXELS
                    * outputPixelsToSourcePixels;

    // The body uses an explicit presentation fraction instead of borrowing the
    // collapse envelope. This preserves the failure path's thick black core
    // from the first exact-mask frame through the surfel handoff. The clamp
    // protects small details and extreme closeups. Siphon-only pixels retain
    // the established proportional treatment.
    float bodyGrayTransitionPixels = clamp(
            ProjectedBodyRadiusPixels * BodyGrayTransitionFraction,
            minimumGrayTransitionPixels,
            maximumGrayTransitionPixels);
    float bodyInnerReachPixels = insideRimReachPixels
            + bodyGrayTransitionPixels;
    float encodedSiphonRadius = centerDepthMaterial.b;
    float siphonWorldRadius = encodedSiphonRadius > 1.5
            ? encodedSiphonRadius - 2.0 : 0.0;
    vec3 siphonViewPosition = projectionEyeView()
            + viewRayAt(texCoord0) * max(surfaceDepth, 0.0);
    float verticalProjectionDenominator =
            max(abs(siphonViewPosition.z)
                    * max(abs(InvProjMat[1][1]), 0.0001), 0.0001);
    float projectedSiphonRadiusPixels = siphonWorldRadius
            * (float(textureSize(Sampler0, 0).y) * 0.5)
            / verticalProjectionDenominator;
    float siphonRadiusAvailable = step(0.0001, siphonWorldRadius)
            * centerDepthValid;
    float siphonGrayTransitionPixels = clamp(
            projectedSiphonRadiusPixels * SIPHON_GRAY_TRANSITION_FRACTION,
            minimumGrayTransitionPixels,
            maximumGrayTransitionPixels);
    float proportionalSiphonInnerReachPixels =
            insideRimReachPixels
            + siphonGrayTransitionPixels;
    float siphonFallbackInnerReachPixels =
            insideRimReachPixels
            + siphonFallbackGrayTransitionPixels;
    float siphonInnerReachPixels = mix(
            siphonFallbackInnerReachPixels,
            proportionalSiphonInnerReachPixels,
            siphonRadiusAvailable);
    float siphonDominance = smoothstep(
            displayedBodyCoverage(material) + 0.02,
            displayedBodyCoverage(material) + 0.10,
            material.g);
    float innerReachPixels = mix(bodyInnerReachPixels,
            siphonInnerReachPixels, siphonDominance);
    vec2 nearStep = texel * outsideRimReachPixels;
    vec2 innerStep = texel * innerReachPixels;
    vec2 directions[8] = vec2[8](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0),
        vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
        vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071)
    );
    // Long-range boundary selection needs locally finer angular resolution
    // than the eight-tap morphology ring. At projected ModelPart corners an
    // overlapping quad can block both coarse candidate rays even though a
    // narrow route to the real exterior remains between them. Four entries are
    // initialized after the coarse pass around its two best directions.
    vec2 escapeDirections[12] = vec2[12](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0),
        vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
        vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071),
        vec2( 1.0,  0.0), vec2( 1.0,  0.0),
        vec2(-1.0,  0.0), vec2(-1.0,  0.0)
    );
    for (int i = 0; i < 8; i++) {
        float nearSample = coverageAt(texCoord0 + directions[i] * nearStep);
        float innerSample = coverageAt(texCoord0 + directions[i] * innerStep);
        nearMaximum = max(nearMaximum, nearSample);
        nearMinimum = min(nearMinimum, nearSample);
        innerMinimum = min(innerMinimum, innerSample);
        nearSupport += nearSample * (i < 4 ? 1.0 : 0.7071);
        innerSupport += innerSample * (i < 4 ? 1.0 : 0.7071);
        float escapeWeight = (1.0 - innerSample)
                * (i < 4 ? 1.0 : 0.7071);
        escapeWeights[i] = escapeWeight;
        if (escapeWeight > strongestEscapeWeight) {
            strongestEscapeWeight = escapeWeight;
            strongestEscapeDirection = escapeDirections[i];
        }
    }
    float coarseAlternateWeight = 0.0;
    vec2 coarseAlternateDirection = strongestEscapeDirection;
    for (int i = 0; i < 8; i++) {
        float sufficientlyDifferent = 1.0 - step(0.72,
                dot(strongestEscapeDirection, escapeDirections[i]));
        float candidateWeight = escapeWeights[i] * sufficientlyDifferent;
        if (candidateWeight > coarseAlternateWeight) {
            coarseAlternateWeight = candidateWeight;
            coarseAlternateDirection = escapeDirections[i];
        }
    }
    const float HALF_ANGLE_COS = 0.9239;
    const float HALF_ANGLE_SIN = 0.3827;
    escapeDirections[8] = vec2(
            strongestEscapeDirection.x * HALF_ANGLE_COS
                    - strongestEscapeDirection.y * HALF_ANGLE_SIN,
            strongestEscapeDirection.x * HALF_ANGLE_SIN
                    + strongestEscapeDirection.y * HALF_ANGLE_COS);
    escapeDirections[9] = vec2(
            strongestEscapeDirection.x * HALF_ANGLE_COS
                    + strongestEscapeDirection.y * HALF_ANGLE_SIN,
            -strongestEscapeDirection.x * HALF_ANGLE_SIN
                    + strongestEscapeDirection.y * HALF_ANGLE_COS);
    escapeDirections[10] = vec2(
            coarseAlternateDirection.x * HALF_ANGLE_COS
                    - coarseAlternateDirection.y * HALF_ANGLE_SIN,
            coarseAlternateDirection.x * HALF_ANGLE_SIN
                    + coarseAlternateDirection.y * HALF_ANGLE_COS);
    escapeDirections[11] = vec2(
            coarseAlternateDirection.x * HALF_ANGLE_COS
                    + coarseAlternateDirection.y * HALF_ANGLE_SIN,
            -coarseAlternateDirection.x * HALF_ANGLE_SIN
                    + coarseAlternateDirection.y * HALF_ANGLE_COS);
    for (int i = 8; i < 12; i++) {
        float innerSample = coverageAt(texCoord0
                + escapeDirections[i] * innerStep);
        // Half-angle probes receive the same near-unit preference as cardinal
        // probes. Their purpose is candidate selection, not morphology.
        float escapeWeight = (1.0 - innerSample) * 0.9239;
        escapeWeights[i] = escapeWeight;
        if (escapeWeight > strongestEscapeWeight) {
            strongestEscapeWeight = escapeWeight;
            strongestEscapeDirection = escapeDirections[i];
        }
    }

    float fill = smoothstep(0.10, 0.48, center);
    float dilated = smoothstep(0.08, 0.42, nearMaximum);
    float erodedNear = smoothstep(0.11, 0.42, nearMinimum);
    float erodedInner = smoothstep(0.12, 0.44, innerMinimum);

    float distanceSeedFound = 0.0;
    vec2 nearestBoundarySeedUv = vec2(-1.0);
    float distanceOutputPixels = 1.0e30;
    if (DistanceFieldAvailable != 0) {
        distanceOutputPixels = silhouetteBoundaryDistanceOutputPixels(
                texCoord0, distanceSeedFound, nearestBoundarySeedUv);
    }
    float distanceFieldUsable = (DistanceFieldAvailable != 0 ? 1.0 : 0.0)
            * distanceSeedFound;

    // Outside-in ordering: hard purple contour, adaptive noisy gray transition,
    // then the nearly opaque black material core.
    // A real contour has support along neighboring rays. A lone under-resolved
    // SDF hit does not, so it may retain its own sharp fill pixel but can no
    // longer turn into an opaque eight-neighbor purple comb.
    float coherentSupport = smoothstep(0.16, 0.38,
            nearSupport / 6.8284);
    float morphologyOutsideRim = clamp(dilated - fill, 0.0, 1.0)
            * coherentSupport;
    float distanceOutsideRim = (1.0 - fill)
            * (1.0 - smoothstep(
                    PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS * 0.12,
                    PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS,
                    distanceOutputPixels));
    float outsideRim = mix(morphologyOutsideRim,
            distanceOutsideRim, distanceFieldUsable);
    // Do not average all outward probes into one escape vector. At concave
    // corners or overlapping projected model parts, equally plausible exits
    // can cancel and leave an arbitrary screen-right fallback. Keep the
    // strongest exit, then retain the strongest direction separated from it
    // by at least roughly 33 degrees. Searching both lets one ray escape when
    // the other runs into a neighboring projected part.
    float alternateEscapeWeight = 0.0;
    vec2 alternateEscapeDirection = strongestEscapeDirection;
    for (int i = 0; i < 12; i++) {
        float sufficientlyDifferent = 1.0 - step(0.84,
                dot(strongestEscapeDirection, escapeDirections[i]));
        float candidateWeight = escapeWeights[i] * sufficientlyDifferent;
        if (candidateWeight > alternateEscapeWeight) {
            alternateEscapeWeight = candidateWeight;
            alternateEscapeDirection = escapeDirections[i];
        }
    }
    // A projected-radius width remains authoritative on broad body regions.
    // The directional support is retained only for the compatibility path.
    // When the distance field is available, measure both along and across the
    // nearest contour normal. Requiring support in both axes distinguishes a
    // broad torso from a long-but-thin horn, ribbon, tail, or acute tip.
    float normalizedInnerSupport = clamp(innerSupport / 6.8284, 0.0, 1.0);
    float coarseBroadBodyRegion = smoothstep(0.18, 0.52,
            normalizedInnerSupport);
    float measuredLocalWidthSupport = coarseBroadBodyRegion;
    if (distanceFieldUsable > 0.5 && fill > 0.001) {
        vec2 boundaryToInteriorPixels = (texCoord0
                - nearestBoundarySeedUv) * ScreenSize;
        float boundaryToInteriorLength = length(boundaryToInteriorPixels);
        vec2 fallbackInwardDirection = -strongestEscapeDirection;
        vec2 inwardDirection = boundaryToInteriorLength > 0.35
                ? boundaryToInteriorPixels / boundaryToInteriorLength
                : fallbackInwardDirection;
        vec2 inwardUvPerOutputPixel = inwardDirection
                / max(ScreenSize, vec2(1.0));
        vec2 tangentUvPerOutputPixel = vec2(-inwardDirection.y,
                inwardDirection.x) / max(ScreenSize, vec2(1.0));
        vec2 probeOriginUv = texCoord0
                + inwardUvPerOutputPixel * 1.5;
        float nearProbePixels = 4.0;
        float farProbePixels = 8.0;
        float inwardNearSupport = coverageAt(probeOriginUv
                + inwardUvPerOutputPixel * nearProbePixels);
        float inwardFarSupport = coverageAt(probeOriginUv
                + inwardUvPerOutputPixel * farProbePixels);
        float tangentPositiveSupport = coverageAt(probeOriginUv
                + tangentUvPerOutputPixel * nearProbePixels);
        float tangentNegativeSupport = coverageAt(probeOriginUv
                - tangentUvPerOutputPixel * nearProbePixels);
        float axialSupport = mix(inwardNearSupport,
                inwardFarSupport, 0.35);
        float transverseSupport = (tangentPositiveSupport
                + tangentNegativeSupport) * 0.5;
        measuredLocalWidthSupport = smoothstep(0.24, 0.82,
                min(axialSupport, transverseSupport));
    }
    float broadBodyRegion = mix(coarseBroadBodyRegion,
            measuredLocalWidthSupport, distanceFieldUsable);
    float localThinGrayLimitPixels = mix(
            minimumGrayTransitionPixels,
            thinFeatureMaximumGrayTransitionPixels,
            smoothstep(0.04, 0.72, measuredLocalWidthSupport));
    float localizedBodyGrayTransitionPixels = mix(
            min(bodyGrayTransitionPixels,
                    localThinGrayLimitPixels),
            bodyGrayTransitionPixels,
            smoothstep(0.55, 0.94, broadBodyRegion));
    float minimumThinInsideRimPixels =
            THIN_FEATURE_MIN_INSIDE_RIM_OUTPUT_PIXELS
                    * outputPixelsToSourcePixels;
    float localizedBodyInsideRimPixels = mix(
            min(insideRimReachPixels, minimumThinInsideRimPixels),
            insideRimReachPixels,
            smoothstep(0.12, 0.78, measuredLocalWidthSupport));
    float resolvedSiphonGrayTransitionPixels = mix(
            siphonFallbackGrayTransitionPixels,
            siphonGrayTransitionPixels,
            siphonRadiusAvailable);
    float grayReachPixels = mix(localizedBodyGrayTransitionPixels,
            resolvedSiphonGrayTransitionPixels, siphonDominance);
    float resolvedInsideRimReachPixels = mix(
            localizedBodyInsideRimPixels,
            insideRimReachPixels, siphonDominance);
    // Search only as far as this pixel's locally permitted presentation band.
    // Thin sheets and appendages deliberately clamp grayReachPixels; probing
    // with the broad body's full radius would jump across their first exterior
    // and land in another projected ModelPart.
    float boundarySearchReachPixels = resolvedInsideRimReachPixels
            + grayReachPixels;
    float boundaryDistancePixels = boundarySearchReachPixels;
    float boundaryFound = 0.0;
    if (DistanceFieldAvailable != 0) {
        // Style the projected body+siphon union by true Euclidean screen-space
        // distance. ModelPart normals, face orientation, depth discontinuities,
        // and overlapping quads cannot redirect or cancel this transition.
        boundaryDistancePixels = min(boundarySearchReachPixels,
                distanceOutputPixels * outputPixelsToSourcePixels);
        boundaryFound = distanceSeedFound;
    } else {
        // Compatibility path for a driver or shader pack that cannot create
        // the compact coordinate field. Preserve the former first-exit search
        // rather than dropping the gray transition entirely.
        float strongestBoundaryFound;
        float strongestBoundaryDistance = boundaryDistanceAlongDirection(
                texCoord0, strongestEscapeDirection, texel,
                boundarySearchReachPixels,
                center, strongestBoundaryFound);
        float alternateBoundaryFound;
        float alternateBoundaryDistance = boundaryDistanceAlongDirection(
                texCoord0, alternateEscapeDirection, texel,
                boundarySearchReachPixels,
                center, alternateBoundaryFound);
        alternateBoundaryFound *= step(0.0001, alternateEscapeWeight);
        float strongestUsableDistance = mix(boundarySearchReachPixels,
                strongestBoundaryDistance,
                step(0.001, strongestBoundaryFound));
        float alternateUsableDistance = mix(boundarySearchReachPixels,
                alternateBoundaryDistance,
                step(0.001, alternateBoundaryFound));
        boundaryDistancePixels = min(strongestUsableDistance,
                alternateUsableDistance);
        boundaryFound = max(strongestBoundaryFound,
                alternateBoundaryFound);

        // Filtering can leave both far probes barely above the formal crossing
        // threshold. This restrained support exists only in the fallback; the
        // distance field never guesses from face-aligned probes.
        float conservativeBoundarySupport = smoothstep(0.08, 0.52,
                max(strongestEscapeWeight, alternateEscapeWeight))
                * (1.0 - smoothstep(0.82, 0.98,
                        normalizedInnerSupport));
        boundaryFound = max(boundaryFound,
                conservativeBoundarySupport * 0.55);
    }
    // Only 1.5 pixels of the four-pixel purple treatment dilate the exact
    // silhouette. The remaining 2.5 pixels are drawn inward using the measured
    // boundary distance, keeping the requested visual thickness without
    // enlarging the carrier by four pixels on every side.
    float surfaceRim = fill * (1.0 - smoothstep(
            resolvedInsideRimReachPixels * 0.72,
            max(resolvedInsideRimReachPixels, 0.0001),
            boundaryDistancePixels)) * 0.96;
    float purpleRim = max(outsideRim, surfaceRim);
    float grayInwardProgress = clamp((boundaryDistancePixels
            - resolvedInsideRimReachPixels)
            / max(grayReachPixels, 0.0001), 0.0, 1.0);
    float grayOuterSupport = smoothstep(0.04, 0.96, fill);
    float grayToBlackFade = 1.0
            - smoothstep(0.0, 1.0, grayInwardProgress);
    grayToBlackFade *= boundaryFound;
    float grayBand = grayOuterSupport * grayToBlackFade
            * (1.0 - purpleRim * 0.55);
    // Coverage is the screen projection of one combined implicit body/release
    // surface plus the siphon union. Deriving the bands from that final mask
    // makes the purple rim ride every newly exposed depletion edge exactly.
    float core = max(erodedNear, fill * (1.0 - grayBand * 0.70));

    vec2 grainSeed = floor(gl_FragCoord.xy * 0.72);
    float stableGrain = hash12(grainSeed);
    float edgeActivity = clamp(purpleRim + grayBand, 0.0, 1.0);
    float grain = mix(stableGrain, animatedGrain(grainSeed),
            edgeActivity * 0.62);
    // Keep the noisy transition matte. Large silhouettes made the former
    // +/-9% screen-space modulation look like a refracting layer sliding over
    // the body as the camera moved.
    float grayVariation = mix(0.985, 1.015, grain);

    vec3 coreColor = vec3(0.0035, 0.0010, 0.0070);
    float grayGradient = smoothstep(0.08, 0.92, grayBand);
    vec3 grayShadowColor = vec3(0.34, 0.35, 0.38);
    vec3 grayLightColor = vec3(0.74, 0.75, 0.78);
    vec3 grayColor = mix(grayShadowColor, grayLightColor, grayGradient)
            * grayVariation;
    vec3 rimColor = mix(vec3(0.455, 0.080, 0.665),
            vec3(0.690, 0.255, 0.930),
            grain * 0.18 + 0.08);
    vec3 color = mix(coreColor, grayColor, grayBand * 0.90);
    color = mix(color, rimColor, purpleRim * 0.97);

    // Do not reconstruct a reflective body normal from proxy/surfel depth.
    // Overlapping splats can exchange front-depth authority as the camera or
    // turbulence moves, which made the former Phong/Fresnel response resemble
    // a sliding prism. Instead, derive one restrained, screen-stable sheen from
    // the same Euclidean silhouette field that owns the purple/gray bands. It
    // lives just inside the black core, never alters coverage, and fails closed
    // when the distance field is unavailable.
    vec2 contourToBoundaryPixels = (nearestBoundarySeedUv - texCoord0)
            * ScreenSize;
    float contourDirectionLength = length(contourToBoundaryPixels);
    vec2 contourOutwardDirection = contourDirectionLength > 0.35
            ? contourToBoundaryPixels / contourDirectionLength
            : strongestEscapeDirection;
    const vec2 BODY_SHEEN_KEY_DIRECTION = vec2(-0.5793, 0.8151);
    float contourKeyDot = dot(contourOutwardDirection,
            BODY_SHEEN_KEY_DIRECTION);
    float contourBroadLobe = smoothstep(-0.50, 0.65, contourKeyDot);
    float contourPeakLobe = pow(smoothstep(0.20, 0.96,
            contourKeyDot), 2.0);
    float contourKeyLobe = mix(contourBroadLobe,
            contourPeakLobe, 0.65);
    float blackInnerEdgeProfile = smoothstep(0.62, 0.84,
            grayInwardProgress)
            * (1.0 - smoothstep(0.90, 1.0, grayInwardProgress));
    float bodyDominance = smoothstep(0.08, 0.42,
            displayedBodyCoverage(material))
            * (1.0 - smoothstep(0.08, 0.36, material.g));
    float graySuppression = 1.0 - smoothstep(0.12, 0.68, grayBand);
    float bodyContourSheenMask = min(contourKeyLobe
            * blackInnerEdgeProfile
            * smoothstep(0.72, 0.96, core)
            * graySuppression
            * (1.0 - purpleRim)
            * bodyDominance
            * distanceFieldUsable
            * clamp(BodyContourSheenStrength, 0.0, 0.06), 0.06);
    // Restore a real body-depth edge response without restoring the old
    // whole-surface Fresnel layer. Only a narrow black-side shoulder performs
    // the four extra fetches. Every neighbor must belong to the body, remain
    // depth-continuous, and support a locally planar central difference; a
    // surfel winner switch therefore fails closed instead of becoming a bright
    // sliding prism facet.
    float bodyDepthSpecularMask = 0.0;
    float bodyDepthShoulder = smoothstep(0.48, 0.78,
            grayInwardProgress)
            * (1.0 - smoothstep(0.95, 1.0, grayInwardProgress))
            * smoothstep(0.72, 0.96, core)
            * graySuppression
            * (1.0 - purpleRim)
            * bodyDominance
            * distanceFieldUsable;
    float bodyDepthPhase = clamp(DepthHighlightBlend, 0.0, 1.0);
    if (BodyDepthSpecularStrength > 0.0001
            && bodyDepthShoulder * bodyDepthPhase > 0.001
            && centerDepthValid > 0.001
            && centerDepthMaterial.r > centerDepthMaterial.g + 0.02) {
        float bodyDepthContinuityLimit = max(
                SurfaceDepthScale * 0.035, 0.025);
        vec4 bodyLeftSurface = connectedBodySurfacePosition(
                centerDepthCoord + ivec2(-1, 0), depthExtent,
                maximumDepthCoord, surfaceDepth,
                bodyDepthContinuityLimit);
        vec4 bodyRightSurface = connectedBodySurfacePosition(
                centerDepthCoord + ivec2(1, 0), depthExtent,
                maximumDepthCoord, surfaceDepth,
                bodyDepthContinuityLimit);
        vec4 bodyDownSurface = connectedBodySurfacePosition(
                centerDepthCoord + ivec2(0, -1), depthExtent,
                maximumDepthCoord, surfaceDepth,
                bodyDepthContinuityLimit);
        vec4 bodyUpSurface = connectedBodySurfacePosition(
                centerDepthCoord + ivec2(0, 1), depthExtent,
                maximumDepthCoord, surfaceDepth,
                bodyDepthContinuityLimit);
        float bodyFourSideSupport = min(min(bodyLeftSurface.w,
                bodyRightSurface.w), min(bodyDownSurface.w,
                bodyUpSurface.w));
        float bodyBilateralConfidence = smoothstep(0.72, 0.98,
                bodyFourSideSupport);

        vec3 bodyEyeView = projectionEyeView();
        vec3 bodyCenterViewPosition = bodyEyeView
                + viewRayAt(texelCenterUv(centerDepthCoord,
                        depthExtent)) * max(surfaceDepth, 0.0);
        vec3 bodyLeftViewPosition = bodyLeftSurface.xyz
                / max(bodyLeftSurface.w, 0.001);
        vec3 bodyRightViewPosition = bodyRightSurface.xyz
                / max(bodyRightSurface.w, 0.001);
        vec3 bodyDownViewPosition = bodyDownSurface.xyz
                / max(bodyDownSurface.w, 0.001);
        vec3 bodyUpViewPosition = bodyUpSurface.xyz
                / max(bodyUpSurface.w, 0.001);
        vec3 bodyTangentX = bodyRightViewPosition
                - bodyLeftViewPosition;
        vec3 bodyTangentY = bodyUpViewPosition
                - bodyDownViewPosition;
        vec3 bodyRawNormal = cross(bodyTangentX, bodyTangentY);
        float bodyNormalAreaConfidence = step(0.00000000000001,
                dot(bodyRawNormal, bodyRawNormal));

        float bodyDepthScale = max(SurfaceDepthScale, 0.05);
        float bodyLeftDepth = length(bodyLeftViewPosition - bodyEyeView);
        float bodyRightDepth = length(bodyRightViewPosition - bodyEyeView);
        float bodyDownDepth = length(bodyDownViewPosition - bodyEyeView);
        float bodyUpDepth = length(bodyUpViewPosition - bodyEyeView);
        float bodyDepthCurvature = max(abs(bodyLeftDepth
                + bodyRightDepth - 2.0 * surfaceDepth),
                abs(bodyDownDepth + bodyUpDepth
                - 2.0 * surfaceDepth)) / bodyDepthScale;
        float bodyPlanarityConfidence = 1.0 - smoothstep(0.010,
                0.035, bodyDepthCurvature);
        float bodyTangentAspect = min(length(bodyTangentX),
                length(bodyTangentY))
                / max(max(length(bodyTangentX), length(bodyTangentY)),
                0.00001);
        float bodyShapeConfidence = smoothstep(0.08, 0.25,
                bodyTangentAspect);

        vec3 bodyViewDirection = safeNormalize3(bodyEyeView
                - bodyCenterViewPosition, vec3(0.0, 0.0, 1.0));
        vec3 bodySurfaceNormal = safeNormalize3(bodyRawNormal,
                bodyViewDirection);
        bodySurfaceNormal *= dot(bodySurfaceNormal, bodyViewDirection) < 0.0
                ? -1.0 : 1.0;
        vec3 bodyLightDirection = safeNormalize3(
                vec3(-0.52, 0.68, 0.56), vec3(0.0, 0.0, 1.0));
        vec3 bodyHalfDirection = safeNormalize3(bodyLightDirection
                + bodyViewDirection, bodyViewDirection);
        float bodyKeyedSide = smoothstep(-0.05, 0.48,
                dot(bodySurfaceNormal, bodyLightDirection));
        float bodyRoughSpecular = pow(clamp(dot(bodySurfaceNormal,
                bodyHalfDirection), 0.0, 1.0), 10.0) * bodyKeyedSide;
        float bodyNormalConfidence = centerDepthValid
                * bodyBilateralConfidence
                * bodyNormalAreaConfidence
                * bodyPlanarityConfidence
                * bodyShapeConfidence;
        float largeBodyAttenuation = mix(1.0, 0.62,
                smoothstep(280.0, 720.0, ProjectedBodyRadiusPixels));
        float bodySpecularCap = 0.085 * largeBodyAttenuation;
        bodyDepthSpecularMask = min(bodyRoughSpecular
                * bodyNormalConfidence
                * bodyDepthShoulder
                * bodyDepthPhase
                * clamp(BodyDepthSpecularStrength, 0.0, 0.12)
                * largeBodyAttenuation, bodySpecularCap);
    }
    float combinedBodyHighlightMask = min(bodyContourSheenMask
            + bodyDepthSpecularMask, 0.10);
    vec3 bodySheenColor = vec3(0.54, 0.58, 0.76);
    color = mix(color, bodySheenColor, combinedBodyHighlightMask);

    // The narrow siphon retains its independent depth-normal highlight. B-only
    // handoff storage and depthless conservative fill remain excluded.
    float siphonGate = smoothstep(0.08, 0.42, material.g)
            * smoothstep(0.02, 0.24, centerDepthMaterial.g);
    float highlightSignal = clamp(depthHighlight
            * clamp(DepthHighlightBlend, 0.0, 1.0),
            0.0, 1.0);
    float broadHighlight = pow(smoothstep(0.26, 0.78,
            highlightSignal), 1.35);
    float peakHighlight = pow(smoothstep(0.60, 0.95,
            highlightSignal), 0.85);
    float siphonInteriorHighlight = broadHighlight
            * smoothstep(0.24, 0.78, core)
            * (1.0 - grayBand * 0.40)
            * (1.0 - purpleRim * 0.92) * 0.080;
    float siphonInnerEdgeHighlight = peakHighlight * grayBand
            * (1.0 - purpleRim * 0.70) * 0.150;
    float siphonOuterGlint = peakHighlight * purpleRim * 0.020;
    float siphonHighlightMask = min((siphonInteriorHighlight
            + siphonInnerEdgeHighlight + siphonOuterGlint) * siphonGate,
            0.115);
    float highlightMask = siphonHighlightMask;
    vec3 coolWhite = mix(vec3(0.48, 0.63, 0.82),
            vec3(0.94, 0.98, 1.00), peakHighlight);
    color = mix(color, coolWhite, highlightMask);

    // Gray is a color transition inside the same dense silhouette, not a
    // translucent smoke layer. Preserve only ordinary projected-coverage
    // antialiasing at the outer contour.
    float opacity = max(fill * 0.985, purpleRim * 0.98);
    opacity = max(opacity, purpleRim * 0.98);
    opacity = max(opacity, core * 0.99);
    // Once the full transition reach fits inside the silhouette, the material
    // is the nearly opaque black core again.
    opacity = mix(opacity, 1.0,
            smoothstep(0.55, 0.85, erodedInner));

    // The exact mask target retains the frozen hit-frame RGB for diagnostics,
    // but the final composite consumes only its authoritative texture-alpha
    // coverage. This keeps the original Pokemon material out of the presented
    // effect while preserving an opaque exact silhouette during the SDF
    // handoff and allowing aura-only pixels to grow in.
    vec4 frozenSnapshot = texture(MaskSampler, texCoord0);
    float frozenCoverage = smoothstep(0.015, 0.12, frozenSnapshot.a);

    float handoff = clamp(DeformationBlend, 0.0, 1.0);
    float frozenWeight = frozenCoverage * (1.0 - handoff);
    float silhouetteWeight = opacity * handoff;
    float combinedWeight = frozenWeight + silhouetteWeight;
    if (combinedWeight < 0.001) {
        discard;
    }
    fragColor = vec4(color, clamp(combinedWeight, 0.0, 1.0));
}
