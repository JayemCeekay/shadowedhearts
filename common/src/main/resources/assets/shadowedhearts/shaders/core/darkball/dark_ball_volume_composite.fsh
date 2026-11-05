#version 150

uniform sampler2D Sampler0;
uniform sampler2D MaskSampler;
uniform sampler2D DistanceSampler;
uniform float DeformationBlend;
uniform float BodyGrayTransitionFraction;
uniform int AnimeEnergyCoverage;
uniform float AnimeIntakePulse;
uniform vec2 AnimeFlowDirection;
uniform float GameTime;
uniform int DistanceFieldAvailable;
uniform vec2 ScreenSize;
uniform float ProjectedBodyRadiusPixels;

in vec2 texCoord0;
out vec4 fragColor;

const float PURPLE_RIM_REACH_OUTPUT_PIXELS = 4.0;
const float PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS = 1.5;
const float MIN_GRAY_TRANSITION_OUTPUT_PIXELS = 1.15;
const float MAX_GRAY_TRANSITION_OUTPUT_PIXELS = 96.0;
const float THIN_FEATURE_MAX_GRAY_TRANSITION_OUTPUT_PIXELS = 5.0;
const float THIN_FEATURE_MIN_INSIDE_RIM_OUTPUT_PIXELS = 0.85;

float displayedBodyCoverage(vec4 material) {
    // The edge resolve has already produced the exact texture-clipped core and
    // unioned the legitimate depth-resolved 3D aura shell into R. Never blend
    // raw voxel channel B back into visible body coverage here.
    return material.r;
}

float coverageAt(vec2 uv) {
    vec2 bounded = clamp(uv, vec2(0.0), vec2(1.0));
    vec4 material = texture(Sampler0, bounded);
    return displayedBodyCoverage(material);
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

void main() {
    vec4 material = texture(Sampler0, texCoord0);
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    // Resolve a single body silhouette. Because the inlet is already merged
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
    // texture-size-relative UV steps. This keeps the body rim at
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
    // The body uses an explicit presentation fraction instead of borrowing the
    // collapse envelope. This preserves the failure path's thick black core
    // from the first exact-mask frame through the energy handoff. The clamp
    // protects small details and extreme closeups.
    float bodyGrayTransitionPixels = clamp(
            ProjectedBodyRadiusPixels * BodyGrayTransitionFraction,
            minimumGrayTransitionPixels,
            maximumGrayTransitionPixels);
    float bodyInnerReachPixels = insideRimReachPixels
            + bodyGrayTransitionPixels;
    float innerReachPixels = bodyInnerReachPixels;
    vec2 nearStep = texel * outsideRimReachPixels;
    vec2 innerStep = texel * innerReachPixels;
    vec2 directions[8] = vec2[8](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0),
        vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
        vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071)
    );
    // Long-range boundary selection needs locally finer angular resolution
    // than the eight-tap morphology ring. At projected model-part corners an
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
    float grayReachPixels = localizedBodyGrayTransitionPixels;
    float resolvedInsideRimReachPixels = localizedBodyInsideRimPixels;
    // Search only as far as this pixel's locally permitted presentation band.
    // Thin sheets and appendages deliberately clamp grayReachPixels; probing
    // with the broad body's full radius would jump across their first exterior
    // and land in another projected model-part.
    float boundarySearchReachPixels = resolvedInsideRimReachPixels
            + grayReachPixels;
    float boundaryDistancePixels = boundarySearchReachPixels;
    float boundaryFound = 0.0;
    if (DistanceFieldAvailable != 0) {
        // Style the projected body silhouette by true Euclidean screen-space
        // distance. model-part normals, face orientation, depth discontinuities,
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
    // surface. Deriving the bands from that final mask
    // makes the purple rim ride every newly exposed depletion edge exactly.
    float core = max(erodedNear, fill * (1.0 - grayBand * 0.70));

    vec3 color;
    float edgeActivity = clamp(purpleRim + grayBand, 0.0, 1.0);
    vec2 contourToBoundaryPixels = (nearestBoundarySeedUv - texCoord0)
            * ScreenSize;
    float contourDirectionLength = length(contourToBoundaryPixels);
    vec2 contourOutwardDirection = contourDirectionLength > 0.35
            ? contourToBoundaryPixels / contourDirectionLength
            : strongestEscapeDirection;
    float animeExteriorOpacityScale = 1.0;
    // Style the existing silhouette only. Its coverage, thin-feature width
    // limits, nearest-depth ownership and exact-mask recovery stay upstream.
    // A coverage normal is stable when moving source triangles exchange depth
    // authority; it also points outward on both sides of the contour.
    vec2 animeOutward = contourOutwardDirection;
    if (distanceFieldUsable > 0.5 && edgeActivity > 0.001) {
        vec2 directionStep = 2.0 / max(ScreenSize, vec2(1.0));
        vec2 coverageGradient = vec2(
                coverageAt(nearestBoundarySeedUv + vec2(directionStep.x, 0.0))
                        - coverageAt(nearestBoundarySeedUv - vec2(directionStep.x, 0.0)),
                coverageAt(nearestBoundarySeedUv + vec2(0.0, directionStep.y))
                        - coverageAt(nearestBoundarySeedUv - vec2(0.0, directionStep.y)));
        float gradientLength = length(coverageGradient);
        animeOutward = gradientLength > 0.02
                ? -coverageGradient / gradientLength
                : contourOutwardDirection * (center >= 0.5 ? 1.0 : -1.0);
    }
    float flowLength = length(AnimeFlowDirection);
    vec2 animeFlow = flowLength > 0.0001
            ? AnimeFlowDirection / flowLength : vec2(0.0, 1.0);
    float upperContour = smoothstep(-0.55, 0.65, animeOutward.y);
    float leadingContour = smoothstep(-0.55, 0.65,
            dot(animeOutward, animeFlow));
    float luminousContour = clamp(upperContour * 0.55
            + leadingContour * 0.45, 0.0, 1.0);

    // This is a conservative presentation heuristic, not a source-hole
    // classifier. A small boundary surrounded by coverage in every probe
    // direction receives less decoration. It can include real tiny texture
    // cutouts: only RGB is attenuated, never the mask or its transparency.
    float compactBoundary = 0.0;
    if (distanceFieldUsable > 0.5 && edgeActivity > 0.001) {
        float enclosingSupport = 1.0;
        vec2 enclosingStep = 12.0 / max(ScreenSize, vec2(1.0));
        for (int i = 0; i < 8; i++) {
            enclosingSupport = min(enclosingSupport,
                    coverageAt(nearestBoundarySeedUv
                            + directions[i] * enclosingStep));
        }
        compactBoundary = smoothstep(0.72, 0.96, enclosingSupport);
    }
    float contourDecoration = mix(1.0, 0.28, compactBoundary);
    float animeGrayBand = grayBand * mix(1.0, 0.45, compactBoundary);
    float smokeValue = smoothstep(0.0, 1.0, animeGrayBand);
    vec3 animeGray = mix(vec3(0.22, 0.225, 0.25),
            vec3(0.70, 0.705, 0.75), smokeValue);
    // Broad, slowly changing value replaces per-pixel animated flecks.
    animeGray *= 0.985 + 0.015 * sin(GameTime * 0.7
            + luminousContour * 2.0);
    vec3 animeRim = mix(vec3(0.49, 0.17, 0.77),
            vec3(0.77, 0.63, 0.94), luminousContour);
    animeRim = mix(animeRim, vec3(0.97, 0.95, 1.0),
            smoothstep(0.48, 0.95, luminousContour));
    animeRim = mix(animeRim, vec3(0.99, 0.97, 1.0),
            clamp(AnimeIntakePulse, 0.0, 1.0) * 0.20);
    color = mix(vec3(0.002, 0.001, 0.004), animeGray,
            animeGrayBand * 0.90);
    color = mix(color, animeRim, purpleRim * 0.97
            * contourDecoration);

    // A soft exterior halo fits inside the existing 1.5 output-pixel reach.
    // It needs no shader-pack bloom and never expands the carrier/bounds.
    // Fade only the outside opacity; an intake pulse cannot fill holes or
    // turn the black interior into a transparent/emissive sheet.
    if (distanceFieldUsable > 0.5) {
        float hardContour = 1.0 - smoothstep(0.25, 0.85,
                distanceOutputPixels);
        float haloBlend = (1.0 - fill) * (1.0 - hardContour);
        color = mix(color, vec3(0.64, 0.43, 0.89), haloBlend);
        animeExteriorOpacityScale = mix(0.22, 1.0, hardContour);
    }


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
    opacity *= mix(animeExteriorOpacityScale, 1.0, fill);


    if (AnimeEnergyCoverage != 0) {
        // R is the source/energy geometric union; B is its complementary
        // presentation weight. Keep that weight out of every contour probe.
        // Dividing by occupancy preserves antialiasing already supplied by
        // opacity rather than applying the silhouette's edge coverage twice.
        vec4 alphaMaterial = material;
        if (alphaMaterial.r <= 0.0001 && distanceFieldUsable > 0.5) {
            // The exterior halo inherits only its nearest actual boundary's
            // weight. A fading source elsewhere cannot illuminate this edge.
            alphaMaterial = texture(Sampler0, nearestBoundarySeedUv);
        }
        float presentationAlpha = alphaMaterial.r > 0.0001
                ? clamp(alphaMaterial.b / alphaMaterial.r, 0.0, 1.0) : 0.0;
        opacity *= presentationAlpha;
    }

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
