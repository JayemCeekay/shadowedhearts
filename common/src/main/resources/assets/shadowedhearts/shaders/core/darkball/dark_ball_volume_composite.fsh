#version 150

uniform sampler2D Sampler0;
uniform sampler2D MaskSampler;
uniform float DeformationBlend;
uniform float GameTime;
uniform int DeformationDebugMode;
uniform float ProjectedBodyRadiusPixels;
uniform mat4 InvProjMat;
uniform int SurfaceDepthAvailable;
uniform float SurfaceDepthScale;

in vec2 texCoord0;
out vec4 fragColor;

const float PURPLE_RIM_INNER_REACH_PIXELS = 1.15;
const float GRAY_TRANSITION_FRACTION = 0.40;
const float MIN_GRAY_TRANSITION_PIXELS = 1.15;
const float MAX_GRAY_TRANSITION_PIXELS = 96.0;
const float GRAY_SMOKE_MIN_OPACITY = 0.52;
const float SIPHON_FALLBACK_INNER_REACH_PIXELS = 2.20;

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
    if (DeformationDebugMode > 0) {
        // Mode 12 excludes B: that channel is the undeformed formation-handoff
        // projection and would be mistaken for one of the current SDF owners.
        float storedBodyCoverage = bodyStorageCoverage(material.b);
        float bodyDebugCoverage = DeformationDebugMode == 12
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
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));

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
    if (centerDepthValid > 0.001) {
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
    vec2 innerEscapeVector = vec2(0.0);
    // Keep the purple contour narrow, then spend roughly 40% of the projected
    // body radius on the noisy gray transition before reaching the black core.
    // The clamp protects small details and extreme closeups. Siphon-only pixels
    // retain the legacy narrow transition so the thin tube keeps a black core.
    float bodyGrayTransitionPixels = clamp(
            ProjectedBodyRadiusPixels * GRAY_TRANSITION_FRACTION,
            MIN_GRAY_TRANSITION_PIXELS,
            MAX_GRAY_TRANSITION_PIXELS);
    float bodyInnerReachPixels = PURPLE_RIM_INNER_REACH_PIXELS
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
            projectedSiphonRadiusPixels * GRAY_TRANSITION_FRACTION,
            MIN_GRAY_TRANSITION_PIXELS,
            MAX_GRAY_TRANSITION_PIXELS);
    float proportionalSiphonInnerReachPixels =
            PURPLE_RIM_INNER_REACH_PIXELS
            + siphonGrayTransitionPixels;
    float siphonInnerReachPixels = mix(
            SIPHON_FALLBACK_INNER_REACH_PIXELS,
            proportionalSiphonInnerReachPixels,
            siphonRadiusAvailable);
    float siphonDominance = smoothstep(
            displayedBodyCoverage(material) + 0.02,
            displayedBodyCoverage(material) + 0.10,
            material.g);
    float innerReachPixels = mix(bodyInnerReachPixels,
            siphonInnerReachPixels, siphonDominance);
    vec2 nearStep = texel * PURPLE_RIM_INNER_REACH_PIXELS;
    vec2 innerStep = texel * innerReachPixels;
    vec2 directions[8] = vec2[8](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0),
        vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
        vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071)
    );
    for (int i = 0; i < 8; i++) {
        float nearSample = coverageAt(texCoord0 + directions[i] * nearStep);
        float innerSample = coverageAt(texCoord0 + directions[i] * innerStep);
        nearMaximum = max(nearMaximum, nearSample);
        nearMinimum = min(nearMinimum, nearSample);
        innerMinimum = min(innerMinimum, innerSample);
        nearSupport += nearSample * (i < 4 ? 1.0 : 0.7071);
        float escapeWeight = (1.0 - innerSample)
                * (i < 4 ? 1.0 : 0.7071);
        innerEscapeVector += directions[i] * escapeWeight;
    }

    float fill = smoothstep(0.10, 0.48, center);
    float dilated = smoothstep(0.08, 0.42, nearMaximum);
    float erodedNear = smoothstep(0.11, 0.42, nearMinimum);
    float erodedInner = smoothstep(0.12, 0.44, innerMinimum);

    // Outside-in ordering: hard purple contour, adaptive noisy gray transition,
    // then the nearly opaque black material core.
    // A real contour has support along neighboring rays. A lone under-resolved
    // SDF hit does not, so it may retain its own sharp fill pixel but can no
    // longer turn into an opaque eight-neighbor purple comb.
    float coherentSupport = smoothstep(0.16, 0.38,
            nearSupport / 6.8284);
    float outsideRim = clamp(dilated - fill, 0.0, 1.0)
            * coherentSupport;
    float surfaceRim = clamp(fill - erodedNear, 0.0, 1.0) * 0.34;
    float purpleRim = max(outsideRim, surfaceRim);
    // The far erosion ring already reveals which direction reaches the nearest
    // exterior. Search for that boundary along the inferred direction, then
    // turn its continuous pixel distance into one broad smoothstep. Averaging a
    // few fixed probes quantizes hard coverage into visible concentric bands.
    float escapeLengthSquared = dot(innerEscapeVector, innerEscapeVector);
    vec2 grayGradientDirection = escapeLengthSquared > 0.000001
            ? innerEscapeVector * inversesqrt(escapeLengthSquared)
            : vec2(1.0, 0.0);
    float grayReachPixels = max(innerReachPixels
            - PURPLE_RIM_INNER_REACH_PIXELS, 0.0);
    float insideDistancePixels = 0.0;
    float outsideDistancePixels = innerReachPixels;
    float insideCoverage = center;
    float farCoverage = coverageAt(texCoord0
            + grayGradientDirection * innerStep);
    float outsideCoverage = farCoverage;
    for (int searchIndex = 0; searchIndex < 7; searchIndex++) {
        float midpointDistancePixels = (insideDistancePixels
                + outsideDistancePixels) * 0.5;
        float midpointCoverage = coverageAt(texCoord0
                + grayGradientDirection * texel
                * midpointDistancePixels);
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
    float boundaryDistancePixels = mix(insideDistancePixels,
            outsideDistancePixels, crossingBlend);
    float grayInwardProgress = clamp((boundaryDistancePixels
            - PURPLE_RIM_INNER_REACH_PIXELS)
            / max(grayReachPixels, 0.0001), 0.0, 1.0);
    float boundaryFound = 1.0 - smoothstep(0.44, 0.60, farCoverage);
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
    float grayVariation = mix(0.90, 1.08, grain);

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

    // Body and siphon share the same reconstructed surface light. Separate
    // coverage gates preserve the established body intensity while giving the
    // narrower siphon a restrained highlight instead of a solid white stripe.
    // B-only handoff storage and depthless conservative fill remain excluded.
    float displayedBody = displayedBodyCoverage(material);
    float bodyGate = smoothstep(0.12, 0.52, displayedBody)
            * smoothstep(0.02, 0.28, centerDepthMaterial.r);
    float siphonGate = smoothstep(0.08, 0.42, material.g)
            * smoothstep(0.02, 0.24, centerDepthMaterial.g);
    float deformationGate = smoothstep(0.14, 0.62, DeformationBlend);
    float highlightSignal = clamp(depthHighlight * deformationGate,
            0.0, 1.0);
    float broadHighlight = pow(smoothstep(0.26, 0.78,
            highlightSignal), 1.35);
    float peakHighlight = pow(smoothstep(0.60, 0.95,
            highlightSignal), 0.85);
    float interiorHighlight = broadHighlight
            * smoothstep(0.28, 0.82, core)
            * (1.0 - grayBand * 0.35)
            * (1.0 - purpleRim * 0.90) * 0.11;
    float innerEdgeHighlight = peakHighlight * grayBand
            * (1.0 - purpleRim * 0.65) * 0.22;
    float outerGlint = peakHighlight * purpleRim * 0.035;
    float bodyHighlightMask = min((interiorHighlight + innerEdgeHighlight
            + outerGlint) * bodyGate, 0.16);
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
    float highlightMask = max(bodyHighlightMask, siphonHighlightMask);
    vec3 coolWhite = mix(vec3(0.48, 0.63, 0.82),
            vec3(0.94, 0.98, 1.00), peakHighlight);
    color = mix(color, coolWhite, highlightMask);

    float opacity = max(fill * 0.985, purpleRim * 0.98);
    // The movie reference reads as a translucent smoky shell around a dense
    // black mass. Reduce alpha only where gray owns the material, then let the
    // same inward gradient restore opacity continuously toward the core.
    float graySmokeAmount = smoothstep(0.04, 0.92, grayBand)
            * (1.0 - purpleRim * 0.85);
    float grayOpacityMultiplier = mix(1.0, GRAY_SMOKE_MIN_OPACITY,
            graySmokeAmount);
    opacity *= grayOpacityMultiplier;
    opacity = max(opacity, purpleRim * 0.98);
    opacity = max(opacity, core * 0.99);
    // Once the full transition reach fits inside the silhouette, the material
    // is the nearly opaque black core again.
    opacity = mix(opacity, 1.0,
            smoothstep(0.55, 0.85, erodedInner));

    // RGB in the exact mask target is the hit-frame Pokemon albedo, frozen in
    // the same pose as its authoritative texture-alpha coverage in A. Blend it
    // with the completed SDF material here, after ownership, rim, and depth
    // lighting have all resolved. Complementary normalized weights keep the
    // shared core opaque during the handoff while aura-only pixels grow in.
    vec4 frozenSnapshot = texture(MaskSampler, texCoord0);
    float frozenCoverage = smoothstep(0.015, 0.12, frozenSnapshot.a);
    vec3 frozenColor = frozenSnapshot.rgb
            / max(frozenSnapshot.a, 0.0001);
    frozenColor = clamp(frozenColor, vec3(0.0), vec3(1.0));

    float handoff = clamp(DeformationBlend, 0.0, 1.0);
    float frozenWeight = frozenCoverage * (1.0 - handoff);
    float silhouetteWeight = opacity * handoff;
    float combinedWeight = frozenWeight + silhouetteWeight;
    if (combinedWeight < 0.001) {
        discard;
    }
    vec3 crossfadedColor = (frozenColor * frozenWeight
            + color * silhouetteWeight) / combinedWeight;
    fragColor = vec4(crossfadedColor,
            clamp(combinedWeight, 0.0, 1.0));
}
