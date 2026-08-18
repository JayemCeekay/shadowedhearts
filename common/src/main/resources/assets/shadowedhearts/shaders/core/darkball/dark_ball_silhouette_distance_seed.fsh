#version 150

uniform sampler2D Sampler0;
uniform vec2 TargetSize;

in vec2 texCoord0;
out vec4 fragColor;

float coverageAtTexel(ivec2 texelCoord, ivec2 sourceExtent) {
    vec4 material = texelFetch(Sampler0,
            clamp(texelCoord, ivec2(0), sourceExtent - ivec2(1)), 0);
    return max(material.r, material.g);
}

void considerBoundarySegment(ivec2 firstCoord, ivec2 secondCoord,
        float firstCoverage, float secondCoverage,
        ivec2 sourceExtent, vec2 fieldCenterUv,
        inout vec2 bestSeedUv, inout float bestDistanceSquared) {
    ivec2 boundedFirst = clamp(firstCoord, ivec2(0),
            sourceExtent - ivec2(1));
    ivec2 boundedSecond = clamp(secondCoord, ivec2(0),
            sourceExtent - ivec2(1));
    if (all(equal(boundedFirst, boundedSecond))) {
        return;
    }

    float firstInside = step(0.5, firstCoverage);
    float secondInside = step(0.5, secondCoverage);
    if (abs(firstInside - secondInside) < 0.5) {
        return;
    }

    float crossingBlend = clamp((0.5 - firstCoverage)
            / (secondCoverage - firstCoverage), 0.0, 1.0);
    vec2 firstUv = (vec2(boundedFirst) + vec2(0.5))
            / vec2(sourceExtent);
    vec2 secondUv = (vec2(boundedSecond) + vec2(0.5))
            / vec2(sourceExtent);
    vec2 crossingUv = mix(firstUv, secondUv, crossingBlend);
    vec2 sourceDelta = (crossingUv - fieldCenterUv)
            * vec2(sourceExtent);
    float distanceSquared = dot(sourceDelta, sourceDelta);
    if (distanceSquared < bestDistanceSquared) {
        bestDistanceSquared = distanceSquared;
        bestSeedUv = crossingUv;
    }
}

void main() {
    ivec2 sourceExtent = textureSize(Sampler0, 0);
    ivec2 sourceCenter = clamp(
            ivec2(floor(texCoord0 * vec2(sourceExtent))),
            ivec2(0), sourceExtent - ivec2(1));
    vec2 bestSeedUv = vec2(-1.0);
    float bestDistanceSquared = 1.0e30;
    float sourceCoverage[16];

    // The field is intentionally half resolution, but its contour must not be.
    // Inspect the full-resolution source texels covered by this field pixel and
    // their immediate halo. Exact 0.5 crossings keep one-pixel horns, claws,
    // ribbons, and acute corners from falling between field-pixel centers.
    // Cache the 4x4 source neighborhood so this refinement costs sixteen
    // source reads, not one fresh pair for every candidate edge.
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            ivec2 sourceCoord = sourceCenter + ivec2(x - 2, y - 2);
            sourceCoverage[y * 4 + x] = coverageAtTexel(
                    sourceCoord, sourceExtent);
        }
    }
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            ivec2 sourceCoord = sourceCenter + ivec2(x - 2, y - 2);
            int sourceIndex = y * 4 + x;
            if (x < 3) {
                considerBoundarySegment(sourceCoord,
                        sourceCoord + ivec2(1, 0),
                        sourceCoverage[sourceIndex],
                        sourceCoverage[sourceIndex + 1],
                        sourceExtent, texCoord0,
                        bestSeedUv, bestDistanceSquared);
            }
            if (y < 3) {
                considerBoundarySegment(sourceCoord,
                        sourceCoord + ivec2(0, 1),
                        sourceCoverage[sourceIndex],
                        sourceCoverage[sourceIndex + 4],
                        sourceExtent, texCoord0,
                        bestSeedUv, bestDistanceSquared);
            }
        }
    }

    // RG32F stores normalized screen coordinates. Negative clear values mark
    // pixels that have not yet received a boundary seed.
    fragColor = bestDistanceSquared < 1.0e29
            ? vec4(bestSeedUv, 0.0, 1.0)
            : vec4(-1.0, -1.0, 0.0, 0.0);
}
