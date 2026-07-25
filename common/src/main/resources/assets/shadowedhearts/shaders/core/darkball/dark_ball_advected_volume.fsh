#version 150

uniform sampler2D ShapeSampler;
uniform sampler2D SurfaceAttributeSampler;
uniform sampler2D DensitySampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D BodyDensitySampler;

uniform mat4 InvProjMat;
uniform mat4 CameraToWorldMat;
uniform vec3 CameraPos;
uniform vec3 VolumeRoot;
uniform vec3 VolumeAxis;
uniform vec3 VolumeSide;
uniform vec3 VolumeUp;
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
uniform vec3 SpikeTransportOrigin;
uniform float BodyRadius;
uniform float BodyMaxX;
uniform float VoxelSize;
uniform float SiphonEndRadius;
uniform float SiphonSinkFeather;
uniform float Formation;
uniform float Destabilization;
uniform float TurbulenceBlend;
uniform float SiphonProgress;
uniform float FinalCollapse;
uniform float EffectFade;
uniform float GameTime;
uniform float DeformationTime;
uniform float SpikeFlowTime;
uniform int DeformationDebugMode;
uniform int VortexCellCount;
uniform vec4 VortexPhaseSin;
uniform vec4 VortexPhaseCos;
uniform int BodyRaymarchSamples;
uniform int SiphonRaymarchSamples;

in vec2 texCoord0;
out vec4 fragColor;

const int GRID_X = 96;
const int GRID_Y = 64;
const int GRID_Z = 64;
const int SIPHON_X = 48;
const int SIPHON_Y = 12;
const int SIPHON_Z = 12;
const int MAX_BODY_STEPS = 96;
const int MAX_SIPHON_STEPS = 24;
const int SIPHON_BOLT_SEGMENTS = 13;
// Transport and closest-curve queries retain three chords per smooth control
// interval (39 total) so atlas advection follows the continuously evolving
// Hermite centerline accurately.
const int SIPHON_CURVE_SUBDIVISIONS = 3;
// Presentation uses a coarser static node-aware partition of that same curve.
// Sixteen square prisms preserve every control-node elbow while remaining
// independent from the transport/arc-length resolution above.
const int SIPHON_PRESENTATION_PRISM_COUNT = 16;
const float TAU = 6.28318530718;
const float FIELD_DEBUG_AURA = 0.125;
const float FIELD_DEBUG_CORE = 0.375;
const float FIELD_DEBUG_UNRESOLVED = 0.875;
const float AURA_DEPTH_TAG_BIAS = 2.0;
// Increase all outward-spike coordinates together so the surface carries more
// peaks without changing their apparent travel velocity. The broad inward
// trough field removes this scale again before sampling.
const float SPIKE_SPATIAL_FREQUENCY_SCALE = 1.50;
const float BODY_SPIKE_FLOW_SPAN =
        8.0 * SPIKE_SPATIAL_FREQUENCY_SCALE;
const float SPIKE_FLOW_PHASE_SPEED =
        3.00 * SPIKE_SPATIAL_FREQUENCY_SCALE;
// Signed body-deformation tuning. Values are fractions of BodyRadius unless
// explicitly described as voxel counts. The small pre-siphon values preserve
// the readable source silhouette; the larger values arrive before depletion
// and remain visible throughout the full-strength turbulence hold.
const float BODY_AURA_INITIAL_EXPANSION = 0.008;
const float BODY_AURA_FINAL_EXPANSION = 0.040;
const float BODY_SPIKE_INITIAL_DISPLACEMENT = 0.060;
const float BODY_SPIKE_MAX_DISPLACEMENT = 0.180;
const float BODY_INDENT_INITIAL_DISPLACEMENT = 0.008;
const float BODY_INDENT_MAX_DISPLACEMENT = 0.075;
// Retract only the turbulent displacement before the depletion surface reaches
// its source material. The persistent expanded carrier remains, preventing the
// front from cutting off a narrow spike root while its outer cap is visible.
const float BODY_ATTACHMENT_EMPTY_REMAINING = 0.58;
const float BODY_ATTACHMENT_FULL_REMAINING = 0.92;
// Probe just inside the projected source surface, then reserve enough local
// material that thin horns, tails, and limbs cannot be carved through.
const float BODY_INDENT_THICKNESS_PROBE_VOXELS = 0.80;
const float BODY_INDENT_THICKNESS_RESERVE_VOXELS = 1.25;
const float BODY_INDENT_THICKNESS_FRACTION = 0.24;
const float SIPHON_SPIKE_FREQUENCY_PER_RADIUS =
        3.20 * SPIKE_SPATIAL_FREQUENCY_SCALE;
const float SIPHON_SURFACE_THRESHOLD = 0.08;

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

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float smoothNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(cell + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(cell + vec3(1.0, 1.0, 1.0));
    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

vec3 viewFromDepth(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * ndc;
    view.xyz *= abs(view.w) > 0.000001 ? 1.0 / view.w : 1.0;
    return view.xyz;
}

// Vanilla folds view bob, hurt tilt, and similar affine camera motion into the
// projection matrix. The physical CameraPos is therefore not necessarily the
// pinhole of InvProjMat. Recover that pinhole projectively so the raymarch,
// raster mask, and scene depth all use the exact same camera transform.
vec3 projectionEyeView() {
    vec4 eye = InvProjMat * vec4(0.0, 0.0, 1.0, 0.0);
    return abs(eye.w) > 0.000001 ? eye.xyz / eye.w : vec3(0.0);
}

vec3 worldFromDepth(vec2 uv, float depth) {
    vec3 view = viewFromDepth(uv, depth);
    return CameraPos + (CameraToWorldMat * vec4(view, 0.0)).xyz;
}

vec3 worldToLocal(vec3 worldPos) {
    vec3 rel = worldPos - VolumeRoot;
    return vec3(dot(rel, VolumeAxis), dot(rel, VolumeSide), dot(rel, VolumeUp));
}

vec3 worldDirectionToLocal(vec3 direction) {
    return vec3(dot(direction, VolumeAxis), dot(direction, VolumeSide), dot(direction, VolumeUp));
}

bool cellInBounds(ivec3 cell) {
    return cell.x >= 0 && cell.x < GRID_X
        && cell.y >= 0 && cell.y < GRID_Y
        && cell.z >= 0 && cell.z < GRID_Z;
}

vec4 shapeTexel(ivec3 cell) {
    if (!cellInBounds(cell)) {
        return vec4(VolumeSize.y * 4.0, 0.0, 1.0, 0.0);
    }
    return texelFetch(ShapeSampler, ivec2(cell.z * GRID_X + cell.x, cell.y), 0);
}

vec4 surfaceAttributeTexel(ivec3 cell) {
    if (!cellInBounds(cell)) {
        return vec4(0.0);
    }
    return texelFetch(SurfaceAttributeSampler,
            ivec2(cell.z * GRID_X + cell.x, cell.y), 0);
}

float bodyMobileTexel(ivec3 cell) {
    if (!cellInBounds(cell)) {
        return 0.0;
    }
    return texelFetch(BodyDensitySampler,
            ivec2(cell.z * GRID_X + cell.x, cell.y), 0).r;
}

bool siphonCellInBounds(ivec3 cell) {
    return cell.x >= 0 && cell.x < SIPHON_X
        && cell.y >= 0 && cell.y < SIPHON_Y
        && cell.z >= 0 && cell.z < SIPHON_Z;
}

float siphonDensityTexel(ivec3 cell) {
    if (!siphonCellInBounds(cell)) {
        return 0.0;
    }
    return texelFetch(DensitySampler,
            ivec2(cell.z * SIPHON_X + cell.x, cell.y), 0).r;
}

vec3 localToGrid(vec3 localPos) {
    return vec3(
        localPos.x / max(VolumeSize.x, 0.0001) * float(GRID_X) - 0.5,
        (localPos.y / max(VolumeSize.y, 0.0001) * 0.5 + 0.5) * float(GRID_Y) - 0.5,
        (localPos.z / max(VolumeSize.z, 0.0001) * 0.5 + 0.5) * float(GRID_Z) - 0.5
    );
}

vec4 sampleShapeLocal(vec3 localPos) {
    vec3 grid = localToGrid(localPos);
    if (grid.x < -1.0 || grid.x > float(GRID_X)
            || grid.y < -1.0 || grid.y > float(GRID_Y)
            || grid.z < -1.0 || grid.z > float(GRID_Z)) {
        return vec4(VolumeSize.y * 4.0, 0.0, 1.0, 0.0);
    }
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    vec4 c000 = shapeTexel(base);
    vec4 c100 = shapeTexel(base + ivec3(1, 0, 0));
    vec4 c010 = shapeTexel(base + ivec3(0, 1, 0));
    vec4 c110 = shapeTexel(base + ivec3(1, 1, 0));
    vec4 c001 = shapeTexel(base + ivec3(0, 0, 1));
    vec4 c101 = shapeTexel(base + ivec3(1, 0, 1));
    vec4 c011 = shapeTexel(base + ivec3(0, 1, 1));
    vec4 c111 = shapeTexel(base + ivec3(1, 1, 1));
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y),
               mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

vec3 sampleSmoothedShapeNormalLocal(vec3 localPos) {
    vec3 grid = localToGrid(localPos);
    if (grid.x < -1.0 || grid.x > float(GRID_X)
            || grid.y < -1.0 || grid.y > float(GRID_Y)
            || grid.z < -1.0 || grid.z > float(GRID_Z)) {
        return vec3(0.0);
    }
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    vec3 n000 = surfaceAttributeTexel(base).gba;
    vec3 n100 = surfaceAttributeTexel(base + ivec3(1, 0, 0)).gba;
    vec3 n010 = surfaceAttributeTexel(base + ivec3(0, 1, 0)).gba;
    vec3 n110 = surfaceAttributeTexel(base + ivec3(1, 1, 0)).gba;
    vec3 n001 = surfaceAttributeTexel(base + ivec3(0, 0, 1)).gba;
    vec3 n101 = surfaceAttributeTexel(base + ivec3(1, 0, 1)).gba;
    vec3 n011 = surfaceAttributeTexel(base + ivec3(0, 1, 1)).gba;
    vec3 n111 = surfaceAttributeTexel(base + ivec3(1, 1, 1)).gba;
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float sampleTransportOrderLocal(vec3 localPos) {
    // Clamp before reconstructing the flattened 3D atlas. Sampling through
    // an out-of-bounds sentinel would blend an unrelated phase into boundary
    // cells and recreate a split peak at the capture-volume edge.
    vec3 grid = clamp(localToGrid(localPos),
            vec3(0.0),
            vec3(float(GRID_X - 1), float(GRID_Y - 1),
                    float(GRID_Z - 1)));
    ivec3 base = ivec3(floor(grid));
    ivec3 upper = min(base + ivec3(1),
            ivec3(GRID_X - 1, GRID_Y - 1, GRID_Z - 1));
    vec3 f = fract(grid);
    float t000 = surfaceAttributeTexel(base).r;
    float t100 = surfaceAttributeTexel(ivec3(upper.x, base.y, base.z)).r;
    float t010 = surfaceAttributeTexel(ivec3(base.x, upper.y, base.z)).r;
    float t110 = surfaceAttributeTexel(ivec3(upper.x, upper.y, base.z)).r;
    float t001 = surfaceAttributeTexel(ivec3(base.x, base.y, upper.z)).r;
    float t101 = surfaceAttributeTexel(ivec3(upper.x, base.y, upper.z)).r;
    float t011 = surfaceAttributeTexel(ivec3(base.x, upper.y, upper.z)).r;
    float t111 = surfaceAttributeTexel(upper).r;
    return mix(mix(mix(t000, t100, f.x), mix(t010, t110, f.x), f.y),
               mix(mix(t001, t101, f.x), mix(t011, t111, f.x), f.y), f.z);
}

// Evaluate the shape and its independently prefiltered normal. An analytical
// derivative of a trilinear SDF jumps at every voxel boundary; projecting the
// spike coordinates with that derivative created the fine hairs and crawling
// seams visible at long displacement. The uploaded normal field is trilinear
// too, but its values—not its derivative—are interpolated, so it remains
// continuous across cell boundaries.
vec4 sampleShapeLocalWithGradient(vec3 localPos, out vec3 sdfGradient) {
    sdfGradient = sampleSmoothedShapeNormalLocal(localPos);
    return sampleShapeLocal(localPos);
}

// Continue the captured SDF beyond the atlas instead of letting the sentinel
// texels hard-clip an aura lobe at the volume box. The nearest valid shape
// sample supplies release order and component identity; Euclidean distance
// outside that box supplies a conservative SDF continuation.
vec4 sampleShapeExtendedLocal(vec3 localPos) {
    vec3 halfCell = vec3(
        VolumeSize.x / float(GRID_X) * 0.5,
        VolumeSize.y / float(GRID_Y),
        VolumeSize.z / float(GRID_Z)
    );
    vec3 minimum = vec3(0.0, -VolumeSize.y, -VolumeSize.z) + halfCell;
    vec3 maximum = vec3(VolumeSize.x, VolumeSize.y, VolumeSize.z) - halfCell;
    vec3 boundedPos = clamp(localPos, minimum, maximum);
    vec4 shape = sampleShapeLocal(boundedPos);
    shape.r += length(localPos - boundedPos);
    return shape;
}

vec4 sampleShapeExtendedLocalWithNormal(vec3 localPos,
                                        out vec3 sdfNormal) {
    vec3 halfCell = vec3(
        VolumeSize.x / float(GRID_X) * 0.5,
        VolumeSize.y / float(GRID_Y),
        VolumeSize.z / float(GRID_Z)
    );
    vec3 minimum = vec3(0.0, -VolumeSize.y, -VolumeSize.z) + halfCell;
    vec3 maximum = vec3(VolumeSize.x, VolumeSize.y, VolumeSize.z) - halfCell;
    vec3 boundedPos = clamp(localPos, minimum, maximum);
    vec3 boundedGradient;
    vec4 shape = sampleShapeLocalWithGradient(boundedPos, boundedGradient);
    vec3 outsideDelta = localPos - boundedPos;
    float outsideDistance = length(outsideDelta);
    if (outsideDistance > 0.000001) {
        vec3 insideAxis = vec3(1.0)
                - step(vec3(0.000001), abs(outsideDelta));
        boundedGradient *= insideAxis;
        shape.r += outsideDistance;
        boundedGradient += outsideDelta / outsideDistance;
    }
    vec3 radialFallback = localPos - vec3(BodyMaxX * 0.50, 0.0, 0.0);
    sdfNormal = safeNormalize(boundedGradient, radialFallback);
    return shape;
}

float sampleBodyMobileLocal(vec3 localPos) {
    vec3 grid = localToGrid(localPos);
    if (grid.x < -1.0 || grid.x > float(GRID_X)
            || grid.y < -1.0 || grid.y > float(GRID_Y)
            || grid.z < -1.0 || grid.z > float(GRID_Z)) {
        return 0.0;
    }
    ivec3 base = ivec3(floor(grid));
    vec3 f = fract(grid);
    float c000 = bodyMobileTexel(base);
    float c100 = bodyMobileTexel(base + ivec3(1, 0, 0));
    float c010 = bodyMobileTexel(base + ivec3(0, 1, 0));
    float c110 = bodyMobileTexel(base + ivec3(1, 1, 0));
    float c001 = bodyMobileTexel(base + ivec3(0, 0, 1));
    float c101 = bodyMobileTexel(base + ivec3(1, 0, 1));
    float c011 = bodyMobileTexel(base + ivec3(0, 1, 1));
    float c111 = bodyMobileTexel(base + ivec3(1, 1, 1));
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y),
               mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

float sampleSiphonDensityCoordinates(float curveT, vec2 radial) {
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

    // Chord-limit every tangent. Nearly straight runs retain enough tangent to
    // flow, while resolved electrical doglegs collapse rapidly toward a short
    // tangent so the visible path keeps a decisive cartoon elbow.
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

float siphonPresentationCurveT(int boundaryIndex) {
    if (boundaryIndex <= 0) {
        return 0.0;
    }
    if (boundaryIndex >= SIPHON_PRESENTATION_PRISM_COUNT) {
        return 1.0;
    }

    // Static node-aware 16-prism partition:
    //   all thirteen Hermite interval boundaries remain visible joints;
    //   animated intervals 3, 6, and 9 receive one fixed midpoint.
    // Never derive this partition from the animated path or it will pop.
    if (boundaryIndex <= 3) {
        return float(boundaryIndex) / float(SIPHON_BOLT_SEGMENTS);
    }
    int offsetIndex = boundaryIndex - 4;
    int block = offsetIndex / 4;
    int remainder = offsetIndex - block * 4;
    float pathUnits = float(3 + block * 3)
            + (remainder == 0 ? 0.5 : float(remainder));
    return pathUnits / float(SIPHON_BOLT_SEGMENTS);
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

vec3 siphonTransportedFrameSide(vec3 rootTangent, vec3 rootSide,
                                vec3 tangent) {
    vec3 rotationAxis = cross(rootTangent, tangent);
    float tangentCosine = clamp(dot(rootTangent, tangent), -1.0, 1.0);
    vec3 transportedSide = rootSide
            + cross(rotationAxis, rootSide)
            + cross(rotationAxis, cross(rotationAxis, rootSide))
                    / max(1.0 + tangentCosine, 0.0001);
    transportedSide -= tangent * dot(transportedSide, tangent);
    return safeNormalize(transportedSide, rootSide);
}

vec3 rotationMinimizingPrismSide(vec3 prismAxis, vec3 previousSide,
                                 vec3 previousUp) {
    // Parallel-transport the prior square frame one chord at a time. Projecting
    // onto the new normal plane avoids the antiparallel singularity in a
    // root-to-current Rodrigues rotation and prevents 90-degree roll pops.
    vec3 projectedSide = previousSide
            - prismAxis * dot(previousSide, prismAxis);
    if (dot(projectedSide, projectedSide) <= 0.0000001) {
        projectedSide = previousUp
                - prismAxis * dot(previousUp, prismAxis);
    }
    if (dot(projectedSide, projectedSide) <= 0.0000001) {
        vec3 absoluteAxis = abs(prismAxis);
        vec3 leastAligned = absoluteAxis.x <= absoluteAxis.y
                && absoluteAxis.x <= absoluteAxis.z
                ? vec3(1.0, 0.0, 0.0)
                : (absoluteAxis.y <= absoluteAxis.z
                        ? vec3(0.0, 1.0, 0.0)
                        : vec3(0.0, 0.0, 1.0));
        projectedSide = leastAligned
                - prismAxis * dot(leastAligned, prismAxis);
    }
    vec3 side = safeNormalize(projectedSide, previousSide);
    vec3 up = safeNormalize(cross(prismAxis, side), previousUp);
    return safeNormalize(cross(up, prismAxis), side);
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
    side = siphonTransportedFrameSide(rootTangent, rootSide, tangent);
    up = safeNormalize(cross(tangent, side), cross(rootTangent, rootSide));
}

// Shared high-frequency structure for both the body boundary and the siphon.
// The caller supplies two transverse coordinates plus one monotonic flow
// coordinate, avoiding an ever-growing spatial backtrace through a curved or
// spatially varying direction field.
float spikeFieldAtCoordinates(vec3 spikeCoordinates) {
    float carrier = smoothNoise(spikeCoordinates
            + vec3(6.31, -3.77, 2.19));
    float peakSignal = carrier * 0.84;

    // Normalize against the carrier's reachable maximum. The former 0.88
    // upper edge could never be reached by a signal capped at 0.84, and its
    // final gain-and-clamp rounded the tallest crests into small plateaus.
    //
    // A small sine contribution preserves an attached shoulder while the
    // quintic term concentrates nearly all height at the crest, producing a
    // much sharper point without a discontinuous threshold.
    float profileT = saturate((peakSignal - 0.46) / (0.84 - 0.46));
    float sineEaseIn = 1.0 - cos(profileT * TAU * 0.25);
    float quinticEaseIn = profileT * profileT * profileT
            * profileT * profileT;
    return mix(sineEaseIn, quinticEaseIn, 0.72);
}

float bodyIndentFieldAtCoordinates(vec3 spikeCoordinates,
                                   float outwardSpikeField) {
    // Use the same transported phase as the outward crests, but sample an
    // independent, lower-frequency volume. This produces broad troughs instead
    // of a mirrored saw-tooth copy of the narrow spike field.
    // Outward density increased, but the inward troughs should remain broad.
    // Undo the shared frequency multiplier for this independent sampler.
    vec3 broadCoordinates =
            spikeCoordinates / SPIKE_SPATIAL_FREQUENCY_SCALE;
    float broadCarrier = smoothNoise(
            broadCoordinates * vec3(0.52, 0.52, 0.64)
                    + vec3(-11.73, 8.29, 4.61));
    float broadCompanion = smoothNoise(
            broadCoordinates * vec3(0.31, 0.31, 0.42)
                    + vec3(3.17, -14.09, 9.43));
    float troughSignal = mix(1.0 - broadCarrier, broadCompanion, 0.24);
    float troughProfile = smoothstep(0.50, 0.80, troughSignal);
    troughProfile = troughProfile * troughProfile
            * (3.0 - 2.0 * troughProfile);

    // Preserve the established outward crest silhouette. The independent
    // trough may own the broad space between crests, but cannot blunt a spike
    // by subtracting from the same high-value peak.
    float spikeExclusion = 1.0 - smoothstep(0.01, 0.12,
            outwardSpikeField);
    return troughProfile * spikeExclusion;
}

vec3 bodySpikeTransportCoordinates(vec3 surfaceAnchor,
                                   float transportOrder) {
    // A flow crest needs an identity that stays approximately constant while
    // the Euclidean transport phase advances toward the exact inlet. The old
    // body-position projections drifted across unrelated transverse noise
    // cells and made a tracked spike retract and regrow during its journey.
    vec3 inletToAnchor = surfaceAnchor - SpikeTransportOrigin;
    float inletDistance = length(inletToAnchor);
    vec3 inletFallback = safeNormalize(
            SiphonP3 - SpikeTransportOrigin, vec3(1.0, 0.0, 0.0));
    vec3 outletRay = safeNormalize(inletToAnchor, inletFallback);
    float singularityBlend = smoothstep(
            max(VoxelSize * 1.50, BodyRadius * 0.035),
            max(VoxelSize * 4.00, BodyRadius * 0.14),
            inletDistance);
    vec3 streamIdentity = safeNormalize(
            mix(inletFallback, outletRay, singularityBlend),
            inletFallback);
    vec3 transverseA = vec3(0.7071068, 0.7071068, 0.0);
    vec3 transverseB = vec3(0.4082483, -0.4082483, 0.8164966);
    return vec3(
        dot(streamIdentity, transverseA)
                * (7.00 * SPIKE_SPATIAL_FREQUENCY_SCALE),
        dot(streamIdentity, transverseB)
                * (7.00 * SPIKE_SPATIAL_FREQUENCY_SCALE),
        transportOrder * BODY_SPIKE_FLOW_SPAN
                - SpikeFlowTime * SPIKE_FLOW_PHASE_SPEED
    );
}

float transportedBodySpikeField(vec3 surfaceAnchor, float transportOrder) {
    return spikeFieldAtCoordinates(bodySpikeTransportCoordinates(
            surfaceAnchor, transportOrder));
}

float smoothlyCapPositive(float value, float limit) {
    if (value <= 0.0 || limit <= 0.000001) {
        return 0.0;
    }
    // Smooth asymptotic cap: approximately identity for small requests, never
    // exceeds the local budget, and has no hard min() knee that could trace
    // thickness voxel boundaries onto the visible contour.
    return limit * (1.0 - exp(-value / limit));
}

float transportedSiphonSpikeField(float curveDistance, vec2 radial) {
    float radialLength = length(radial);
    vec2 radialDirection = radialLength > 0.0001
            ? radial / radialLength : vec2(1.0, 0.0);
    vec3 flowCoordinates = vec3(
        radialDirection
                * (2.80 * SPIKE_SPATIAL_FREQUENCY_SCALE),
        BODY_SPIKE_FLOW_SPAN
                + curveDistance / max(BodyRadius, 0.0001)
                * SIPHON_SPIKE_FREQUENCY_PER_RADIUS
                - SpikeFlowTime * SPIKE_FLOW_PHASE_SPEED
    );
    return spikeFieldAtCoordinates(flowCoordinates);
}

float siphonRadius(float curveT) {
    // Match the transport shader's thin electrical channel exactly. Voxel
    // floors keep the low-resolution radial atlas usable without allowing its
    // texel footprint to dictate the visible geometry.
    float u = saturate(curveT);
    float rootRadius = max(BodyRadius * 0.055, VoxelSize * 0.90);
    float shoulderRadius = max(BodyRadius * 0.070, VoxelSize * 1.10);
    float endRadius = max(SiphonEndRadius * 0.72, VoxelSize * 0.90);
    float throat = smoothstep(0.0, 0.12, u);
    float downstream = smoothstep(0.10, 1.0, u);
    return mix(mix(rootRadius, shoulderRadius, throat), endRadius,
            downstream);
}

float siphonMaterialWindow(float curveT) {
    // The leading edge reveals material from the body-side inlet toward the
    // ball while the silhouette is being consumed.
    float leadingProgress = smoothstep(0.003, 0.40, SiphonProgress);
    float leadingFront = mix(0.0, 1.08, leadingProgress);
    float leadingGate = 1.0 - smoothstep(leadingFront - 0.045,
            leadingFront + 0.045, curveT);

    // FinalCollapse begins shortly before the last body material disappears.
    // Its trailing edge creates a narrow handoff overlap, then clears the
    // channel in the same inlet-to-ball direction without stranding material.
    float trailingProgress = smoothstep(0.015, 0.94, FinalCollapse);
    float trailingFront = mix(-0.06, 1.08, trailingProgress);
    float trailingGate = smoothstep(trailingFront - 0.045,
            trailingFront + 0.045, curveT);
    return leadingGate * trailingGate;
}

void closestCurveFrame(vec3 point, out float curveT,
                       out float curveDistance, out vec3 center,
                       out vec3 tangent, out vec3 side, out vec3 up) {
    float bestDistanceSquared = 1.0e30;
    curveT = 0.0;
    curveDistance = 0.0;
    float accumulatedDistance = 0.0;
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
            float segmentStartT = (float(segmentIndex)
                    + float(subdivision - 1)
                            / float(SIPHON_CURVE_SUBDIVISIONS))
                    / float(SIPHON_BOLT_SEGMENTS);
            float segmentEndT = (float(segmentIndex)
                    + float(subdivision)
                            / float(SIPHON_CURVE_SUBDIVISIONS))
                    / float(SIPHON_BOLT_SEGMENTS);
            vec3 current = siphonHermitePoint(pathStart, pathEnd,
                    pathStartTangent, pathEndTangent, localT);
            vec3 segment = current - previous;
            float segmentLengthSquared = max(dot(segment, segment),
                    0.0000001);
            float segmentLength = length(segment);
            float along = saturate(dot(point - previous, segment)
                    / segmentLengthSquared);
            vec3 candidate = previous + segment * along;
            float candidateDistanceSquared = dot(
                    point - candidate, point - candidate);
            if (candidateDistanceSquared < bestDistanceSquared) {
                bestDistanceSquared = candidateDistanceSquared;
                curveT = mix(segmentStartT, segmentEndT, along);
                curveDistance = accumulatedDistance
                        + segmentLength * along;
            }
            accumulatedDistance += segmentLength;
            previous = current;
        }
    }
    curveFrame(curveT, center, tangent, side, up);
}

float siphonSinkGateAt(vec3 localPos, float curveT) {
    vec3 terminalTangent = siphonPathFrameTangent(1.0);
    float terminalAxial = dot(localPos - SiphonP3, terminalTangent);
    float terminalGate = 1.0 - smoothstep(
            -max(SiphonSinkFeather, 0.0001), 0.0, terminalAxial);
    // A violently folded upstream segment can cross the terminal tangent's
    // half-space without being near the ball. Restrict absorption to the final
    // path neighborhood so that inversion cannot erase unrelated bolt legs.
    return mix(1.0, terminalGate, smoothstep(0.84, 0.98,
            saturate(curveT)));
}

float sampleSiphonMaterialDensityAt(vec3 localPos, float curveT) {
    vec3 center;
    vec3 tangent;
    vec3 side;
    vec3 up;
    curveFrame(curveT, center, tangent, side, up);
    float radius = max(siphonRadius(curveT), 0.0001);
    vec3 offset = localPos - center;
    vec2 radial = vec2(dot(offset, side), dot(offset, up)) / radius;

    // The atlas remains the transported-material authority, but it no longer
    // expands the visible radius. Sample slightly inside the geometric surface
    // and retain a centerline fallback so a low-resolution radial texel cannot
    // puncture an otherwise continuous electrical channel.
    float atlasInset = mix(0.62, 0.56, saturate(
            float(SiphonRaymarchSamples) / float(MAX_SIPHON_STEPS)));
    float radialDensity = sampleSiphonDensityCoordinates(curveT,
            clamp(radial * atlasInset, vec2(-0.82), vec2(0.82)));
    float centerDensity = sampleSiphonDensityCoordinates(curveT, vec2(0.0));
    float transportedDensity = max(radialDensity, centerDensity * 0.70);
    float sinkGate = siphonSinkGateAt(localPos, curveT);
    if (sinkGate <= 0.0) {
        return 0.0;
    }
    return transportedDensity * siphonMaterialWindow(curveT)
            * sinkGate;
}

float sampleDensityLocal(vec3 localPos) {
    float curveT;
    float curveDistance;
    vec3 center;
    vec3 tangent;
    vec3 side;
    vec3 up;
    closestCurveFrame(localPos, curveT, curveDistance,
            center, tangent, side, up);
    float radius = max(siphonRadius(curveT), 0.0001);
    vec3 offset = localPos - center;
    vec2 radial = vec2(dot(offset, side), dot(offset, up)) / radius;
    float geometryGate = 1.0 - smoothstep(0.98, 1.02, length(radial));
    return sampleSiphonMaterialDensityAt(localPos, curveT) * geometryGate;
}

bool intersectBox(vec3 origin, vec3 direction, vec3 boxMin, vec3 boxMax,
                  out float entry, out float exitDistance) {
    vec3 safeDirection = vec3(
        abs(direction.x) < 0.000001 ? (direction.x < 0.0 ? -0.000001 : 0.000001) : direction.x,
        abs(direction.y) < 0.000001 ? (direction.y < 0.0 ? -0.000001 : 0.000001) : direction.y,
        abs(direction.z) < 0.000001 ? (direction.z < 0.0 ? -0.000001 : 0.000001) : direction.z
    );
    vec3 inverse = 1.0 / safeDirection;
    vec3 first = (boxMin - origin) * inverse;
    vec3 second = (boxMax - origin) * inverse;
    vec3 nearValue = min(first, second);
    vec3 farValue = max(first, second);
    entry = max(max(nearValue.x, nearValue.y), nearValue.z);
    exitDistance = min(min(farValue.x, farValue.y), farValue.z);
    entry = max(entry, 0.0);
    return exitDistance >= entry;
}

float currentReleaseFront() {
    if (SiphonProgress <= 0.001) {
        return -1.0;
    }
    return clamp(SiphonProgress * 1.16 - 0.045 + FinalCollapse * 0.16,
            0.0, 1.30);
}

float releaseRemaining(float releaseOrder) {
    float front = currentReleaseFront();
    if (front < 0.0) {
        return 1.0;
    }
    float width = 0.130 + (1.0 - min(SiphonProgress, 1.0)) * 0.045;
    return smoothstep(front - width, front + width, releaseOrder);
}

float bodyDeformationAttachment(float releaseOrder) {
    return smoothstep(BODY_ATTACHMENT_EMPTY_REMAINING,
            BODY_ATTACHMENT_FULL_REMAINING,
            releaseRemaining(releaseOrder));
}

float visibleReleaseRemaining(float releaseOrder) {
    // Transport keeps the broad, conservative release ramp above. The visible
    // silhouette uses a much narrower threshold so optical path length and
    // ray-sample jitter cannot turn a clean release order into a fuzzy fade.
    return smoothstep(0.42, 0.58, releaseRemaining(releaseOrder));
}

float silhouetteMaterialResponse(float density) {
    float opticalResponse = 1.0 - exp(-max(density, 0.0) * 14.0);
    float cohesiveCore = smoothstep(0.08, 0.70, opticalResponse);
    return mix(opticalResponse, cohesiveCore, 0.45);
}

float siphonSurfaceResponseAt(vec3 localPos) {
    float visibleMobile = silhouetteMaterialResponse(sampleDensityLocal(localPos));
    float sourceBodyDistance = sampleShapeLocal(localPos).r;
    float outsideSourceBody = smoothstep(-VoxelSize * 0.20,
            VoxelSize * 0.85, sourceBodyDistance);
    return visibleMobile * outsideSourceBody;
}

float depletionSurfaceSdf(float releaseOrder) {
    float front = currentReleaseFront();
    if (front < 0.0) {
        return -BodyRadius * 4.0;
    }

    // Release order is dimensionless. Convert it to a conservative local-space
    // distance so it can participate in an intersection with the body SDF.
    float releaseMetric = max(BodyRadius * 0.52, VoxelSize * 7.0);
    return (front - releaseOrder) * releaseMetric;
}

float exactRemainingCoreSdf(vec4 shape) {
    return max(shape.r, depletionSurfaceSdf(shape.b));
}

float auraRemainingBodySdf(vec3 localPos, vec4 shape, vec3 shapeNormal,
                           out float auraOwnership) {
    float coreRemainingSdf = exactRemainingCoreSdf(shape);
    // Java already supplies a smoothstep ramp. Applying a second ease here
    // compressed nearly all visible expansion into only a few frames.
    float auraStrength = clamp(TurbulenceBlend, 0.0, 1.0);
    if (auraStrength <= 0.001) {
        auraOwnership = 0.0;
        return coreRemainingSdf;
    }

    // Project every sample back to the original zero-isosurface before
    // evaluating noise. The field is therefore approximately constant along
    // each source-SDF normal, preventing a high-frequency ambient noise volume
    // from introducing extra crossings or detached exterior shells.
    vec3 surfaceAnchor = localPos - shapeNormal * shape.r;
    // Visual transport has one Euclidean metric across the complete atlas.
    // Depletion keeps the geodesic release order in ShapeSampler.b; separating
    // the two prevents transported peaks from crossing its fallback seam.
    float surfaceTransportOrder = sampleTransportOrderLocal(surfaceAnchor);
    vec3 flowCoordinates = bodySpikeTransportCoordinates(
            surfaceAnchor, surfaceTransportOrder);
    float spikeField = spikeFieldAtCoordinates(flowCoordinates);
    float indentField = bodyIndentFieldAtCoordinates(flowCoordinates,
            spikeField);

    // The Java envelope is a late-accelerating squared smoothstep. Drive the
    // amplitude mix from that pre-depletion envelope instead of SiphonProgress
    // so the strongest signed contour has time to read before extraction.
    float deformationGrowth = auraStrength;
    float uniformExpansion = BodyRadius * auraStrength * mix(
            BODY_AURA_INITIAL_EXPANSION, BODY_AURA_FINAL_EXPANSION,
            deformationGrowth);
    float outwardAmplitude = mix(BODY_SPIKE_INITIAL_DISPLACEMENT,
            BODY_SPIKE_MAX_DISPLACEMENT, deformationGrowth);
    float outwardDisplacement = BodyRadius * auraStrength
            * spikeField * outwardAmplitude;

    float requestedInwardAmplitude = mix(
            BODY_INDENT_INITIAL_DISPLACEMENT,
            BODY_INDENT_MAX_DISPLACEMENT, deformationGrowth);
    float requestedInwardDisplacement = BodyRadius * auraStrength
            * indentField * requestedInwardAmplitude;

    // ShapeSampler.g is meaningful in material cells. Sampling slightly inside
    // the projected source surface avoids blending its thickness with exterior
    // zeroes. A smooth confidence ramp plus an asymptotic cap prevents thin
    // texture-aware features from being erased by inward erosion.
    vec3 thicknessProbe = surfaceAnchor - shapeNormal
            * (VoxelSize * BODY_INDENT_THICKNESS_PROBE_VOXELS);
    float localThickness = max(sampleShapeLocal(thicknessProbe).g, 0.0);
    float usableThickness = max(localThickness
            - VoxelSize * BODY_INDENT_THICKNESS_RESERVE_VOXELS, 0.0);
    float thicknessConfidence = smoothstep(0.0, VoxelSize * 1.50,
            usableThickness);
    float inwardBudget = usableThickness
            * BODY_INDENT_THICKNESS_FRACTION * thicknessConfidence;
    float inwardDisplacement = smoothlyCapPositive(
            requestedInwardDisplacement, inwardBudget);

    // Positive offsets protrude; negative offsets carve the same single
    // texture-aware implicit surface. Depletion support retracts both turbulent
    // terms before their source material is removed. The modest uniform
    // expansion remains as the connected carrier instead of exposing the
    // texture-exact body or leaving a detached spike cap.
    float attachment = bodyDeformationAttachment(shape.b);
    float turbulentDisplacement = outwardDisplacement
            - inwardDisplacement;
    float edgeDisplacement = uniformExpansion
            + turbulentDisplacement * attachment;
    float auraSurfaceSdf = shape.r - edgeDisplacement;
    float depletionSdf = depletionSurfaceSdf(shape.b);
    float auraRemainingSdf = max(auraSurfaceSdf, depletionSdf);

    // Style ownership is symmetric for outward and inward displacement, but
    // only when the deformed body surface actually wins the max-intersection.
    // A depletion-front hit retains core ownership and its positive depth tag.
    float ownershipEpsilon = max(VoxelSize * 0.010, 0.00002);
    float deformedBodyOwns = step(depletionSdf + ownershipEpsilon,
            auraSurfaceSdf);
    float deformationIsVisible = step(ownershipEpsilon,
            abs(edgeDisplacement));
    auraOwnership = deformedBodyOwns * deformationIsVisible;

    // The exact core remains available through undeformedSdf/B for texture
    // recovery and formation data, but must not be visibly unioned here:
    // min(core, aura) would fill every intentional inward trough.
    return auraRemainingSdf;
}

float deformedRemainingBodySdfFromShape(vec3 localPos, vec4 shape,
                                        vec3 shapeNormal,
                                        out float surfaceStyle,
                                        out float undeformedSdf) {
    float coreRemainingSdf = exactRemainingCoreSdf(shape);
    undeformedSdf = coreRemainingSdf;
    float auraOwnership;
    float remainingSdf = auraRemainingBodySdf(localPos, shape, shapeNormal,
            auraOwnership);
    // Carry exact ownership to the first-hit resolver. Debug coloring and the
    // depth-preserving shell tag are encoded only after the raymarch winner is
    // known.
    surfaceStyle = auraOwnership;
    return remainingSdf;
}

float sampleDeformedRemainingBodySdf(vec3 localPos, out float surfaceStyle,
                                     out float undeformedSdf) {
    vec3 shapeNormal;
    vec4 shape = sampleShapeExtendedLocalWithNormal(localPos, shapeNormal);
    return deformedRemainingBodySdfFromShape(localPos, shape, shapeNormal,
            surfaceStyle, undeformedSdf);
}

// Geometry-only companion for the staggered ray probe. Keep this definition
// identical to the full evaluator so probe and confirmed hits cannot disagree
// at either the exact core or the new aura envelope.
float sampleDeformedRemainingBodyGeometrySdf(vec3 localPos) {
    vec3 shapeNormal;
    vec4 shape = sampleShapeExtendedLocalWithNormal(localPos, shapeNormal);
    float auraOwnership;
    return auraRemainingBodySdf(localPos, shape, shapeNormal, auraOwnership);
}

float mobileShellEnergy(float mobile, vec3 localPos) {
    float densityBand = smoothstep(0.06, 0.24, mobile)
            * (1.0 - smoothstep(0.56, 0.86, mobile));
    float ripple = 0.90 + 0.10 * smoothNoise(localPos / max(VoxelSize * 2.1, 0.0001)
            + vec3(GameTime * 0.31, -GameTime * 0.19, GameTime * 0.13));
    return densityBand * ripple * (0.96 + Destabilization * 0.18);
}

float mobileTransitionEnergy(float mobile, vec3 localPos) {
    float densityBand = smoothstep(0.28, 0.48, mobile)
            * (1.0 - smoothstep(0.74, 0.94, mobile));
    float noise = 0.52 + 0.48 * smoothNoise(
            localPos / max(VoxelSize * 1.45, 0.0001)
                    + vec3(GameTime * 0.055, -GameTime * 0.035, GameTime * 0.025));
    return densityBand * noise;
}

bool intersectRaySlab(float localOrigin, float localDirection,
                      float halfExtent, inout float nearDistance,
                      inout float farDistance) {
    float safeExtent = max(halfExtent, 0.000001);
    if (abs(localDirection) <= 0.000001) {
        float boundaryTolerance = max(safeExtent * 0.00001, 0.000001);
        return abs(localOrigin) <= safeExtent + boundaryTolerance;
    }
    float inverseDirection = 1.0 / localDirection;
    float first = (-safeExtent - localOrigin) * inverseDirection;
    float second = (safeExtent - localOrigin) * inverseDirection;
    nearDistance = max(nearDistance, min(first, second));
    farDistance = min(farDistance, max(first, second));
    return farDistance >= nearDistance;
}

bool rayOrientedPrismFirstHit(vec3 rayOrigin, vec3 rayDirection,
                              vec3 segmentStart, vec3 segmentEnd,
                              vec3 frameSide, float halfWidth,
                              float startExtension, float endExtension,
                              float sceneDistance, out float hitDistance,
                              out float segmentAlong) {
    vec3 segment = segmentEnd - segmentStart;
    float segmentLength = length(segment);
    if (segmentLength <= 0.000001) {
        hitDistance = sceneDistance;
        segmentAlong = 0.0;
        return false;
    }
    vec3 prismAxis = safeNormalize(segment, SiphonP3 - SiphonP0);
    vec3 absoluteAxis = abs(prismAxis);
    vec3 fallbackReference = absoluteAxis.x <= absoluteAxis.y
            && absoluteAxis.x <= absoluteAxis.z
            ? vec3(1.0, 0.0, 0.0)
            : (absoluteAxis.y <= absoluteAxis.z
                    ? vec3(0.0, 1.0, 0.0)
                    : vec3(0.0, 0.0, 1.0));
    vec3 fallbackSide = safeNormalize(cross(fallbackReference, prismAxis),
            vec3(0.0, 1.0, 0.0));
    vec3 prismSide = frameSide
            - prismAxis * dot(frameSide, prismAxis);
    prismSide = safeNormalize(prismSide, fallbackSide);
    vec3 prismUp = safeNormalize(cross(prismAxis, prismSide),
            cross(prismAxis, fallbackSide));
    // Re-orthogonalize after the fallback so all three slab coordinates share
    // one exact frame even at a nearly degenerate curve chord.
    prismSide = safeNormalize(cross(prismUp, prismAxis), prismSide);

    float safeStartExtension = max(startExtension, 0.0);
    float safeEndExtension = max(endExtension, 0.0);
    vec3 extendedStart = segmentStart
            - prismAxis * safeStartExtension;
    vec3 extendedEnd = segmentEnd
            + prismAxis * safeEndExtension;
    vec3 prismCenter = (extendedStart + extendedEnd) * 0.5;
    vec3 prismHalfExtents = vec3(
            max((segmentLength + safeStartExtension
                    + safeEndExtension) * 0.5, 0.000001),
            max(halfWidth, 0.000001),
            max(halfWidth, 0.000001));

    vec3 originOffset = rayOrigin - prismCenter;
    vec3 localRayOrigin = vec3(
            dot(originOffset, prismAxis),
            dot(originOffset, prismSide),
            dot(originOffset, prismUp));
    vec3 localRayDirection = vec3(
            dot(rayDirection, prismAxis),
            dot(rayDirection, prismSide),
            dot(rayDirection, prismUp));
    float nearDistance = -sceneDistance;
    float farDistance = sceneDistance;
    if (!intersectRaySlab(localRayOrigin.x, localRayDirection.x,
            prismHalfExtents.x, nearDistance, farDistance)
            || !intersectRaySlab(localRayOrigin.y, localRayDirection.y,
                    prismHalfExtents.y, nearDistance, farDistance)
            || !intersectRaySlab(localRayOrigin.z, localRayDirection.z,
                    prismHalfExtents.z, nearDistance, farDistance)
            || farDistance < 0.0 || nearDistance > sceneDistance) {
        hitDistance = sceneDistance;
        segmentAlong = 0.0;
        return false;
    }

    hitDistance = nearDistance >= 0.0 ? nearDistance : farDistance;
    if (hitDistance < 0.0 || hitDistance > sceneDistance) {
        segmentAlong = 0.0;
        return false;
    }
    vec3 hitPosition = rayOrigin + rayDirection * hitDistance;
    segmentAlong = segmentLength > 0.000001
            ? saturate(dot(hitPosition - segmentStart, prismAxis)
                    / segmentLength)
            : 0.5;
    return true;
}

void main() {
    vec3 eyeView = projectionEyeView();
    vec3 rayOriginWorld = CameraPos
            + (CameraToWorldMat * vec4(eyeView, 0.0)).xyz;
    // Form the direction before adding the large world-space camera position;
    // this retains precision far from the world origin.
    vec3 rayDirectionView = viewFromDepth(texCoord0, 1.0) - eyeView;
    vec3 rayDirectionWorld = safeNormalize(
            (CameraToWorldMat * vec4(rayDirectionView, 0.0)).xyz,
            vec3(0.0, 0.0, 1.0));
    vec3 rayOriginLocal = worldToLocal(rayOriginWorld);
    vec3 rayDirectionLocal = safeNormalize(worldDirectionToLocal(rayDirectionWorld),
            vec3(1.0, 0.0, 0.0));

    float sceneDepth = texture(SceneDepthSampler, texCoord0).r;
    float sceneDistance = 100000.0;
    if (sceneDepth < 0.999999) {
        vec3 sceneWorld = worldFromDepth(texCoord0, sceneDepth);
        sceneDistance = max(0.0,
                dot(sceneWorld - rayOriginWorld, rayDirectionWorld));
    }

    float bodyEntry;
    float bodyExit;
    float baseMargin = max(VoxelSize * 2.0, BodyRadius * 0.090);
    float auraStrength = clamp(TurbulenceBlend, 0.0, 1.0);
    float auraMargin = BodyRadius * 0.36 * auraStrength;
    float bodyMaxX = BodyMaxX + baseMargin + auraMargin;
    bool hitsBody = intersectBox(rayOriginLocal, rayDirectionLocal,
            vec3(-baseMargin - auraMargin,
                    -VolumeSize.y - auraMargin,
                    -VolumeSize.z - auraMargin),
            vec3(bodyMaxX, VolumeSize.y + auraMargin,
                    VolumeSize.z + auraMargin), bodyEntry, bodyExit);
    bodyExit = min(bodyExit, sceneDistance);

    float siphonOptical = 0.0;
    float bodyCoveragePeak = 0.0;
    float undeformedBodyCoveragePeak = 0.0;
    float siphonCoverageOptical = 0.0;
    float resolvedSiphonHitDepth = -1.0;
    float resolvedSiphonRadius = 0.0;
    float bodySurfaceStyle = -1.0;
    // Persist enough raymarch evidence to distinguish an intentional signed
    // contour carve from an unresolved R sample. B encodes that distinction at
    // output without consuming A, which remains dedicated to surface depth.
    bool resolvedDeformedBodyHit = false;
    float resolvedDeformedBodyProximity = 0.0;
    float closestDeformedBodyField = 100000.0;
    float intentionalCarveFieldThreshold = 100000.0;
    // In normal rendering mode A carries first-hit depth and ownership. Core
    // depth remains positive. Aura depth is encoded below -(bias + depth), while
    // -1 remains the invalid conservative-fill/collar sentinel.
    float resolvedBodyHitDepth = -1.0;
    if (hitsBody && bodyExit > bodyEntry) {
        int sampleCount = clamp(BodyRaymarchSamples, 1, MAX_BODY_STEPS);
        float segmentLength = bodyExit - bodyEntry;
        float stepLength = segmentLength / float(sampleCount);
        // Keep the body silhouette spatially stable. Motion belongs inside the
        // siphon, not in the screen-space placement of the body samples.
        vec3 handoffTangent = siphonPathFrameTangent(0.0);
        float collarRadius = max(siphonRadius(0.0) * 1.10,
                VoxelSize * 1.65);
        float collarHalfLength = max(BodyRadius * 0.14,
                VoxelSize * 2.25);
        float previousT = bodyEntry;
        float previousStyle;
        float previousUndeformedField;
        float previousField = sampleDeformedRemainingBodySdf(
                rayOriginLocal + rayDirectionLocal * previousT, previousStyle,
                previousUndeformedField);
        float closestBodyField = previousField;
        float closestUndeformedField = previousUndeformedField;
        bool bodyHit = false;
        float collarCoveragePeak = 0.0;
        // Conversion is a coherent surface reveal. Per-sample noise here used
        // to perforate the first-hit projection into flickering islands before
        // the body became solid.
        float formationCoverage = smoothstep(0.06, 0.62, Formation);
        for (int i = 0; i < MAX_BODY_STEPS; i++) {
            if (i >= sampleCount) {
                break;
            }
            // Find the first crossing of the combined body/release surface.
            // Fixed midpoints preserve temporal stability; bisection keeps the
            // resolved surface attached to the moving front between voxels.
            float rayT = bodyEntry + (float(i) + 0.5) * stepLength;
            vec3 localPos = rayOriginLocal + rayDirectionLocal * rayT;
            vec3 shapeNormal;
            vec4 shape = sampleShapeExtendedLocalWithNormal(localPos,
                    shapeNormal);
            float currentStyle;
            float undeformedField;
            float currentField = deformedRemainingBodySdfFromShape(localPos,
                    shape, shapeNormal, currentStyle, undeformedField);
            closestBodyField = min(closestBodyField, currentField);
            closestUndeformedField = min(closestUndeformedField,
                    undeformedField);
            // Midpoints plus the staggered boundary probes below are separated
            // by half a step. A sub-half-step conservative tolerance prevents
            // thin, oblique displaced sections from falling between both streams.
            float hitTolerance = max(VoxelSize * 0.16, stepLength * 0.20);
            if (!bodyHit && currentField <= hitTolerance) {
                float hitStyle = currentStyle;
                float hitT = rayT;
                if (previousField > hitTolerance) {
                    float lowerT = previousT;
                    float upperT = rayT;
                    for (int refinement = 0; refinement < 5; refinement++) {
                        float middleT = (lowerT + upperT) * 0.5;
                        float middleStyle;
                        float middleUndeformedField;
                        float middleField = sampleDeformedRemainingBodySdf(
                                rayOriginLocal + rayDirectionLocal * middleT,
                                middleStyle, middleUndeformedField);
                        if (middleField > hitTolerance) {
                            lowerT = middleT;
                        } else {
                            upperT = middleT;
                            hitStyle = middleStyle;
                        }
                    }
                    hitT = upperT;
                }
                bodyHit = true;
                bodySurfaceStyle = hitStyle;
                resolvedBodyHitDepth = hitT;
                bodyCoveragePeak = formationCoverage;
            }
            if (undeformedField <= hitTolerance) {
                undeformedBodyCoveragePeak = max(undeformedBodyCoveragePeak,
                        formationCoverage);
            }
            vec3 handoffOffset = localPos - SiphonP0;
            float handoffAxial = dot(handoffOffset, handoffTangent);
            float handoffRadial = length(handoffOffset
                    - handoffTangent * handoffAxial);
            float radialGate = 1.0 - smoothstep(collarRadius * 0.65,
                    collarRadius * 1.35, handoffRadial);
            float axialGate = 1.0 - smoothstep(VoxelSize,
                    collarHalfLength, abs(handoffAxial));
            float releasedGate = (1.0 - releaseRemaining(shape.b))
                    * step(0.5, shape.a)
                    * smoothstep(0.002, 0.035, SiphonProgress);
            float collarMobile = sampleBodyMobileLocal(localPos);
            float collarGate = radialGate * axialGate * releasedGate;
            float collarMaterial = silhouetteMaterialResponse(collarMobile);
            collarCoveragePeak = max(collarCoveragePeak,
                    collarMaterial * collarGate * formationCoverage);

            // Interleave the exact geometry at interval boundaries. This
            // halves the maximum axial gap while preserving the common full
            // evaluator path for style and refined first-hit depth.
            bool resolvedProbe = false;
            float probeField = currentField;
            float probeT = min(rayT + stepLength * 0.5, bodyExit);
            if (!bodyHit && probeT > rayT + 0.000001) {
                vec3 probePos = rayOriginLocal + rayDirectionLocal * probeT;
                float probeGeometry = sampleDeformedRemainingBodyGeometrySdf(
                        probePos);
                closestBodyField = min(closestBodyField, probeGeometry);
                if (probeGeometry <= hitTolerance) {
                    float probeStyle;
                    float probeUndeformedField;
                    probeField = sampleDeformedRemainingBodySdf(probePos,
                            probeStyle, probeUndeformedField);
                    resolvedProbe = true;
                    closestBodyField = min(closestBodyField, probeField);
                    closestUndeformedField = min(closestUndeformedField,
                            probeUndeformedField);
                    if (probeUndeformedField <= hitTolerance) {
                        undeformedBodyCoveragePeak = max(
                                undeformedBodyCoveragePeak, formationCoverage);
                    }
                    if (probeField <= hitTolerance) {
                        float hitStyle = probeStyle;
                        float hitT = probeT;
                        if (currentField > hitTolerance) {
                            float lowerT = rayT;
                            float upperT = probeT;
                            for (int refinement = 0; refinement < 5;
                                    refinement++) {
                                float middleT = (lowerT + upperT) * 0.5;
                                float middleStyle;
                                float middleUndeformedField;
                                float middleField = sampleDeformedRemainingBodySdf(
                                        rayOriginLocal
                                                + rayDirectionLocal * middleT,
                                        middleStyle, middleUndeformedField);
                                if (middleField > hitTolerance) {
                                    lowerT = middleT;
                                } else {
                                    upperT = middleT;
                                    hitStyle = middleStyle;
                                }
                            }
                            hitT = upperT;
                        }
                        bodyHit = true;
                        bodySurfaceStyle = hitStyle;
                        resolvedBodyHitDepth = hitT;
                        bodyCoveragePeak = formationCoverage;
                    }
                }
            }
            previousT = resolvedProbe ? probeT : rayT;
            previousField = resolvedProbe ? probeField : currentField;
        }
        // Preserve fractional proximity around the implicit surface instead of
        // collapsing every ray to a binary hit. This makes subpixel temporal
        // SDF motion survive the raymarch and lets the downstream contour move
        // smoothly rather than snapping between pixels.
        float hitTolerance = max(VoxelSize * 0.16, stepLength * 0.20);
        float coverageFeather = max(VoxelSize * 0.12, stepLength * 0.06);
        float bodyProximity = 1.0 - smoothstep(hitTolerance * 0.35,
                hitTolerance + coverageFeather, closestBodyField);
        float undeformedProximity = 1.0 - smoothstep(hitTolerance * 0.35,
                hitTolerance + coverageFeather, closestUndeformedField);
        resolvedDeformedBodyHit = bodyHit;
        resolvedDeformedBodyProximity = bodyProximity;
        closestDeformedBodyField = closestBodyField;
        intentionalCarveFieldThreshold = hitTolerance
                + coverageFeather * 1.50;
        bodyCoveragePeak = max(bodyCoveragePeak,
                bodyProximity * formationCoverage);
        undeformedBodyCoveragePeak = max(undeformedBodyCoveragePeak,
                undeformedProximity * formationCoverage);
        bodyCoveragePeak = max(bodyCoveragePeak, collarCoveragePeak);
    }

    // Resolve sixteen node-aware chords of the Hermite centerline independently.
    // The former global interval spent only 24 samples across the complete folded
    // path, so a narrow bolt could fall entirely between samples whenever
    // distant segments widened that interval. Analytical oriented-prism hits
    // make the smooth tessellated centerline the crisp geometry authority; the
    // advected atlas remains only the transported material/intensity gate.
    vec3 prismRootCenter;
    vec3 prismRootTangent;
    vec3 prismRootSide;
    vec3 prismRootUp;
    curveFrame(0.0, prismRootCenter, prismRootTangent,
            prismRootSide, prismRootUp);
    bool hasPreviousPrism = false;
    vec3 previousPrismAxis = prismRootTangent;
    vec3 previousPrismSide = prismRootSide;
    vec3 previousPrismUp = prismRootUp;
    float previousPrismLength = 0.0;
    float previousPrismHalfWidth = siphonRadius(0.0);
    vec3 prismStart = siphonPathPoint(0.0);
    for (int prismIndex = 0;
            prismIndex < SIPHON_PRESENTATION_PRISM_COUNT; prismIndex++) {
        float prismStartT = siphonPresentationCurveT(prismIndex);
        float prismEndT = siphonPresentationCurveT(prismIndex + 1);
        vec3 prismEnd = siphonPathPoint(prismEndT);
        float prismHalfWidth = max(siphonRadius(prismStartT),
                siphonRadius(prismEndT));
        // Boundaries keep fixed interval ownership, but their live curve
        // positions let every prism shorten or lengthen continuously as the
        // electrical path shifts.
        float prismLength = length(prismEnd - prismStart);
        vec3 prismAxis = safeNormalize(prismEnd - prismStart,
                previousPrismAxis);
        vec3 prismSide = rotationMinimizingPrismSide(
                prismAxis, previousPrismSide, previousPrismUp);
        vec3 prismUp = safeNormalize(cross(prismAxis, prismSide),
                previousPrismUp);

        // The current prism alone owns this joint's backward extension.
        // A conservative square miter closes the outside elbow, while the
        // adjacent-chord cap prevents a short collar chord from reaching
        // behind P0 or far beyond the path neighborhood.
        float startExtension = 0.0;
        if (hasPreviousPrism && prismLength > 0.000001) {
            float turnCosine = clamp(dot(previousPrismAxis, prismAxis),
                    -0.995, 1.0);
            float turnTangent = sqrt(max(
                    (1.0 - turnCosine) / (1.0 + turnCosine), 0.0));
            float seamPad = VoxelSize * 0.12;
            float requestedMiter = seamPad
                    + 1.41421356
                            * max(previousPrismHalfWidth, prismHalfWidth)
                            * turnTangent;
            float overlapCap = 0.45
                    * min(previousPrismLength, prismLength);
            startExtension = min(requestedMiter,
                    max(overlapCap, 0.0));
        }
        float endExtension = 0.0;

        if (prismLength > 0.000001) {
            hasPreviousPrism = true;
            previousPrismAxis = prismAxis;
            previousPrismSide = prismSide;
            previousPrismUp = prismUp;
            previousPrismLength = prismLength;
            previousPrismHalfWidth = prismHalfWidth;
        }
        float hitDistance;
        float hitAlong;
        if (!rayOrientedPrismFirstHit(
                rayOriginLocal, rayDirectionLocal,
                prismStart, prismEnd, prismSide, prismHalfWidth,
                startExtension, endExtension, sceneDistance,
                hitDistance, hitAlong)) {
            prismStart = prismEnd;
            continue;
        }

        float curveT = mix(prismStartT, prismEndT, hitAlong);
        vec3 localPos = rayOriginLocal
                + rayDirectionLocal * hitDistance;
        float mobile = sampleSiphonMaterialDensityAt(localPos, curveT);
        float visibleMobile = silhouetteMaterialResponse(mobile);
        float sourceBodyDistance = sampleShapeLocal(localPos).r;
        float outsideSourceBody = smoothstep(-VoxelSize * 0.20,
                VoxelSize * 0.85, sourceBodyDistance);
        float surfaceResponse = visibleMobile * outsideSourceBody;

        siphonOptical = max(siphonOptical, surfaceResponse);
        siphonCoverageOptical = max(siphonCoverageOptical,
                surfaceResponse);
        if (surfaceResponse >= SIPHON_SURFACE_THRESHOLD
                && (resolvedSiphonHitDepth < 0.0
                        || hitDistance < resolvedSiphonHitDepth)) {
            resolvedSiphonHitDepth = hitDistance;
            resolvedSiphonRadius = siphonRadius(curveT);
        }
        prismStart = prismEnd;
    }

    float siphonCoreAlpha = 1.0 - exp(-siphonOptical * 3.20);
    float siphonCoverageAlpha = 1.0 - exp(-siphonCoverageOptical * 1.75);
    float bodyCoverage = smoothstep(0.22, 0.55, bodyCoveragePeak)
            * EffectFade;
    float undeformedBodyCoverage = smoothstep(0.22, 0.55,
            undeformedBodyCoveragePeak) * EffectFade;
    float siphonCoverage = smoothstep(0.025, 0.34,
            max(siphonCoverageAlpha, siphonCoreAlpha)) * EffectFade;
    // Signed-B storage contract:
    //   B > 0: ordinary undeformed reference, eligible for tightly bounded
    //          downstream recovery when R was unresolved near its surface.
    //   B < 0: the undeformed projection exists, but the signed deformed field
    //          is safely outside with no hit or proximity; this is an
    //          intentional inward contour carve and must never be filled.
    // abs(B) remains storage coverage so this signal cannot prematurely discard
    // the pixel, and A remains wholly available for body/siphon surface depth.
    bool intentionalContourCarve = undeformedBodyCoverage > 0.002
            && !resolvedDeformedBodyHit
            && resolvedDeformedBodyProximity < 0.002
            && closestDeformedBodyField > intentionalCarveFieldThreshold;
    float signedUndeformedBodyCoverage = intentionalContourCarve
            ? -undeformedBodyCoverage : undeformedBodyCoverage;
    float materialCoverage = max(max(bodyCoverage,
            abs(signedUndeformedBodyCoverage)), siphonCoverage);
    if (materialCoverage < 0.002) {
        discard;
    }

    float bodyMaterial = bodyCoverage;
    float siphonMaterial = siphonCoverage;
    // B normally carries signed undeformed-body storage in [-1, 1]. On a
    // genuinely siphon-only first hit that storage is unused, so values above
    // 2 encode the local world-space tube radius for proportional compositing.
    float storagePayload = signedUndeformedBodyCoverage;
    if (bodyMaterial < 0.002
            && abs(signedUndeformedBodyCoverage) < 0.002
            && resolvedSiphonRadius > 0.0) {
        storagePayload = 2.0 + resolvedSiphonRadius;
    }
    float surfacePayload = resolvedBodyHitDepth;
    if (DeformationDebugMode == 12) {
        surfacePayload = resolvedBodyHitDepth > 0.0
                ? mix(FIELD_DEBUG_CORE, FIELD_DEBUG_AURA,
                        step(0.5, bodySurfaceStyle))
                : FIELD_DEBUG_UNRESOLVED;
    } else if (DeformationDebugMode == 0
            && resolvedBodyHitDepth > 0.0
            && bodySurfaceStyle > 0.5) {
        surfacePayload = -(AURA_DEPTH_TAG_BIAS + resolvedBodyHitDepth);
    } else if (DeformationDebugMode == 0
            && resolvedBodyHitDepth <= 0.0
            && bodyMaterial < 0.002
            && resolvedSiphonHitDepth > 0.0) {
        // A is shared by the currently visible first surface. Body ownership
        // always wins at the buried inlet so its negative aura tag cannot be
        // replaced by the siphon. B is deliberately ignored here: it is broad
        // remaining-mass storage, not visible surface ownership, and the edge
        // resolve will either texture-mask it into the exact core or preserve
        // this siphon depth on a genuinely siphon-only pixel.
        surfacePayload = resolvedSiphonHitDepth;
    }
    // R = deformed body, G = siphon, B = signed undeformed body projection or
    // a >2 siphon-only local-radius payload,
    // A = positive core-or-siphon depth, depth-preserving negative aura tag,
    // or the selected diagnostic in debug mode. B is internal storage only;
    // its sign carries the intentional-carve contract documented above.
    fragColor = vec4(bodyMaterial, siphonMaterial,
            storagePayload, surfacePayload);
}
