#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 VolumeRootCameraRelative;
uniform vec3 VolumeAxis;
uniform vec3 VolumeSide;
uniform vec3 VolumeUp;
uniform vec3 InletLocal;
uniform vec3 SpikeTransportOrigin;
uniform vec3 SiphonTangentLocal;
uniform float BodyRadius;
uniform float VoxelSize;
uniform float ThicknessScale;
uniform float SplatRadius;
uniform float ReleaseFront;
uniform float CollapseLocality;
uniform float TurbulenceBlend;
uniform float TurbulenceComplexityBlend;
uniform float SpikeFlowTime;
uniform float DeformationFlowTime;
uniform float EffectFade;
uniform float SplatDiagnosticMode;
uniform float BoneRouteEnabled;
uniform float BoneRoutePathScale;
uniform float TransportActivationHalfWidth;
uniform float TransportFrontSpan;
uniform float TransportMinimumCrossSection;
uniform float TransportThroatCoordinate;
uniform float TransportTerminalCoordinate;
uniform float TransportRetirementWidth;
uniform float TransportCollarExitCoordinate;
uniform float TransportCollarExitWidth;
uniform mat4 BoneRouteNode0;
uniform mat4 BoneRouteNode1;
uniform mat4 BoneRouteNode2;
uniform mat4 BoneRouteNode3;
uniform mat4 BoneRouteNode4;
uniform mat4 BoneRouteNode5;
uniform mat4 BoneRouteNode6;
uniform mat4 BoneRouteNode7;

out vec2 splatCoordinate;
out float bodyCoverage;
out float undeformedCoverage;
out vec3 cameraRelativePosition;
out float deformedSurface;
out float spikeTipStrength;
out float accumulationWeightScale;
out float hookStrength;
out float hookReach;
out float hookBend;
flat out int splatIdentity;

const float TAU = 6.28318530718;
// The separable resolve retains only one quarter of an isolated one-pixel
// carrier after its horizontal and vertical passes. A confidently classified
// thin sheet therefore needs four units of encoded support to reach the same
// fusion threshold as a normally overlapped surface. This is deliberately
// derived from thin-sheet metadata rather than applied globally: ordinary
// isolated splats must still fail the anti-marble fusion guard.
const float THIN_SHEET_FUSION_SUPPORT = 4.0;
const float SPIKE_SPATIAL_FREQUENCY_SCALE = 2.25;
const float INDENT_SPATIAL_FREQUENCY_SCALE = 1.80;
const float BODY_SPIKE_FLOW_SPAN =
        8.0 * SPIKE_SPATIAL_FREQUENCY_SCALE;
const float SPIKE_FLOW_PHASE_SPEED =
        3.0 * SPIKE_SPATIAL_FREQUENCY_SCALE;
// Keep the fused carrier close to the captured zero-isosurface. Large-scale
// motion comes from attached spikes, indents, and crescents rather than a
// globally inflated copy of the Pokemon.
const float BODY_AURA_INITIAL_EXPANSION = 0.003;
const float BODY_AURA_FINAL_EXPANSION = 0.012;
const float BODY_SPIKE_INITIAL_DISPLACEMENT = 0.070;
const float BODY_SPIKE_MAX_DISPLACEMENT = 0.245;
const float BODY_INDENT_INITIAL_DISPLACEMENT = 0.012;
const float BODY_INDENT_MAX_DISPLACEMENT = 0.140;
const float BODY_COLLAPSE_RETAINED_SPAN = 0.08;
const float MIN_COLLAPSE_LOCALITY = 0.50;
const float ORDERED_POSITIONAL_FRONT_END = 0.92;
const float TERMINAL_TRANSVERSE_END = 1.10;
const float TERMINAL_AXIAL_START = 1.06;
const float TERMINAL_THROAT_END = 1.24;
const float TERMINAL_TRANSVERSE_SCALE = 0.30;
const float TERMINAL_AXIAL_SCALE = 0.55;
const float TERMINAL_ORDER_LEAD = 0.06;
const float BODY_ATTACHMENT_EMPTY_REMAINING = 0.58;
const float BODY_ATTACHMENT_FULL_REMAINING = 0.92;
const float BODY_INDENT_THICKNESS_RESERVE_VOXELS = 0.55;
const float BODY_INDENT_THICKNESS_FRACTION = 0.55;
const float BODY_INDENT_RADIUS_CAP = 0.14;
// The uploaded carrier is already inset by at most 15% of its local
// thickness. Bound every additional inward term together (indent plus the
// negative modal pulse), leaving at least 60% of the measured chord intact.
const float BODY_UPLOAD_INSET_THICKNESS_FRACTION = 0.15;
const float BODY_MAX_TOTAL_INWARD_THICKNESS_FRACTION = 0.40;
const float BODY_DYNAMIC_INWARD_RESERVE_VOXELS = 0.50;
const float BRANCH_ROUTE_OFFSET_FLOOR = 0.48;
const float BRANCH_ROUTE_MIN_ADVANCE = 0.01;
const float BRANCH_ROUTE_FULL_ADVANCE = 0.22;
const float TRANSPORT_DEFORMATION_FADE_START = 0.82;
const float TRANSPORT_DEFORMATION_FADE_END = 0.985;
const float MAX_SPLAT_TANGENT_STRETCH = 2.00;
const float MAX_BODY_SPLAT_TANGENT_STRETCH = 1.62;
const float MAX_NARROW_SPLAT_TANGENT_STRETCH = 1.42;
const float MAX_BODY_SPLAT_ANISOTROPY = 4.80;
const float MAX_HANDOFF_SPLAT_ANISOTROPY = 7.00;
const float MAX_PRINCIPAL_TANGENT_AUTHORITY = 0.60;
const float MIN_SPLAT_NORMAL_STRETCH = 0.28;
const float HIGH_SPIKE_TANGENT_MULTIPLIER = 0.55;
const float HIGH_SPIKE_NORMAL_MULTIPLIER = 0.78;
const float MAX_SPIKE_BRIDGE_RADII = 1.75;
const float MIN_COVARIANCE_STRETCH = 0.72;
const float MIN_COVARIANCE_WEIGHT = 0.65;
const float COVARIANCE_BLEND_START = 0.02;
const float COVARIANCE_BLEND_END = 0.30;
const float SINK_HANDOFF_LEAD = 0.14;
const float SINK_HANDOFF_END_OFFSET = 0.015;
const float SINK_LONGITUDINAL_STRETCH = 1.20;
const float SINK_MINIMUM_CROSS_SECTION = 0.30;
const float FINAL_SAFETY_CUTOFF_START = 1.285;
const float FINAL_SAFETY_CUTOFF_END = 1.300;
const float DIRECT_CURL_START_PROGRESS = 0.54;
const float DIRECT_CURL_MAX_ANGLE = 0.40;
const float DIRECT_BEND_MAX_ANGLE = 0.22;
const float ROUTE_CORRIDOR_CLAMP_START_PROGRESS = 0.08;
const float ROUTE_CORRIDOR_CLAMP_FULL_PROGRESS = 0.30;
const float ROUTE_CORRIDOR_TAPER_START_PROGRESS = 0.12;
const float ROUTE_CORRIDOR_TAPER_END_PROGRESS = 0.94;
const float ROUTE_CORRIDOR_START_SPLAT_RADII = 1.65;
const float ROUTE_CORRIDOR_END_SPLAT_RADII = 0.38;
const float ROUTE_CORRIDOR_MIN_VOXELS = 0.55;
const float KELVINLET_PINCH_START_PROGRESS = 0.18;
const float KELVINLET_PINCH_FULL_PROGRESS = 0.88;
const float KELVINLET_MAX_PINCH = 0.18;
const float SPLAT_HANDOFF_START_PROGRESS = 0.62;
const float SPLAT_HANDOFF_FULL_PROGRESS = 0.94;
const float SPLAT_HANDOFF_LONGITUDINAL_STRETCH = 2.00;
const float SPLAT_HANDOFF_TRANSVERSE_SCALE = 0.48;
const float MODAL_TWIST_MAX_ANGLE = 0.16;
const float MODAL_PULSE_RADIUS_SCALE = 0.024;
const float MIN_NEIGHBOR_SPACING_SCALE = 0.72;
const float MAX_NEIGHBOR_SPACING_SCALE = 1.35;
const float MIN_NEIGHBOR_AREA_SCALE = 0.5184;
const float MAX_NEIGHBOR_AREA_SCALE = 1.8225;
const float MIN_TRANSVERSE_NEIGHBOR_SCALE = 0.72;
const float MAX_TRANSVERSE_NEIGHBOR_SCALE = 1.22;
const float FEATURE_TRANSVERSE_THICKNESS_FRACTION = 0.48;
const float FEATURE_TRANSVERSE_VOXEL_FLOOR = 0.38;
const float COLLAPSE_FOOTPRINT_SCALE_FLOOR = 0.18;
const float DEFORMED_OWNERSHIP_MIN_DISPLACEMENT_VOXELS = 0.30;
const float BOUNDARY_HOOK_MAX_REACH = 1.90;
const float BOUNDARY_HOOK_MAX_BEND = 1.55;
const float THIN_SHEET_NORMAL_THICKNESS_FRACTION = 0.32;
const float THIN_SHEET_NORMAL_VOXEL_FLOOR = 0.12;
const float THIN_SHEET_TANGENTIAL_REDIRECT = 0.58;
const float THIN_SHEET_LOW_CONFIDENCE_STRETCH = 1.72;
const float THIN_SHEET_LOW_CONFIDENCE_ANISOTROPY = 5.40;
const float THIN_SHEET_LONGITUDINAL_COVERAGE_FLOOR = 1.18;
const float THIN_SHEET_TRANSVERSE_VOXEL_FLOOR = 0.72;
// Physical sheet thickness is measured across the two opposing SDF faces; it
// is not the in-plane spacing between carriers. Clamping a billboard's screen
// transverse radius to that thickness turns fins and ribbons into a lattice of
// needle-thin splats. Preserve a bounded fraction of the ordinary sampling
// footprint for confirmed sheets while keeping the exact projected mask as
// the silhouette authority.
const float THIN_SHEET_TRANSVERSE_RADIUS_FRACTION = 0.82;
// A single carrier is never allowed to span most of the framebuffer. This is
// a final whole-splat containment fence for malformed Iris state; all six
// triangle-list vertices execute the same four-corner test and retire together.
const float MAX_SAFE_FOOTPRINT_NDC = 0.35;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float decodePackedRange(int packedValue,
                        float minimum,
                        float maximum) {
    float signedUnit = clamp(
            float(packedValue) / 32767.0, -1.0, 1.0);
    return mix(minimum, maximum, signedUnit * 0.5 + 0.5);
}

float decodePackedUnit(int packedValue) {
    return clamp(float(packedValue) / 32767.0, 0.0, 1.0);
}

void decodeTangentFrameMetadata(int packedValue,
                                out float angle,
                                out float confidence) {
    int packedBits = packedValue & 65535;
    int encodedAngle = packedBits & 4095;
    int encodedConfidence = (packedBits >> 12) & 15;
    angle = float(encodedAngle) / 4095.0 * (TAU * 0.5);
    confidence = float(encodedConfidence) / 15.0;
}

float decodeThinSheetScore() {
    int packedMetadata =
            int(floor(Color.b * 255.0 + 0.5));
    return float((packedMetadata >> 5) & 7) / 7.0;
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
    float n000 = hash31(cell);
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

vec3 safeNormalize(vec3 value, vec3 fallback) {
    float lengthSquared = dot(value, value);
    return lengthSquared > 0.0000001
            ? value * inversesqrt(lengthSquared) : fallback;
}

vec2 safeNormalize2(vec2 value, vec2 fallback) {
    float lengthSquared = dot(value, value);
    return lengthSquared > 0.0000001
            ? value * inversesqrt(lengthSquared) : fallback;
}

float smoothlyCapPositive(float value, float limit) {
    if (value <= 0.0 || limit <= 0.000001) {
        return 0.0;
    }
    return limit * (1.0 - exp(-value / limit));
}

vec3 spikeTransportCoordinates(vec3 surfaceAnchor,
                               float transportOrder,
                               float flowTime) {
    vec3 inletToAnchor = surfaceAnchor - SpikeTransportOrigin;
    float inletDistance = length(inletToAnchor);
    vec3 inletFallback = safeNormalize(
            InletLocal - SpikeTransportOrigin, vec3(1.0, 0.0, 0.0));
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
                * (7.0 * SPIKE_SPATIAL_FREQUENCY_SCALE),
        dot(streamIdentity, transverseB)
                * (7.0 * SPIKE_SPATIAL_FREQUENCY_SCALE),
        transportOrder * BODY_SPIKE_FLOW_SPAN
                - flowTime * SPIKE_FLOW_PHASE_SPEED
    );
}

float spikeField(vec3 coordinates) {
    // A broad carrier moves neighboring surfels together while the retained
    // detail carrier keeps the violent edge language. Isolated high-frequency
    // peaks read as individual balls after fusion; coherent peaks read as one
    // attached deformation of the silhouette.
    float broadPeak = smoothNoise(
            coordinates * 0.58 + vec3(-1.93, 5.47, 8.11));
    float detailPeak = smoothNoise(
            coordinates + vec3(6.31, -3.77, 2.19));
    float peakSignal = mix(broadPeak, detailPeak, 0.21) * 0.84;
    float profileFloor = mix(
            0.58, 0.36, saturate(TurbulenceComplexityBlend));
    float profile = saturate(
            (peakSignal - profileFloor) / (0.84 - profileFloor));
    float sineEaseIn = 1.0 - cos(profile * TAU * 0.25);
    float quinticEaseIn =
            profile * profile * profile * profile * profile;
    return mix(sineEaseIn, quinticEaseIn, 0.21);
}

float indentField(vec3 coordinates, float outwardSpike) {
    vec3 broadCoordinates = coordinates
            * (INDENT_SPATIAL_FREQUENCY_SCALE
                    / SPIKE_SPATIAL_FREQUENCY_SCALE);
    float broadCarrier = smoothNoise(
            broadCoordinates * vec3(0.52, 0.52, 0.64)
                    + vec3(-11.73, 8.29, 4.61));
    float broadCompanion = smoothNoise(
            broadCoordinates * vec3(0.31, 0.31, 0.42)
                    + vec3(3.17, -14.09, 9.43));
    float troughSignal = mix(
            1.0 - broadCarrier, broadCompanion, 0.24);
    float complexity = saturate(TurbulenceComplexityBlend);
    float profile = smoothstep(
            mix(0.70, 0.46, complexity),
            mix(0.88, 0.76, complexity),
            troughSignal);
    profile = profile * profile * (3.0 - 2.0 * profile);
    return profile
            * (1.0 - smoothstep(0.12, 0.38, outwardSpike));
}

float releaseRemaining(float releaseOrder) {
    if (ReleaseFront < 0.0) {
        return 1.0;
    }
    float front = clamp(ReleaseFront, 0.0, 1.0);
    return smoothstep(front - 0.13, front + 0.13, releaseOrder);
}

float transportActivation(float sectionCoordinate) {
    float halfWidth = max(
            TransportActivationHalfWidth, 0.0001);
    return smoothstep(
            sectionCoordinate - halfWidth,
            sectionCoordinate + halfWidth,
            ReleaseFront);
}

float transportTravel(float sectionCoordinate) {
    float travelStart = sectionCoordinate + max(
            TransportActivationHalfWidth, 0.0001);
    return transportActivation(sectionCoordinate) * smoothstep(
            travelStart,
            travelStart + max(TransportFrontSpan, 0.0001),
            ReleaseFront);
}

float transportFrontTarget(float sectionCoordinate) {
    float section = saturate(sectionCoordinate);
    float terminal = max(
            TransportTerminalCoordinate,
            TransportThroatCoordinate + 0.0001);
    return clamp(
            max(ReleaseFront, section),
            section,
            terminal);
}

float transportedCoordinate(float sectionCoordinate,
                            float travelProgress) {
    float section = saturate(sectionCoordinate);
    float frontTarget = transportFrontTarget(section);
    return mix(
            section,
            frontTarget,
            saturate(travelProgress));
}

float transportPathProgress(float sectionCoordinate,
                            float transportedSection) {
    float section = saturate(sectionCoordinate);
    float terminal = max(
            TransportTerminalCoordinate,
            TransportThroatCoordinate + 0.0001);
    return saturate(
            (transportedSection - section)
                    / max(terminal - section, 0.0001));
}

float directTransportRemaining(float pathProgress) {
    return 1.0 - saturate(pathProgress);
}

float nearInletCurlAngle(float pathProgress,
                         float signedCurlStrength) {
    float windowProgress = smoothstep(
            DIRECT_CURL_START_PROGRESS,
            1.0,
            saturate(pathProgress));
    float endpointZeroEnvelope =
            4.0 * windowProgress * (1.0 - windowProgress);
    return clamp(signedCurlStrength, -1.0, 1.0)
            * DIRECT_CURL_MAX_ANGLE
            * endpointZeroEnvelope;
}

vec3 rotateAroundAxis(vec3 value,
                      vec3 axis,
                      float angle) {
    vec3 unitAxis = safeNormalize(axis, vec3(1.0, 0.0, 0.0));
    float cosine = cos(angle);
    float sine = sin(angle);
    return value * cosine
            + cross(unitAxis, value) * sine
            + unitAxis * dot(unitAxis, value) * (1.0 - cosine);
}

vec3 analyticalVortex(vec3 position,
                      vec3 center,
                      vec3 axis,
                      float radius,
                      float spin) {
    vec3 relative = position - center;
    float normalizedRadiusSquared =
            dot(relative, relative)
                    / max(radius * radius, 0.0001);
    float influence = 1.0 - smoothstep(
            0.18, 1.0, normalizedRadiusSquared);
    return cross(
            safeNormalize(axis, vec3(0.0, 1.0, 0.0)),
            relative) * (spin * influence);
}

vec3 analyticalBoundaryCurl(vec3 normalizedPosition,
                            float flowTime) {
    float firstPulse = 0.78
            + 0.22 * sin(flowTime * 1.31 + 0.47);
    float secondPulse = 0.76
            + 0.24 * sin(flowTime * 1.57 + 2.13);
    float thirdPulse = 0.80
            + 0.20 * sin(flowTime * 1.79 + 4.01);
    vec3 flow = analyticalVortex(
            normalizedPosition,
            vec3(-0.36, 0.24, 0.12),
            vec3(0.31, 0.87, 0.38),
            1.18,
            firstPulse);
    flow += analyticalVortex(
            normalizedPosition,
            vec3(0.38, -0.18, 0.26),
            vec3(-0.72, 0.22, 0.66),
            1.10,
            -secondPulse);
    flow += analyticalVortex(
            normalizedPosition,
            vec3(0.04, 0.34, -0.42),
            vec3(0.58, 0.71, -0.40),
            1.24,
            thirdPulse);
    return flow;
}

float transportCrossSectionScale(float pathProgress) {
    return mix(
            1.0,
            clamp(TransportMinimumCrossSection, 0.0001, 1.0),
            saturate(pathProgress));
}

float transportBodyOwnership(float pathProgress) {
    float retirementWidth = clamp(
            TransportRetirementWidth, 0.0001, 0.25);
    return 1.0 - smoothstep(
            1.0 - retirementWidth,
            1.0,
            saturate(pathProgress));
}

float finalSafetyCoverage() {
    return 1.0 - smoothstep(
            FINAL_SAFETY_CUTOFF_START,
            FINAL_SAFETY_CUTOFF_END,
            ReleaseFront);
}

float compressionScale(float releaseOrder) {
    if (ReleaseFront <= 0.0001) {
        return 1.0;
    }
    float targetOrder = clamp(
            ReleaseFront, 0.0, ORDERED_POSITIONAL_FRONT_END);
    float effectiveOrder = min(
            saturate(releaseOrder)
                    * clamp(CollapseLocality,
                            MIN_COLLAPSE_LOCALITY, 1.0),
            0.9999);
    float scale = 1.0;
    if (effectiveOrder < targetOrder) {
        float orderedAdvance = clamp(
                (targetOrder - effectiveOrder)
                        / max(1.0 - effectiveOrder, 0.0001),
                0.0, 1.0);
        scale = clamp(
                1.0 - (1.0 - BODY_COLLAPSE_RETAINED_SPAN)
                        * orderedAdvance,
                BODY_COLLAPSE_RETAINED_SPAN, 1.0);
    }
    return scale;
}

float terminalStart(float baseStart, float releaseOrder) {
    float effectiveOrder = min(saturate(releaseOrder), 0.9999)
            * clamp(CollapseLocality, MIN_COLLAPSE_LOCALITY, 1.0);
    return baseStart + effectiveOrder * TERMINAL_ORDER_LEAD;
}

float terminalTransverseScale(float releaseOrder) {
    float blend = smoothstep(
            terminalStart(
                    ORDERED_POSITIONAL_FRONT_END, releaseOrder),
            TERMINAL_TRANSVERSE_END,
            ReleaseFront);
    return mix(1.0, TERMINAL_TRANSVERSE_SCALE, blend);
}

float terminalAxialScale(float releaseOrder) {
    float blend = smoothstep(
            terminalStart(TERMINAL_AXIAL_START, releaseOrder),
            TERMINAL_THROAT_END,
            ReleaseFront);
    return mix(1.0, TERMINAL_AXIAL_SCALE, blend);
}

mat4 packedBoneRouteNodes(int index) {
    if (index <= 0) {
        return BoneRouteNode0;
    }
    if (index == 1) {
        return BoneRouteNode1;
    }
    if (index == 2) {
        return BoneRouteNode2;
    }
    if (index == 3) {
        return BoneRouteNode3;
    }
    if (index == 4) {
        return BoneRouteNode4;
    }
    if (index == 5) {
        return BoneRouteNode5;
    }
    if (index == 6) {
        return BoneRouteNode6;
    }
    return BoneRouteNode7;
}


void buildSurfaceTangentFrame(vec3 normal,
                              out vec3 tangent0,
                              out vec3 tangent1) {
    vec3 unitNormal = safeNormalize(
            normal, vec3(0.0, 0.0, 1.0));
    vec3 reference = abs(unitNormal.z) < 0.75
            ? vec3(0.0, 0.0, 1.0)
            : vec3(0.0, 1.0, 0.0);
    tangent0 = safeNormalize(
            cross(reference, unitNormal),
            vec3(1.0, 0.0, 0.0));
    tangent1 = safeNormalize(
            cross(unitNormal, tangent0),
            vec3(0.0, 1.0, 0.0));
}

vec4 boneRouteNode(int nodeIndex) {
    int safeNode = clamp(nodeIndex, 0, 31);
    // The capture-time palette root is only a topological marker. The live
    // frozen inlet supplied by this draw owns the exact terminal position.
    if (safeNode == 0) {
        return vec4(InletLocal, -1.0);
    }
    int matrixIndex = safeNode / 4;
    int columnIndex = safeNode - matrixIndex * 4;
    mat4 packedNodes = packedBoneRouteNodes(matrixIndex);
    if (columnIndex == 0) {
        return packedNodes[0];
    }
    if (columnIndex == 1) {
        return packedNodes[1];
    }
    if (columnIndex == 2) {
        return packedNodes[2];
    }
    return packedNodes[3];
}

void decodeBoneRouteMetadata(out int attachmentNode,
                             out float attachmentT,
                             out float routeConfidence,
                             out float normalizedPathLength) {
    attachmentNode = int(floor(Color.b * 255.0 + 0.5)) & 31;
    int packedAttachmentTAndCorner =
            int(floor(Color.a * 255.0 + 0.5));
    attachmentT = float((packedAttachmentTAndCorner >> 2) & 63) / 63.0;
    int packedRoute = UV2.y & 65535;
    routeConfidence =
            float(packedRoute & 255) / 255.0;
    normalizedPathLength =
            float((packedRoute >> 8) & 255) / 255.0;
}

vec3 rotateDirectionBetween(vec3 value,
                            vec3 fromDirection,
                            vec3 toDirection) {
    vec3 fromUnit = safeNormalize(
            fromDirection, vec3(1.0, 0.0, 0.0));
    vec3 toUnit = safeNormalize(
            toDirection, fromUnit);
    float cosine = clamp(dot(fromUnit, toUnit), -1.0, 1.0);
    vec3 rawAxis = cross(fromUnit, toUnit);
    float axisLengthSquared = dot(rawAxis, rawAxis);
    if (axisLengthSquared < 1e-10) {
        if (cosine > 0.0) {
            return value;
        }
        vec3 reference = abs(fromUnit.x) < 0.75
                ? vec3(1.0, 0.0, 0.0)
                : vec3(0.0, 1.0, 0.0);
        vec3 halfTurnAxis = safeNormalize(
                cross(fromUnit, reference),
                vec3(0.0, 0.0, 1.0));
        return rotateAroundAxis(value, halfTurnAxis, 0.5 * TAU);
    }
    float sine = sqrt(axisLengthSquared);
    vec3 axis = rawAxis / sine;
    return rotateAroundAxis(
            value, axis, atan(sine, cosine));
}

vec3 polarRouteSegmentPosition(vec3 startPosition,
                               vec3 endPosition,
                               float segmentProgress) {
    vec3 startRelative = startPosition - InletLocal;
    vec3 endRelative = endPosition - InletLocal;
    float startRadius = length(startRelative);
    float endRadius = length(endRelative);
    vec3 fallbackDirection = startRadius > 0.0001
            ? startRelative / startRadius
            : safeNormalize(
                    endRelative, vec3(1.0, 0.0, 0.0));
    vec3 startDirection = safeNormalize(
            startRelative, fallbackDirection);
    vec3 endDirection = safeNormalize(
            endRelative, fallbackDirection);
    vec3 direction = safeNormalize(
            mix(
                    startDirection,
                    endDirection,
                    saturate(segmentProgress)),
            fallbackDirection);
    float radius = mix(
            startRadius,
            endRadius,
            saturate(segmentProgress));
    return InletLocal + direction * radius;
}

vec3 boneRoutePosition(vec3 sourcePosition,
                       float pathProgress,
                       out vec3 initialTangent,
                       out vec3 currentTangent,
                       out float routeWeight,
                       out float coherentRoutePhase,
                       out float terminalVisibility) {
    int attachmentNode;
    float attachmentT;
    float routeConfidence;
    float normalizedPathLength;
    decodeBoneRouteMetadata(
            attachmentNode,
            attachmentT,
            routeConfidence,
            normalizedPathLength);

    vec4 child = boneRouteNode(attachmentNode);
    int parentNode = int(floor(child.w + 0.5));
    bool validMetadata = routeConfidence > 0.0
            && normalizedPathLength > 0.0
            && attachmentNode > 0
            && parentNode >= 0
            && parentNode < 32
            && parentNode != attachmentNode;
    bool validRoute = BoneRouteEnabled > 0.5
            && validMetadata;
    // Confidence is a preparation-time assignment diagnostic. Once a route
    // has survived CPU repair and compact metadata validation it owns center
    // transport completely; blending low-confidence centers back toward the
    // direct sink made otherwise valid limbs appear to abandon the bone tree.
    routeWeight = validRoute ? 1.0 : 0.0;
    // Every surfel attached to the same compact anatomical segment receives
    // the same curl phase. This prevents neighboring samples from peeling
    // into independent helical trails while preserving opposing motion across
    // different branches.
    float branchCurlPhase = (
            float(attachmentNode) * 0.61803398875
                    + float(max(parentNode, 0)) * 0.38196601125)
            * TAU;
    coherentRoutePhase = validRoute
            ? branchCurlPhase
            : 0.0;
    // Unrouted samples remain at their captured position before release so a
    // damaged metadata byte cannot punch a preparation-stage hole. The main
    // path retires them as soon as their local release begins.
    terminalVisibility = 1.0;
    if (!validRoute) {
        initialTangent = safeNormalize(
                Normal,
                vec3(1.0, 0.0, 0.0));
        currentTangent = initialTangent;
        return sourcePosition;
    }

    vec3 parentPosition = boneRouteNode(parentNode).xyz;
    // attachmentT is defined parent -> child so that nearby samples on one
    // limb share an ordered route while preserving their local attachment.
    vec3 attachmentPosition = polarRouteSegmentPosition(
            parentPosition, child.xyz, attachmentT);
    float sourceRadius = length(sourcePosition - InletLocal);
    float attachmentRadius =
            length(attachmentPosition - InletLocal);
    if (attachmentRadius > sourceRadius + 0.0001) {
        // CPU preparation already biases the byte-quantized attachment
        // inward. Keep this final GPU guard non-expanding without changing
        // transport families if precision or driver interpolation differs.
        attachmentPosition = InletLocal
                + safeNormalize(
                attachmentPosition - InletLocal,
                sourcePosition - InletLocal)
                * sourceRadius;
    }
    initialTangent = safeNormalize(
            attachmentPosition - sourcePosition,
            parentPosition - sourcePosition);
    currentTangent = initialTangent;

    float targetDistance = saturate(pathProgress)
            * max(
                    normalizedPathLength
                            * BoneRoutePathScale,
                    0.0001);
    vec3 segmentStart = sourcePosition;
    vec3 segmentEnd = attachmentPosition;
    float segmentLength = distance(
            segmentStart, segmentEnd);
    if (targetDistance <= segmentLength) {
        currentTangent = safeNormalize(
                segmentEnd - segmentStart,
                initialTangent);
        return polarRouteSegmentPosition(
                segmentStart,
                segmentEnd,
                targetDistance / max(segmentLength, 0.0001));
    }
    targetDistance -= segmentLength;
    segmentStart = attachmentPosition;
    int currentNode = attachmentNode;

    // The asynchronous planner validates a maximum depth of eight. Keeping the
    // loop bound constant avoids dynamic traversal and driver-dependent
    // behavior on older Intel GLSL 150 implementations.
    for (int step = 0; step < 8; step++) {
        vec4 current = boneRouteNode(currentNode);
        int nextNode = int(floor(current.w + 0.5));
        if (nextNode < 0
                || nextNode >= 32
                || nextNode == currentNode) {
            terminalVisibility = 0.0;
            return segmentStart;
        }
        if (nextNode == 0) {
            // Node zero is the siphon inlet, not a surfel transport segment.
            // Hold the center at the final anatomical/root-collar node while
            // its footprint fades. This removes the last straight inlet chord
            // without creating a second bridge or a stationary late blob.
            float terminalDistance = max(
                    distance(segmentStart, InletLocal),
                    0.0001);
            float terminalProgress = saturate(
                    targetDistance / terminalDistance);
            terminalVisibility = 1.0 - smoothstep(
                    0.0, 0.82, terminalProgress);
            return segmentStart;
        }

        segmentEnd = boneRouteNode(nextNode).xyz;
        segmentLength = distance(segmentStart, segmentEnd);
        currentTangent = safeNormalize(
                segmentEnd - segmentStart,
                currentTangent);
        if (targetDistance <= segmentLength) {
            return polarRouteSegmentPosition(
                    segmentStart,
                    segmentEnd,
                    targetDistance
                            / max(segmentLength, 0.0001));
        }
        targetDistance -= segmentLength;
        segmentStart = segmentEnd;
        currentNode = nextNode;
    }
    terminalVisibility = 0.0;
    return segmentStart;
}

void main() {
    splatIdentity = gl_VertexID / 6;
    float transportSection = saturate(UV0.x);
    float localTravel = transportTravel(transportSection);
    float transportQ = transportedCoordinate(
            transportSection, localTravel);
    float localPathProgress = transportPathProgress(
            transportSection, transportQ);
    float localCrossSection =
            transportCrossSectionScale(localPathProgress);
    float localBodyOwnership =
            transportBodyOwnership(localPathProgress);
    float safetyCoverage = finalSafetyCoverage();
    float localVisibleOwnership =
            localBodyOwnership * safetyCoverage;
    float neighborSpacingScale = decodePackedRange(
            UV1.x,
            MIN_NEIGHBOR_SPACING_SCALE,
            MAX_NEIGHBOR_SPACING_SCALE);
    float neighborhoodCoherence =
            decodePackedUnit(UV1.y);
    float localAreaScale = clamp(
            neighborSpacingScale * neighborSpacingScale,
            MIN_NEIGHBOR_AREA_SCALE,
            MAX_NEIGHBOR_AREA_SCALE);
    float principalTangentAngle;
    float principalTangentConfidence;
    decodeTangentFrameMetadata(
            UV2.x,
            principalTangentAngle,
            principalTangentConfidence);
    float thinSheetScore = decodeThinSheetScore();
    float unstableThinSheet = thinSheetScore
            * (1.0 - smoothstep(
                    0.42, 0.86,
                    principalTangentConfidence));

    // The regular siphon mesh owns the collar after the surfel material
    // handoff. Retired body splats clip immediately instead of submitting
    // zero-ownership fragments.
    if (localVisibleOwnership <= 0.0005) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        splatCoordinate = vec2(0.0);
        bodyCoverage = 0.0;
        undeformedCoverage = 0.0;
        cameraRelativePosition = vec3(0.0);
        deformedSurface = 0.0;
        spikeTipStrength = 0.0;
        accumulationWeightScale = 0.0;
        hookStrength = 0.0;
        hookReach = 0.0;
        hookBend = 0.0;
        return;
    }

    vec3 sourceNormal =
            safeNormalize(Normal, vec3(1.0, 0.0, 0.0));
    float auraStrength = saturate(TurbulenceBlend);
    vec3 flowCoordinates =
            spikeTransportCoordinates(
                    Position, UV0.y, SpikeFlowTime);
    float outwardField = spikeField(flowCoordinates);
    float inwardField = indentField(
            flowCoordinates, outwardField);
    vec3 liveFlowCoordinates =
            spikeTransportCoordinates(
                    Position, UV0.y, DeformationFlowTime);
    float liveOutwardField =
            spikeField(liveFlowCoordinates);
    float liveInwardField = indentField(
            liveFlowCoordinates, liveOutwardField);

    float uniformExpansion = BodyRadius * auraStrength * mix(
            BODY_AURA_INITIAL_EXPANSION,
            BODY_AURA_FINAL_EXPANSION, auraStrength);
    float outwardDisplacement = BodyRadius * auraStrength
            * outwardField * mix(BODY_SPIKE_INITIAL_DISPLACEMENT,
                    BODY_SPIKE_MAX_DISPLACEMENT, auraStrength);
    float requestedInward = BodyRadius * auraStrength
            * inwardField * mix(BODY_INDENT_INITIAL_DISPLACEMENT,
                    BODY_INDENT_MAX_DISPLACEMENT, auraStrength);
    float localThickness = max(Color.r * ThicknessScale, 0.0);
    float inwardBudget = max(
            localThickness
                    - VoxelSize
                    * BODY_INDENT_THICKNESS_RESERVE_VOXELS,
            0.0) * BODY_INDENT_THICKNESS_FRACTION;
    float inwardDisplacement = min(
            smoothlyCapPositive(requestedInward, inwardBudget),
            BodyRadius * BODY_INDENT_RADIUS_CAP);
    float liveOutwardDisplacement = BodyRadius * auraStrength
            * liveOutwardField
            * mix(BODY_SPIKE_INITIAL_DISPLACEMENT,
                    BODY_SPIKE_MAX_DISPLACEMENT,
                    auraStrength);
    float liveRequestedInward = BodyRadius * auraStrength
            * liveInwardField
            * mix(BODY_INDENT_INITIAL_DISPLACEMENT,
                    BODY_INDENT_MAX_DISPLACEMENT,
                    auraStrength);
    float liveInwardDisplacement = min(
            smoothlyCapPositive(
                    liveRequestedInward, inwardBudget),
            BodyRadius * BODY_INDENT_RADIUS_CAP);

    float remaining = releaseRemaining(Color.g);
    // Turbulence belongs to the moving material rather than the old reference
    // mask. Retain it through most of local transport, then dissolve it only
    // as the patch reaches the inlet handoff.
    float attachment = 1.0 - smoothstep(
            TRANSPORT_DEFORMATION_FADE_START,
            TRANSPORT_DEFORMATION_FADE_END,
            localPathProgress);
    float retainedExpansion = uniformExpansion
            * (1.0 - 0.80 * inwardField);
    float modalPhase = UV0.y * TAU * 0.55
            + DeformationFlowTime * 0.32;
    float frozenModalPulse = BodyRadius
            * MODAL_PULSE_RADIUS_SCALE
            * auraStrength
            * sin(UV0.x * TAU * 0.50
                    + SpikeFlowTime * 0.23)
            * mix(0.72, 1.0, neighborhoodCoherence);
    float liveModalPulse = BodyRadius
            * MODAL_PULSE_RADIUS_SCALE
            * auraStrength
            * sin(UV0.x * TAU * 0.50
                    + DeformationFlowTime * 0.23)
            * mix(0.72, 1.0, neighborhoodCoherence);
    float maximumDynamicInward = max(
            localThickness
                    * (BODY_MAX_TOTAL_INWARD_THICKNESS_FRACTION
                            - BODY_UPLOAD_INSET_THICKNESS_FRACTION)
                    - VoxelSize
                    * BODY_DYNAMIC_INWARD_RESERVE_VOXELS,
            0.0);
    float rawEdgeDisplacement = (
            retainedExpansion
                    + outwardDisplacement
                    - inwardDisplacement
                    + frozenModalPulse);
    float rawLiveEdgeDisplacement =
            uniformExpansion
                    * (1.0 - 0.80 * liveInwardField)
                    + liveOutwardDisplacement
                    - liveInwardDisplacement
                    + liveModalPulse;
    float unconstrainedEdgeDisplacement = max(
            rawEdgeDisplacement, -maximumDynamicInward);
    float unconstrainedLiveEdgeDisplacement = max(
            rawLiveEdgeDisplacement, -maximumDynamicInward);
    float thinSheetNormalBudget = max(
            VoxelSize * THIN_SHEET_NORMAL_VOXEL_FLOOR,
            localThickness
                    * THIN_SHEET_NORMAL_THICKNESS_FRACTION);
    float sheetSafeEdgeDisplacement = clamp(
            unconstrainedEdgeDisplacement,
            -thinSheetNormalBudget,
            thinSheetNormalBudget);
    float sheetSafeLiveEdgeDisplacement = clamp(
            unconstrainedLiveEdgeDisplacement,
            -thinSheetNormalBudget,
            thinSheetNormalBudget);
    float edgeDisplacement = mix(
            unconstrainedEdgeDisplacement,
            sheetSafeEdgeDisplacement,
            thinSheetScore);
    float liveEdgeDisplacement = mix(
            unconstrainedLiveEdgeDisplacement,
            sheetSafeLiveEdgeDisplacement,
            thinSheetScore);

    // A paired front/back sheet must have one center trajectory. Energy that
    // cannot safely move the center along two opposing normals is redirected
    // into the shared tangent plane, preserving violent curl without peeling
    // a zero-thickness model part into two visible layers.
    vec3 normalizedSheetPosition =
            Position / max(BodyRadius, VoxelSize);
    vec3 frozenSheetFlow = analyticalBoundaryCurl(
            normalizedSheetPosition, SpikeFlowTime);
    vec3 liveSheetFlow = analyticalBoundaryCurl(
            normalizedSheetPosition, DeformationFlowTime);
    frozenSheetFlow -= sourceNormal
            * dot(frozenSheetFlow, sourceNormal);
    liveSheetFlow -= sourceNormal
            * dot(liveSheetFlow, sourceNormal);
    vec3 sheetReference = abs(sourceNormal.x) < 0.75
            ? vec3(1.0, 0.0, 0.0)
            : vec3(0.0, 1.0, 0.0);
    sheetReference -= sourceNormal
            * dot(sheetReference, sourceNormal);
    vec3 frozenSheetDirection = safeNormalize(
            frozenSheetFlow, sheetReference);
    vec3 liveSheetDirection = safeNormalize(
            liveSheetFlow, frozenSheetDirection);
    float frozenRedirectedDisplacement = (
            unconstrainedEdgeDisplacement
                    - sheetSafeEdgeDisplacement)
            * thinSheetScore
            * THIN_SHEET_TANGENTIAL_REDIRECT;
    float liveRedirectedDisplacement = (
            unconstrainedLiveEdgeDisplacement
                    - sheetSafeLiveEdgeDisplacement)
            * thinSheetScore
            * THIN_SHEET_TANGENTIAL_REDIRECT;
    vec3 frozenSheetOffset = frozenSheetDirection
            * frozenRedirectedDisplacement;
    vec3 liveSheetOffset = liveSheetDirection
            * liveRedirectedDisplacement;
    vec3 displacedSource =
            Position
                    + sourceNormal * edgeDisplacement
                    + frozenSheetOffset;

    vec3 rawTerminalAxis = SpikeTransportOrigin - InletLocal;
    float terminalAxisLengthSquared =
            dot(rawTerminalAxis, rawTerminalAxis);
    vec3 terminalAxis = terminalAxisLengthSquared > 1e-10
            ? rawTerminalAxis
                    * inversesqrt(terminalAxisLengthSquared)
            : vec3(1.0, 0.0, 0.0);
    float directProgress = saturate(localPathProgress);
    float directRemaining =
            directTransportRemaining(directProgress);
    vec3 initialRouteTangent;
    vec3 currentRouteTangent;
    float boneRouteWeight;
    float coherentRoutePhase;
    float routeTerminalVisibility;
    vec3 routedBaseLocal = boneRoutePosition(
            Position,
            directProgress,
            initialRouteTangent,
            currentRouteTangent,
            boneRouteWeight,
            coherentRoutePhase,
            routeTerminalVisibility);
    if (boneRouteWeight <= 0.5
            && directProgress > 0.0001) {
        // Neither a missing palette nor damaged per-splat metadata is allowed
        // to select another center-transport family. Released surfels without
        // a validated parent chain retire in place.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        splatCoordinate = vec2(0.0);
        bodyCoverage = 0.0;
        undeformedCoverage = 0.0;
        cameraRelativePosition = vec3(0.0);
        deformedSurface = 0.0;
        spikeTipStrength = 0.0;
        accumulationWeightScale = 0.0;
        hookStrength = 0.0;
        hookReach = 0.0;
        hookBend = 0.0;
        return;
    }
    localVisibleOwnership *= routeTerminalVisibility;
    if (localVisibleOwnership <= 0.0005) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        splatCoordinate = vec2(0.0);
        bodyCoverage = 0.0;
        undeformedCoverage = 0.0;
        cameraRelativePosition = vec3(0.0);
        deformedSurface = 0.0;
        spikeTipStrength = 0.0;
        accumulationWeightScale = 0.0;
        hookStrength = 0.0;
        hookReach = 0.0;
        hookBend = 0.0;
        return;
    }
    float modalTwistAngle = auraStrength
            * MODAL_TWIST_MAX_ANGLE
            * sin(modalPhase)
            * mix(0.72, 1.0, neighborhoodCoherence);
    vec3 modalRelative = rotateAroundAxis(
            displacedSource - InletLocal,
            terminalAxis,
            modalTwistAngle);
    vec3 modalNormal = rotateAroundAxis(
            sourceNormal,
            terminalAxis,
            modalTwistAngle);

    // A cumulative Kelvinlet-style constriction activates as each local
    // section is reached by the release wave. It never relaxes, so its
    // transverse scaling cannot increase remaining inlet distance.
    float pinchActivation = smoothstep(
            KELVINLET_PINCH_START_PROGRESS,
            KELVINLET_PINCH_FULL_PROGRESS,
            directProgress);
    float pinchStrength = KELVINLET_MAX_PINCH
            * pinchActivation
            * mix(0.76, 1.0, neighborhoodCoherence);
    float axialOffset = dot(modalRelative, terminalAxis);
    vec3 pinchedRelative =
            terminalAxis * axialOffset
            + (modalRelative - terminalAxis * axialOffset)
            * (1.0 - pinchStrength);

    float curlAngle = nearInletCurlAngle(
            directProgress,
            sin(coherentRoutePhase)
                    * boneRouteWeight
                    * mix(0.72, 1.0, neighborhoodCoherence));
    vec3 curledRelative = rotateAroundAxis(
            pinchedRelative,
            terminalAxis,
            curlAngle);
    vec3 bendAxis = safeNormalize(
            cross(terminalAxis, modalNormal),
            vec3(0.0, 1.0, 0.0));
    float bendWindow = smoothstep(
            0.32, 0.86, directProgress)
            * (1.0 - directProgress);
    float bendAngle = DIRECT_BEND_MAX_ANGLE
            * bendWindow
            * sin(coherentRoutePhase * 0.50 + 1.17)
            * boneRouteWeight
            * mix(0.70, 1.0, neighborhoodCoherence);
    vec3 directRelative = rotateAroundAxis(
            curledRelative, bendAxis, bendAngle);
    vec3 directDisplayedLocal = InletLocal
            + directRelative * directRemaining;
    vec3 directDisplayedNormal = rotateAroundAxis(
            modalNormal, terminalAxis, curlAngle);
    directDisplayedNormal = rotateAroundAxis(
            directDisplayedNormal, bendAxis, bendAngle);

    // Carry frozen surface deformation in the local routed frame, then retain
    // the existing modal bend, pinch, and curl as a bounded directional warp.
    // The undeformed carrier owns path length, so live turbulence cannot make
    // a sample under- or overshoot its parent chain.
    vec3 routedCarrierOffset = rotateDirectionBetween(
            displacedSource - Position,
            initialRouteTangent,
            currentRouteTangent) * attachment;
    vec3 directBaseline = InletLocal
            + (displacedSource - InletLocal)
            * directRemaining;
    vec3 routedCandidate = routedBaseLocal
            + routedCarrierOffset
            + directDisplayedLocal
            - directBaseline;
    // Monotonic inlet distance alone does not keep a curl close to its
    // anatomical carrier. Clamp only the center's lateral route-relative
    // offset to a corridor that narrows toward the inlet. Axial motion remains
    // owned by the prepared parent chain, while the later footprint stage
    // retains the exaggerated connected hook/crescent.
    vec3 routeCorridorTangent = safeNormalize(
            currentRouteTangent,
            initialRouteTangent);
    vec3 routeWarp = routedCandidate - routedBaseLocal;
    float routeAxialDistance = dot(
            routeWarp, routeCorridorTangent);
    vec3 routeAxialOffset =
            routeCorridorTangent * routeAxialDistance;
    vec3 routeLateralOffset =
            routeWarp - routeAxialOffset;
    float routeLateralLength = length(routeLateralOffset);
    float routeCorridorTaper = smoothstep(
            ROUTE_CORRIDOR_TAPER_START_PROGRESS,
            ROUTE_CORRIDOR_TAPER_END_PROGRESS,
            directProgress);
    float routeCorridorRadius = max(
            VoxelSize * ROUTE_CORRIDOR_MIN_VOXELS,
            SplatRadius
                    * mix(
                            ROUTE_CORRIDOR_START_SPLAT_RADII,
                            ROUTE_CORRIDOR_END_SPLAT_RADII,
                            routeCorridorTaper)
                    * sqrt(max(localCrossSection, 0.04))
                    * mix(0.78, 1.0, neighborhoodCoherence));
    vec3 clampedRouteLateralOffset = routeLateralOffset
            * min(
                    1.0,
                    routeCorridorRadius
                            / max(routeLateralLength, 0.0001));
    float routeCorridorAuthority = boneRouteWeight * smoothstep(
            ROUTE_CORRIDOR_CLAMP_START_PROGRESS,
            ROUTE_CORRIDOR_CLAMP_FULL_PROGRESS,
            directProgress);
    routedCandidate = routedBaseLocal
            + routeAxialOffset
            + mix(
                    routeLateralOffset,
                    clampedRouteLateralOffset,
                    routeCorridorAuthority);
    vec3 routedRelative = routedCandidate - InletLocal;
    float routedBaseRadius =
            length(routedBaseLocal - InletLocal);
    float routedDeformationBudget =
            length(routedCarrierOffset);
    float initialRouteRadius = max(
            length(Position - InletLocal),
            length(displacedSource - InletLocal));
    float routedRadius = min(
            length(routedRelative),
            min(
                    routedBaseRadius + routedDeformationBudget,
                    initialRouteRadius));
    vec3 routedLocal =
            InletLocal
                    + safeNormalize(
                    routedRelative,
                    routedBaseLocal - InletLocal)
                    * routedRadius;
    vec3 routedRemainingVector =
            routedLocal - InletLocal;
    vec3 displayedDirection = safeNormalize(
            routedRemainingVector,
            terminalAxis);
    float displayedRadius = min(
            length(routedRemainingVector),
            initialRouteRadius);
    vec3 displayedLocal = InletLocal
            + displayedDirection * displayedRadius;

    vec3 routeAlignedNormal = rotateDirectionBetween(
            directDisplayedNormal,
            initialRouteTangent,
            currentRouteTangent);
    vec3 displayedNormal = safeNormalize(
            mix(
                    directDisplayedNormal,
                    routeAlignedNormal,
                    boneRouteWeight * 0.72),
            directDisplayedNormal);
    float transportDisplacement = length(
            displayedLocal - displacedSource);

    vec3 cameraRelativeCenter = VolumeRootCameraRelative
            + VolumeAxis * displayedLocal.x
            + VolumeSide * displayedLocal.y
            + VolumeUp * displayedLocal.z;
    vec4 viewPosition =
            ModelViewMat * vec4(cameraRelativeCenter, 1.0);
    vec4 centerClipPosition = ProjMat * viewPosition;
    float centerClipMagnitude = max(
            max(abs(centerClipPosition.x), abs(centerClipPosition.y)),
            max(abs(centerClipPosition.z), abs(centerClipPosition.w)));
    float centerClipW = centerClipPosition.w;
    vec2 centerNdc = centerClipPosition.xy
            / max(centerClipW, 0.0001);
    bool invalidCenterProjection =
            any(notEqual(centerClipPosition, centerClipPosition))
            || centerClipMagnitude > 1.0e20
            || centerClipW <= 0.0001
            || any(greaterThan(abs(centerNdc), vec2(8.0)));
    if (invalidCenterProjection) {
        // A stale/non-perspective Iris transform or a center crossing the
        // eye plane must never let a finite world-space footprint become a
        // screen-sized slab after perspective division.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        splatCoordinate = vec2(0.0);
        bodyCoverage = 0.0;
        undeformedCoverage = 0.0;
        cameraRelativePosition = vec3(0.0);
        deformedSurface = 0.0;
        spikeTipStrength = 0.0;
        accumulationWeightScale = 0.0;
        hookStrength = 0.0;
        hookReach = 0.0;
        hookBend = 0.0;
        return;
    }

    // Color.a packs a 6-bit bone attachment coordinate and this explicit
    // two-bit corner. The billboard no longer depends on Iris preserving
    // Minecraft's indexed-QUADS gl_VertexID convention.
    int packedAttachmentTAndCorner =
            int(floor(Color.a * 255.0 + 0.5));
    int cornerIndex = packedAttachmentTAndCorner & 3;
    vec2 corner = vec2(
            (cornerIndex == 0 || cornerIndex == 3) ? -1.0 : 1.0,
            (cornerIndex == 0 || cornerIndex == 1) ? -1.0 : 1.0);
    vec3 cameraRelativeNormal = safeNormalize(
            VolumeAxis * displayedNormal.x
                    + VolumeSide * displayedNormal.y
                    + VolumeUp * displayedNormal.z,
            vec3(1.0, 0.0, 0.0));
    vec3 viewNormal = safeNormalize(
            mat3(ModelViewMat) * cameraRelativeNormal,
            vec3(0.0, 0.0, 1.0));
    float edgeOn = 1.0 - abs(viewNormal.z);
    float orientationSeed = hash31(
            Position * 13.71 + vec3(4.17, -8.31, 2.93));
    float fallbackAngle = orientationSeed * TAU;
    vec2 fallbackTangent =
            vec2(cos(fallbackAngle), sin(fallbackAngle));
    vec2 screenNormal = safeNormalize2(
            viewNormal.xy,
            vec2(-fallbackTangent.y, fallbackTangent.x));
    vec2 screenTangent =
            vec2(-screenNormal.y, screenNormal.x);
    float silhouetteBlend = smoothstep(0.16, 0.92, edgeOn);
    spikeTipStrength = saturate(
            auraStrength * liveOutwardField * attachment)
            * silhouetteBlend
            * (1.0 - 0.88 * thinSheetScore);
    float tangentStretch = mix(
            1.0, MAX_SPLAT_TANGENT_STRETCH, silhouetteBlend);
    float normalStretch = mix(
            1.0, MIN_SPLAT_NORMAL_STRETCH, silhouetteBlend);
    tangentStretch *= mix(
            1.0,
            HIGH_SPIKE_TANGENT_MULTIPLIER,
            spikeTipStrength);
    normalStretch *= mix(
            1.0,
            HIGH_SPIKE_NORMAL_MULTIPLIER,
            spikeTipStrength);

    // Build a local captured frame, then hand only its projected longitudinal
    // direction to the real siphon tangent near the inlet. No global spine or
    // covariance eigensystem is reconstructed.
    vec3 sourceTangent0;
    vec3 sourceTangent1;
    buildSurfaceTangentFrame(
            sourceNormal, sourceTangent0, sourceTangent1);
    vec3 sourcePrincipalTangent = safeNormalize(
            sourceTangent0 * cos(principalTangentAngle)
                    + sourceTangent1 * sin(principalTangentAngle),
            sourceTangent0);
    vec3 directPrincipalTangent = rotateAroundAxis(
            sourcePrincipalTangent,
            terminalAxis,
            modalTwistAngle);
    directPrincipalTangent = rotateAroundAxis(
            directPrincipalTangent,
            terminalAxis,
            curlAngle);
    directPrincipalTangent = rotateAroundAxis(
            directPrincipalTangent,
            bendAxis,
            bendAngle);
    vec3 routePrincipalTangent = rotateDirectionBetween(
            directPrincipalTangent,
            initialRouteTangent,
            currentRouteTangent);
    vec3 displayedPrincipalTangent = safeNormalize(
            mix(
                    directPrincipalTangent,
                    routePrincipalTangent,
                    boneRouteWeight * 0.72),
            directPrincipalTangent);
    displayedPrincipalTangent = safeNormalize(
            displayedPrincipalTangent
                    - displayedNormal
                    * dot(displayedPrincipalTangent,
                            displayedNormal),
            directPrincipalTangent);
    vec3 localTangent0;
    vec3 localTangent1;
    buildSurfaceTangentFrame(
            displayedNormal, localTangent0, localTangent1);
    float principalTangentAuthority =
            principalTangentConfidence
                    * MAX_PRINCIPAL_TANGENT_AUTHORITY
                    * (1.0 - 0.72 * unstableThinSheet);
    localTangent0 = safeNormalize(
            mix(
                    localTangent0,
                    displayedPrincipalTangent,
                    principalTangentAuthority),
            displayedPrincipalTangent);
    vec3 cameraRelativeTangent0 =
            VolumeAxis * localTangent0.x
                    + VolumeSide * localTangent0.y
                    + VolumeUp * localTangent0.z;
    vec3 viewTangent0 = mat3(ModelViewMat)
            * cameraRelativeTangent0;
    vec2 localScreenTangent = safeNormalize2(
            viewTangent0.xy, screenTangent);
    screenTangent *= dot(
            screenTangent, localScreenTangent) < 0.0
            ? -1.0 : 1.0;
    // A captured tangent basis is stable but arbitrary. At the projected
    // contour, the long support axis must follow the actual screen-space
    // silhouette tangent or it can point partly outward and visibly inflate
    // the Pokemon.
    float normalContourTrust = silhouetteBlend
            * (1.0 - 0.45 * principalTangentConfidence);
    vec2 contourScreenTangent = safeNormalize2(
            mix(localScreenTangent,
                    screenTangent,
                    normalContourTrust),
            screenTangent);
    vec3 cameraRelativeSiphonTangent =
            VolumeAxis * SiphonTangentLocal.x
                    + VolumeSide * SiphonTangentLocal.y
                    + VolumeUp * SiphonTangentLocal.z;
    vec2 siphonScreenTangent = safeNormalize2(
            (mat3(ModelViewMat)
                    * cameraRelativeSiphonTangent).xy,
            screenTangent);
    siphonScreenTangent *= dot(
            siphonScreenTangent, contourScreenTangent) < 0.0
            ? -1.0 : 1.0;
    vec3 normalizedBoundaryPosition =
            Position / max(BodyRadius, VoxelSize);
    vec3 boundaryCurlLocal = analyticalBoundaryCurl(
            normalizedBoundaryPosition,
            DeformationFlowTime);
    boundaryCurlLocal -= sourceNormal
            * dot(boundaryCurlLocal, sourceNormal);
    float boundaryCurlIntensity = clamp(
            length(boundaryCurlLocal), 0.0, 1.40);
    vec3 cameraRelativeBoundaryCurl =
            VolumeAxis * boundaryCurlLocal.x
                    + VolumeSide * boundaryCurlLocal.y
                    + VolumeUp * boundaryCurlLocal.z;
    vec2 boundaryCurlScreen = safeNormalize2(
            (mat3(ModelViewMat)
                    * cameraRelativeBoundaryCurl).xy,
            contourScreenTangent);
    float handoff = smoothstep(
            SPLAT_HANDOFF_START_PROGRESS,
            SPLAT_HANDOFF_FULL_PROGRESS,
            directProgress);
    float cohesiveHandoff = handoff
            * mix(0.82, 1.0, neighborhoodCoherence);
    vec2 ellipseXDirection = safeNormalize2(
            mix(contourScreenTangent,
                    siphonScreenTangent,
                    cohesiveHandoff),
            siphonScreenTangent);
    ellipseXDirection *=
            dot(ellipseXDirection, boundaryCurlScreen) < 0.0
                    ? -1.0 : 1.0;
    vec2 ellipseYDirection =
            vec2(-ellipseXDirection.y, ellipseXDirection.x);
    float longitudinalSpacingScale = neighborSpacingScale;
    float transverseSpacingScale = clamp(
            neighborSpacingScale,
            MIN_TRANSVERSE_NEIGHBOR_SCALE,
            MAX_TRANSVERSE_NEIGHBOR_SCALE);
    float featureSupport = smoothstep(
            0.52,
            1.55,
            localThickness
                    / max(SplatRadius * 2.0, VoxelSize));
    featureSupport *= mix(
            0.72,
            1.0,
            principalTangentConfidence);
    float robustFeatureSupport =
            featureSupport * featureSupport;
    float bodyLongitudinalStretchCap = mix(
            MAX_NARROW_SPLAT_TANGENT_STRETCH,
            MAX_BODY_SPLAT_TANGENT_STRETCH,
            robustFeatureSupport);
    bodyLongitudinalStretchCap = mix(
            bodyLongitudinalStretchCap,
            THIN_SHEET_LOW_CONFIDENCE_STRETCH,
            unstableThinSheet);
    float longitudinalStretchCap = mix(
            bodyLongitudinalStretchCap,
            MAX_SPLAT_TANGENT_STRETCH,
            handoff);
    float ellipseXStretch = min(
            longitudinalStretchCap,
            tangentStretch * mix(
                    1.0,
                    SPLAT_HANDOFF_LONGITUDINAL_STRETCH,
                    handoff)
                    * longitudinalSpacingScale);
    float ellipseYStretch = max(
            0.20,
            normalStretch
                    * mix(1.0,
                            SPLAT_HANDOFF_TRANSVERSE_SCALE,
                            handoff)
                    * (1.0 - pinchStrength * 0.55)
                    * mix(1.0,
                            1.20,
                            auraStrength
                                    * liveInwardField
                                    * attachment
                                    * (1.0 - smoothstep(
                                            0.0, 0.10,
                                            directProgress)))
                    * transverseSpacingScale);

    float collapseFootprintScale = max(
            sqrt(saturate(localCrossSection)),
            COLLAPSE_FOOTPRINT_SCALE_FLOOR);
    float baseSplatRadius = max(SplatRadius, 0.0001)
            * collapseFootprintScale;
    float featureHalfWidth = max(
            VoxelSize * FEATURE_TRANSVERSE_VOXEL_FLOOR,
            localThickness
                    * FEATURE_TRANSVERSE_THICKNESS_FRACTION)
            * collapseFootprintScale;
    featureHalfWidth = max(
            featureHalfWidth,
            VoxelSize
                    * THIN_SHEET_TRANSVERSE_VOXEL_FLOOR
                    * collapseFootprintScale
                    * thinSheetScore);
    float thinSheetCoverageHalfWidth =
            baseSplatRadius
                    * THIN_SHEET_TRANSVERSE_RADIUS_FRACTION
                    * mix(
                            0.92,
                            1.08,
                            principalTangentConfidence);
    featureHalfWidth = mix(
            featureHalfWidth,
            max(featureHalfWidth,
                    thinSheetCoverageHalfWidth),
            smoothstep(0.18, 0.72, thinSheetScore));
    float transverseRadius = min(
            baseSplatRadius * ellipseYStretch,
            featureHalfWidth);
    ellipseYStretch = clamp(
            transverseRadius / max(baseSplatRadius, 0.0001),
            0.12,
            ellipseYStretch);
    // This is the inexpensive equivalent of clamping the singular values of
    // a fitted local covariance. The transported body may still stretch, but
    // a single flattened quad cannot become a long unsupported needle. The
    // terminal handoff keeps a larger ratio so it can form a narrow streak.
    float covarianceAnisotropyLimit = mix(
            MAX_BODY_SPLAT_ANISOTROPY,
            MAX_HANDOFF_SPLAT_ANISOTROPY,
            handoff);
    float regularAnisotropyCap = max(
            1.0,
            ellipseYStretch * covarianceAnisotropyLimit);
    float thinSheetAnisotropyCap = max(
            THIN_SHEET_LONGITUDINAL_COVERAGE_FLOOR,
            ellipseYStretch
                    * THIN_SHEET_LOW_CONFIDENCE_ANISOTROPY);
    ellipseXStretch = min(
            ellipseXStretch,
            mix(
                    regularAnisotropyCap,
                    thinSheetAnisotropyCap,
                    unstableThinSheet));
    float collapseViolence = mix(
            0.72,
            1.0,
            smoothstep(0.02, 0.66, directProgress));
    float hookMotionBoost = mix(
            0.88,
            1.48,
            smoothstep(0.02, 0.78, directProgress))
            * mix(
                    0.86,
                    1.20,
                    smoothstep(0.08, 1.20,
                            boundaryCurlIntensity));
    // Large crescents belong only to coherent spike peaks. Giving every
    // contour splat a minimum hook contribution turns the connected field into
    // one broad furry halo and rasterizes enormous mostly-empty quads.
    float hookCarrier = smoothstep(
            0.46, 0.82, liveOutwardField);
    hookCarrier *= hookCarrier;
    float footprintViolence = smoothstep(
            0.20, 0.78, TurbulenceBlend);
    footprintViolence *= footprintViolence;
    hookStrength = saturate(
            silhouetteBlend
                    * auraStrength
                    * attachment
                    * collapseViolence
                    * hookCarrier
                    * mix(0.76, 1.0, neighborhoodCoherence)
                    * mix(0.08, 1.0, robustFeatureSupport)
                    * mix(0.08, 1.0, footprintViolence)
                    * (1.0 - thinSheetScore));
    float rawHookReach = BOUNDARY_HOOK_MAX_REACH
            * hookStrength
            * hookMotionBoost
            * mix(0.62, 1.0, TurbulenceComplexityBlend);
    hookReach = min(
            rawHookReach,
            mix(0.16,
                    BOUNDARY_HOOK_MAX_REACH,
                    robustFeatureSupport));
    float curlCross = ellipseXDirection.x
            * boundaryCurlScreen.y
            - ellipseXDirection.y
            * boundaryCurlScreen.x;
    float bendCarrier = curlCross
            + 0.36 * sin(
                    UV0.y * TAU * 0.72
                            + DeformationFlowTime * 1.43);
    float bendSign = bendCarrier < 0.0 ? -1.0 : 1.0;
    hookBend = bendSign
            * min(
            BOUNDARY_HOOK_MAX_BEND,
            mix(0.12,
                    BOUNDARY_HOOK_MAX_BEND,
                    robustFeatureSupport))
            * hookStrength
            * hookMotionBoost
            * mix(0.55, 1.0, TurbulenceComplexityBlend);
    float dynamicFootprintArea = max(
            ellipseXStretch * ellipseYStretch,
            0.08);
    float areaCompensation = clamp(
            sqrt(localAreaScale / dynamicFootprintArea),
            0.70,
            1.24);
    float compressionDensityFade = mix(
            1.0,
            0.30,
            smoothstep(0.42, 0.98, directProgress));
    float regularAccumulationWeight = clamp(
            areaCompensation * compressionDensityFade,
            0.24,
            1.24);
    float thinSheetFusionConfidence = smoothstep(
            0.18, 0.72, thinSheetScore);
    float thinSheetAccumulationFloor =
            THIN_SHEET_FUSION_SUPPORT
                    * thinSheetFusionConfidence
                    * compressionDensityFade;
    accumulationWeightScale = max(
            regularAccumulationWeight,
            thinSheetAccumulationFloor);
    float animatedEdgeDelta =
            (liveEdgeDisplacement - edgeDisplacement)
                    * attachment
                    * directRemaining;
    float inwardAnimationLimit =
            baseSplatRadius * ellipseYStretch * 0.75;
    float animatedEdgeShift = clamp(
            animatedEdgeDelta,
            -inwardAnimationLimit,
            min(
                    baseSplatRadius * 0.85,
                     featureHalfWidth * 0.90));
    viewPosition.xy += screenNormal * animatedEdgeShift;
    vec3 liveSheetAnimationLocal = (
            liveSheetOffset - frozenSheetOffset)
            * attachment
            * directRemaining;
    vec3 liveSheetAnimationCameraRelative =
            VolumeAxis * liveSheetAnimationLocal.x
                    + VolumeSide * liveSheetAnimationLocal.y
                    + VolumeUp * liveSheetAnimationLocal.z;
    viewPosition.xy += (
            mat3(ModelViewMat)
                    * liveSheetAnimationCameraRelative).xy;
    float projectedNormalLength = length(viewNormal.xy);
    float spikeBridgeLength = min(
            liveOutwardDisplacement * attachment
                    * projectedNormalLength,
             min(
                     baseSplatRadius * MAX_SPIKE_BRIDGE_RADII,
                     featureHalfWidth
                            * mix(
                                    0.90,
                                     1.75,
                                     robustFeatureSupport)))
            * silhouetteBlend
            * (1.0 - 0.90 * thinSheetScore);
    // Recenter the same quad halfway toward its undeformed attachment and add
    // the same amount to its normal half-extent. This covers the body-to-tip
    // interval without pushing the tip farther out or emitting another splat.
    viewPosition.xy -=
            screenNormal * (spikeBridgeLength * 0.5);
    float hookDomainScaleX = 1.0 + hookReach * 0.5;
    float hookDomainCenterX = hookReach * 0.5;
    float hookDomainScaleY = 1.0 + abs(hookBend);
    float diagnosticPlaneDepthLimit = max(
            baseSplatRadius
                    * max(ellipseXStretch, ellipseYStretch)
                    * 0.90,
            VoxelSize * 0.35);

    // Validate the complete billboard before choosing this invocation's
    // corner. A vertex-local rejection leaves the other three vertices alive;
    // the indexed quad then clips into one or two enormous triangles. That is
    // especially visible under Iris as a coherent black slab with a valid
    // purple contour. Every invocation evaluates the same four candidates, so
    // an unsafe footprint retires all six triangle-list vertices together.
    bool invalidExpandedFootprint = false;
    for (int footprintCornerIndex = 0;
         footprintCornerIndex < 4;
         footprintCornerIndex++) {
        vec2 footprintCorner = vec2(
                (footprintCornerIndex == 0
                        || footprintCornerIndex == 3) ? -1.0 : 1.0,
                (footprintCornerIndex == 0
                        || footprintCornerIndex == 1) ? -1.0 : 1.0);
        vec2 footprintHookDomainCoordinate = vec2(
                footprintCorner.x * hookDomainScaleX
                        + hookDomainCenterX,
                footprintCorner.y * hookDomainScaleY);
        vec2 footprintOffset =
                ellipseXDirection
                        * (footprintHookDomainCoordinate.x
                        * baseSplatRadius
                        * ellipseXStretch)
                + ellipseYDirection
                        * (footprintHookDomainCoordinate.y
                        * baseSplatRadius
                        * ellipseYStretch)
                + screenNormal
                        * (footprintCorner.y
                        * spikeBridgeLength * 0.5);
        vec4 footprintViewPosition = viewPosition;
        footprintViewPosition.xy += footprintOffset;
        if (SplatDiagnosticMode > 1.5) {
            float safeFootprintViewNormalZ = abs(viewNormal.z) > 0.24
                    ? viewNormal.z
                    : (viewNormal.z < 0.0 ? -0.24 : 0.24);
            float footprintPlaneDepthOffset =
                    -dot(viewNormal.xy, footprintOffset)
                            / safeFootprintViewNormalZ;
            footprintViewPosition.z += clamp(
                    footprintPlaneDepthOffset,
                    -diagnosticPlaneDepthLimit,
                    diagnosticPlaneDepthLimit);
        }
        vec4 footprintProjectedPosition =
                ProjMat * footprintViewPosition;
        float footprintProjectedMagnitude = max(
                max(abs(footprintProjectedPosition.x),
                        abs(footprintProjectedPosition.y)),
                max(abs(footprintProjectedPosition.z),
                        abs(footprintProjectedPosition.w)));
        float footprintProjectedW = footprintProjectedPosition.w;
        vec2 footprintProjectedNdc = footprintProjectedPosition.xy
                / max(footprintProjectedW, 0.0001);
        invalidExpandedFootprint = invalidExpandedFootprint
                || any(notEqual(
                        footprintProjectedPosition,
                        footprintProjectedPosition))
                || footprintProjectedMagnitude > 1.0e20
                || footprintProjectedW <= 0.0001
                || any(greaterThan(
                        abs(footprintProjectedNdc - centerNdc),
                        vec2(MAX_SAFE_FOOTPRINT_NDC)));
    }
    if (invalidExpandedFootprint) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        splatCoordinate = vec2(0.0);
        bodyCoverage = 0.0;
        undeformedCoverage = 0.0;
        cameraRelativePosition = vec3(0.0);
        deformedSurface = 0.0;
        spikeTipStrength = 0.0;
        accumulationWeightScale = 0.0;
        hookStrength = 0.0;
        hookReach = 0.0;
        hookBend = 0.0;
        return;
    }

    vec2 hookDomainCoordinate = vec2(
            corner.x * hookDomainScaleX + hookDomainCenterX,
            corner.y * hookDomainScaleY);
    vec2 splatOffset =
            ellipseXDirection
                    * (hookDomainCoordinate.x
                    * baseSplatRadius
                    * ellipseXStretch)
            + ellipseYDirection
                    * (hookDomainCoordinate.y
                    * baseSplatRadius
                    * ellipseYStretch)
            + screenNormal
                    * (corner.y * spikeBridgeLength * 0.5);
    viewPosition.xy += splatOffset;
    if (SplatDiagnosticMode > 1.5) {
        // The production accumulation intentionally keeps every surfel at its
        // center depth so all overlapping footprints can fuse. This separate
        // replay approximates the captured tangent plane and lets hardware
        // depth testing select only the nearest supported surfel.
        float safeViewNormalZ = abs(viewNormal.z) > 0.24
                ? viewNormal.z
                : (viewNormal.z < 0.0 ? -0.24 : 0.24);
        float planeDepthOffset =
                -dot(viewNormal.xy, splatOffset) / safeViewNormalZ;
        viewPosition.z += clamp(
                planeDepthOffset,
                -diagnosticPlaneDepthLimit,
                diagnosticPlaneDepthLimit);
    }

    gl_Position = ProjMat * viewPosition;
    splatCoordinate = hookDomainCoordinate;
    bodyCoverage = saturate(
            EffectFade
                    * localVisibleOwnership);
    undeformedCoverage = saturate(
            remaining
                    * EffectFade
                    * localVisibleOwnership);
    // The fused material payload remains anchored to the sample center so
    // billboard expansion cannot change its harmonic depth. Only the isolated
    // front-depth replay receives the oriented plane position above.
    cameraRelativePosition = SplatDiagnosticMode > 1.5
            ? viewPosition.xyz
            : cameraRelativeCenter;
    // The signed edge resolve may admit tagged material outside the
    // texture-exact mask, so tag only real outward/transport deformation.
    // Uniform aura padding and dimensionless path progress must never grant an
    // entire billboard authority over the captured contour.
    float positiveShapedDisplacement = max(
            edgeDisplacement - retainedExpansion, 0.0);
    float sheetDeformationDistance = max(
            length(frozenSheetOffset),
            length(liveSheetOffset))
            * attachment;
    float trueDeformationDistance = max(
            positiveShapedDisplacement,
            max(
                    sheetDeformationDistance,
                    max(max(animatedEdgeShift, 0.0),
                            transportDisplacement)));
    deformedSurface = step(
            max(VoxelSize
                    * DEFORMED_OWNERSHIP_MIN_DISPLACEMENT_VOXELS,
                    0.00002),
            trueDeformationDistance);
    if (SplatDiagnosticMode < -0.5) {
        // Compare the actual post-cap live normal displacement with the same
        // expansion/modal carrier evaluated without spikes or indentations.
        // This includes thickness guards and thin-sheet safety, so the
        // heatmap reports what a surfel can really display rather than the
        // uncapped procedural request.
        float neutralRawDisplacement =
                uniformExpansion + liveModalPulse;
        float neutralUnconstrainedDisplacement = max(
                neutralRawDisplacement,
                -maximumDynamicInward);
        float neutralSheetSafeDisplacement = clamp(
                neutralUnconstrainedDisplacement,
                -thinSheetNormalBudget,
                thinSheetNormalBudget);
        float neutralEdgeDisplacement = mix(
                neutralUnconstrainedDisplacement,
                neutralSheetSafeDisplacement,
                thinSheetScore);
        float signedAmplitudeDistance =
                (liveEdgeDisplacement
                        - neutralEdgeDisplacement)
                        * attachment;
        float polarityScale = signedAmplitudeDistance < 0.0
                ? BodyRadius * BODY_INDENT_MAX_DISPLACEMENT
                : BodyRadius * BODY_SPIKE_MAX_DISPLACEMENT;
        float signedNormalizedAmplitude = clamp(
                signedAmplitudeDistance
                        / max(polarityScale, 0.0001),
                -1.0,
                1.0);
        // Mode 13 deliberately reuses the existing A accumulation lane. The
        // regular path still receives its unchanged binary deformation tag.
        deformedSurface = signedNormalizedAmplitude * 0.5 + 0.5;
    }
}
