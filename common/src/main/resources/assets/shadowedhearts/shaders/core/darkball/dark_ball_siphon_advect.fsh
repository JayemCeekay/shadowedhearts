#version 150

uniform sampler2D PreviousSiphonDensitySampler;
uniform sampler2D BodyDensitySampler;

uniform vec3 VolumeSize;
uniform vec3 SiphonP0;
uniform vec3 SiphonP3;
uniform vec3 SiphonBolt1;
uniform vec3 SiphonBolt2;
uniform vec3 SiphonBolt3;
uniform vec3 SiphonBolt4;
uniform vec3 SiphonBolt5;
uniform vec3 SiphonBolt6;
uniform vec3 SiphonBolt7;
uniform vec3 SiphonBolt8;
uniform vec3 SiphonBolt9;
uniform vec3 SiphonBolt10;
uniform vec3 SiphonBolt11;
uniform vec3 SiphonBolt12;
uniform float BodyRadius;
uniform float VoxelSize;
uniform float SiphonEndRadius;
uniform float DeltaTime;
uniform float SiphonProgress;
uniform float FinalCollapse;
uniform float Destabilization;
uniform float GameTime;

in vec2 texCoord0;
out vec4 fragColor;

const int SIPHON_X = 48;
const int SIPHON_Y = 12;
const int SIPHON_Z = 12;
const int BODY_X = 96;
const int BODY_Y = 64;
const int BODY_Z = 64;
const int SIPHON_BOLT_SEGMENTS = 13;
const int SIPHON_CURVE_SUBDIVISIONS = 3;
const float TAU = 6.28318530718;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

vec3 safeNormalize(vec3 value, vec3 fallback) {
    float lengthSquared = dot(value, value);
    if (lengthSquared > 0.0000001) {
        return value * inversesqrt(lengthSquared);
    }
    float fallbackLengthSquared = dot(fallback, fallback);
    if (fallbackLengthSquared > 0.0000001) {
        return fallback * inversesqrt(fallbackLengthSquared);
    }
    return vec3(1.0, 0.0, 0.0);
}

bool siphonCellInBounds(ivec3 cell) {
    return cell.x >= 0 && cell.x < SIPHON_X
        && cell.y >= 0 && cell.y < SIPHON_Y
        && cell.z >= 0 && cell.z < SIPHON_Z;
}

bool bodyCellInBounds(ivec3 cell) {
    return cell.x >= 0 && cell.x < BODY_X
        && cell.y >= 0 && cell.y < BODY_Y
        && cell.z >= 0 && cell.z < BODY_Z;
}

float siphonDensityTexel(ivec3 cell) {
    if (!siphonCellInBounds(cell)) {
        return 0.0;
    }
    return texelFetch(PreviousSiphonDensitySampler,
            ivec2(cell.z * SIPHON_X + cell.x, cell.y), 0).r;
}

float bodyTransferTexel(ivec3 cell) {
    if (!bodyCellInBounds(cell)) {
        return 0.0;
    }
    // BodyDensitySampler is the current RG16F body result: R is remaining
    // mobile density and G is the density removed into this inlet this step.
    return texelFetch(BodyDensitySampler,
            ivec2(cell.z * BODY_X + cell.x, cell.y), 0).g;
}

float sampleSiphonDensity(float curveT, vec2 radial) {
    if (curveT < 0.0 || curveT > 1.0 || dot(radial, radial) > 1.35) {
        return 0.0;
    }
    vec3 grid = vec3(
        curveT * float(SIPHON_X) - 0.5,
        (radial.x * 0.5 + 0.5) * float(SIPHON_Y) - 0.5,
        (radial.y * 0.5 + 0.5) * float(SIPHON_Z) - 0.5
    );
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    float c000 = siphonDensityTexel(base);
    float c100 = siphonDensityTexel(base + ivec3(1, 0, 0));
    float c010 = siphonDensityTexel(base + ivec3(0, 1, 0));
    float c110 = siphonDensityTexel(base + ivec3(1, 1, 0));
    float c001 = siphonDensityTexel(base + ivec3(0, 0, 1));
    float c101 = siphonDensityTexel(base + ivec3(1, 0, 1));
    float c011 = siphonDensityTexel(base + ivec3(0, 1, 1));
    float c111 = siphonDensityTexel(base + ivec3(1, 1, 1));
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y),
               mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

float sampleBodyTransferLocal(vec3 localPos) {
    vec3 grid = vec3(
        localPos.x / max(VolumeSize.x, 0.0001) * float(BODY_X) - 0.5,
        (localPos.y / max(VolumeSize.y, 0.0001) * 0.5 + 0.5) * float(BODY_Y) - 0.5,
        (localPos.z / max(VolumeSize.z, 0.0001) * 0.5 + 0.5) * float(BODY_Z) - 0.5
    );
    if (grid.x < -1.0 || grid.x > float(BODY_X)
            || grid.y < -1.0 || grid.y > float(BODY_Y)
            || grid.z < -1.0 || grid.z > float(BODY_Z)) {
        return 0.0;
    }
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    float c000 = bodyTransferTexel(base);
    float c100 = bodyTransferTexel(base + ivec3(1, 0, 0));
    float c010 = bodyTransferTexel(base + ivec3(0, 1, 0));
    float c110 = bodyTransferTexel(base + ivec3(1, 1, 0));
    float c001 = bodyTransferTexel(base + ivec3(0, 0, 1));
    float c101 = bodyTransferTexel(base + ivec3(1, 0, 1));
    float c011 = bodyTransferTexel(base + ivec3(0, 1, 1));
    float c111 = bodyTransferTexel(base + ivec3(1, 1, 1));
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y),
               mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

vec3 siphonPathNode(int index) {
    if (index <= 0) {
        return SiphonP0;
    }
    if (index == 1) {
        return SiphonBolt1;
    }
    if (index == 2) {
        return SiphonBolt2;
    }
    if (index == 3) {
        return SiphonBolt3;
    }
    if (index == 4) {
        return SiphonBolt4;
    }
    if (index == 5) {
        return SiphonBolt5;
    }
    if (index == 6) {
        return SiphonBolt6;
    }
    if (index == 7) {
        return SiphonBolt7;
    }
    if (index == 8) {
        return SiphonBolt8;
    }
    if (index == 9) {
        return SiphonBolt9;
    }
    if (index == 10) {
        return SiphonBolt10;
    }
    if (index == 11) {
        return SiphonBolt11;
    }
    if (index == 12) {
        return SiphonBolt12;
    }
    return SiphonP3;
}

void siphonPathSegment(float curveT, out int segmentIndex,
                       out float segmentT) {
    float scaled = saturate(curveT) * float(SIPHON_BOLT_SEGMENTS);
    segmentIndex = clamp(int(floor(scaled)), 0,
            SIPHON_BOLT_SEGMENTS - 1);
    segmentT = clamp(scaled - float(segmentIndex), 0.0, 1.0);
}

vec3 siphonPathNodeTangent(int index) {
    if (index <= 0) {
        return (siphonPathNode(1) - siphonPathNode(0)) * 0.68;
    }
    if (index >= SIPHON_BOLT_SEGMENTS) {
        return (siphonPathNode(SIPHON_BOLT_SEGMENTS)
                - siphonPathNode(SIPHON_BOLT_SEGMENTS - 1)) * 0.68;
    }

    vec3 previousLeg = siphonPathNode(index)
            - siphonPathNode(index - 1);
    vec3 nextLeg = siphonPathNode(index + 1)
            - siphonPathNode(index);
    float previousLength = length(previousLeg);
    float nextLength = length(nextLeg);
    vec3 previousDirection = safeNormalize(previousLeg, nextLeg);
    vec3 nextDirection = safeNormalize(nextLeg, previousLeg);
    vec3 tangentDirection = safeNormalize(
            previousDirection + nextDirection, nextDirection);
    float alignment = clamp(dot(previousDirection, nextDirection),
            -1.0, 1.0);
    float cornerRetention = mix(0.16, 0.58,
            smoothstep(0.94, 0.995, alignment));
    return tangentDirection * min(previousLength, nextLength)
            * cornerRetention;
}

vec3 siphonHermitePoint(vec3 start, vec3 end,
                        vec3 startTangent, vec3 endTangent,
                        float segmentT) {
    float t2 = segmentT * segmentT;
    float t3 = t2 * segmentT;
    return start * (2.0 * t3 - 3.0 * t2 + 1.0)
            + startTangent * (t3 - 2.0 * t2 + segmentT)
            + end * (-2.0 * t3 + 3.0 * t2)
            + endTangent * (t3 - t2);
}

vec3 siphonPathPoint(float curveT) {
    int segmentIndex;
    float segmentT;
    siphonPathSegment(curveT, segmentIndex, segmentT);
    vec3 start = siphonPathNode(segmentIndex);
    vec3 end = siphonPathNode(segmentIndex + 1);
    vec3 startTangent = siphonPathNodeTangent(segmentIndex);
    vec3 endTangent = siphonPathNodeTangent(segmentIndex + 1);
    return siphonHermitePoint(start, end, startTangent, endTangent,
            segmentT);
}

vec3 siphonPathDerivative(float curveT) {
    int segmentIndex;
    float segmentT;
    siphonPathSegment(curveT, segmentIndex, segmentT);
    vec3 start = siphonPathNode(segmentIndex);
    vec3 end = siphonPathNode(segmentIndex + 1);
    vec3 startTangent = siphonPathNodeTangent(segmentIndex);
    vec3 endTangent = siphonPathNodeTangent(segmentIndex + 1);
    float t2 = segmentT * segmentT;
    vec3 localDerivative =
            start * (6.0 * t2 - 6.0 * segmentT)
            + startTangent * (3.0 * t2 - 4.0 * segmentT + 1.0)
            + end * (-6.0 * t2 + 6.0 * segmentT)
            + endTangent * (3.0 * t2 - 2.0 * segmentT);
    return localDerivative * float(SIPHON_BOLT_SEGMENTS);
}

vec3 siphonPathFrameTangent(float curveT) {
    return safeNormalize(siphonPathDerivative(curveT),
            SiphonP3 - SiphonP0);
}

float siphonPathArcLength() {
    float total = 0.0;
    vec3 previous = siphonPathPoint(0.0);
    for (int segmentIndex = 0;
            segmentIndex < SIPHON_BOLT_SEGMENTS; segmentIndex++) {
        vec3 pathStart = siphonPathNode(segmentIndex);
        vec3 pathEnd = siphonPathNode(segmentIndex + 1);
        vec3 pathStartTangent = siphonPathNodeTangent(segmentIndex);
        vec3 pathEndTangent = siphonPathNodeTangent(segmentIndex + 1);
        for (int subdivision = 1;
                subdivision <= SIPHON_CURVE_SUBDIVISIONS; subdivision++) {
            float localT = float(subdivision)
                    / float(SIPHON_CURVE_SUBDIVISIONS);
            vec3 current = siphonHermitePoint(pathStart, pathEnd,
                    pathStartTangent, pathEndTangent, localT);
            total += distance(previous, current);
            previous = current;
        }
    }
    return total;
}

void curveFrame(float curveT, out vec3 center, out vec3 tangent,
                out vec3 side, out vec3 up) {
    center = siphonPathPoint(curveT);
    vec3 chord = safeNormalize(SiphonP3 - SiphonP0, vec3(1.0, 0.0, 0.0));
    vec3 rootTangent = siphonPathFrameTangent(0.0);
    float referenceBlend = smoothstep(0.70, 0.96, abs(rootTangent.z));
    vec3 rootReference = mix(vec3(0.0, 0.0, 1.0),
            vec3(0.0, 1.0, 0.0), referenceBlend);
    rootReference -= rootTangent * dot(rootReference, rootTangent);
    rootReference = safeNormalize(rootReference, vec3(0.0, 1.0, 0.0));
    vec3 rootSide = safeNormalize(cross(rootReference, rootTangent),
            vec3(0.0, 1.0, 0.0));

    tangent = siphonPathFrameTangent(curveT);
    vec3 rotationAxis = cross(rootTangent, tangent);
    float tangentCosine = clamp(dot(rootTangent, tangent), -1.0, 1.0);
    vec3 transportedSide = rootSide
            + cross(rotationAxis, rootSide)
            + cross(rotationAxis, cross(rotationAxis, rootSide))
                    / max(1.0 + tangentCosine, 0.0001);
    transportedSide -= tangent * dot(transportedSide, tangent);
    side = safeNormalize(transportedSide, rootSide);
    up = safeNormalize(cross(tangent, side), cross(rootTangent, rootSide));
}

float siphonRadius(float curveT) {
    // Match the presentation shader's thin electrical channel exactly.
    // Voxel floors keep the 48x12x12 transport lattice from collapsing the
    // channel while the body-relative values remain visibly bolt-like.
    float u = saturate(curveT);
    float rootRadius = max(BodyRadius * 0.055, VoxelSize * 0.90);
    float shoulderRadius = max(BodyRadius * 0.070, VoxelSize * 1.10);
    float endRadius = max(SiphonEndRadius * 0.72, VoxelSize * 0.90);
    float throat = smoothstep(0.0, 0.12, u);
    float downstream = smoothstep(0.10, 1.0, u);
    return mix(mix(rootRadius, shoulderRadius, throat), endRadius,
            downstream);
}

void main() {
    int atlasX = int(floor(gl_FragCoord.x));
    int y = int(floor(gl_FragCoord.y));
    int z = atlasX / SIPHON_X;
    int x = atlasX - z * SIPHON_X;
    ivec3 cell = ivec3(x, y, z);
    if (!siphonCellInBounds(cell)) {
        fragColor = vec4(0.0);
        return;
    }

    float curveT = (float(x) + 0.5) / float(SIPHON_X);
    vec2 radial = vec2(
        (float(y) + 0.5) / float(SIPHON_Y) * 2.0 - 1.0,
        (float(z) + 0.5) / float(SIPHON_Z) * 2.0 - 1.0
    );
    if (dot(radial, radial) > 1.0) {
        fragColor = vec4(0.0);
        return;
    }

    float progress = saturate(SiphonProgress);
    float pathScale = max(siphonPathArcLength(), BodyRadius * 1.4);
    float pathSpeed = pathScale
            * (1.55 + progress * 0.30 + FinalCollapse * 0.85);
    float derivativeLength = max(length(siphonPathDerivative(curveT)),
            BodyRadius * 0.10);
    float curveAdvance = pathSpeed / derivativeLength * DeltaTime;
    // Semi-Lagrangian backtracing remains stable beyond one cell per step.
    // Three cells lets Low/Medium quality reach P3 on the same visual schedule
    // instead of making channel fill time depend on the simulation preset.
    curveAdvance = min(curveAdvance, 3.0 / float(SIPHON_X));

    // The changing polyline now owns all electrical motion. Keep transported
    // density coherent in a nearly circular cross-section instead of twisting,
    // shearing, and breathing it into a hose-like ribbon.
    float radialPull = mix(0.18, 0.42, progress)
            + FinalCollapse * 0.18;
    vec2 radialVelocity = -radial * radialPull;
    vec2 maximumRadialDisplacement = vec2(2.0 * 1.15 / float(SIPHON_Y),
            2.0 * 1.15 / float(SIPHON_Z));
    vec2 radialDisplacement = clamp(radialVelocity * DeltaTime,
            -maximumRadialDisplacement, maximumRadialDisplacement);

    float previousT = curveT - curveAdvance;
    vec2 previousRadial = radial - radialDisplacement;
    float mobile = sampleSiphonDensity(previousT, previousRadial);

    vec3 rootCenter;
    vec3 rootTangent;
    vec3 rootSide;
    vec3 rootUp;
    curveFrame(0.0, rootCenter, rootTangent, rootSide, rootUp);
    float rootRadius = siphonRadius(0.0);
    vec3 bodySamplePosition = rootCenter
            + rootSide * (radial.x * rootRadius)
            + rootUp * (radial.y * rootRadius);
    vec3 bodyCellSize = vec3(
        VolumeSize.x / float(BODY_X),
        VolumeSize.y * 2.0 / float(BODY_Y),
        VolumeSize.z * 2.0 / float(BODY_Z)
    );
    float tangentVoxel = 1.0 / max(length(rootTangent / bodyCellSize), 0.0001);
    float bodyTransfer = sampleBodyTransferLocal(bodySamplePosition) * 0.50
            + sampleBodyTransferLocal(bodySamplePosition
                    - rootTangent * tangentVoxel) * 0.25
            + sampleBodyTransferLocal(bodySamplePosition
                    + rootTangent * tangentVoxel) * 0.25;
    // G is the amount transferred during this step. Convert that flux to the
    // concentration occupying the inlet span using its actual CFL advance.
    // Covering every crossed cell prevents a multi-cell backtrace from leaving
    // empty gaps immediately downstream of the boundary.
    float axialCfl = max(curveAdvance * float(SIPHON_X), 0.28);
    float inletCells = max(axialCfl, 1.0);
    float rootKernel = 1.0 - smoothstep(inletCells - 0.25,
            inletCells + 0.25, curveT * float(SIPHON_X));
    float inletConcentration = min(bodyTransfer / axialCfl, 2.5);
    // The far-to-inlet body wave needs time to advect through the source SDF.
    // Maintain a small cohesive throat feed as soon as depletion begins so the
    // opposite inlet-to-ball expansion is visible immediately. Real transfer
    // flux takes over whenever it exceeds this presentation floor.
    float feedEnvelope = smoothstep(0.003, 0.025, SiphonProgress)
            * (1.0 - smoothstep(0.0, 0.06, FinalCollapse));
    inletConcentration = max(inletConcentration, 0.11 * feedEnvelope);
    mobile = mix(mobile, inletConcentration, rootKernel);

    // Begin this inlet-to-ball trailing front just before the final body patch
    // disappears. The narrow overlap removes the dead pause without allowing
    // visual depletion and stored density to diverge.
    float trailingProgress = smoothstep(0.015, 0.94, FinalCollapse);
    float trailingFront = mix(-0.06, 1.08, trailingProgress);
    float trailingGate = smoothstep(trailingFront - 0.045,
            trailingFront + 0.045, curveT);
    mobile *= trailingGate;

    float endpointAbsorption = smoothstep(0.945, 0.998, curveT)
            * mix(0.08, 24.0, smoothstep(0.05, 0.85, FinalCollapse));
    float dissipation = 0.070 + Destabilization * 0.045
            + FinalCollapse * FinalCollapse * 0.12;
    mobile *= exp(-DeltaTime * (dissipation + endpointAbsorption));
    fragColor = vec4(clamp(mobile, 0.0, 2.5), 0.0, 0.0, 1.0);
}
