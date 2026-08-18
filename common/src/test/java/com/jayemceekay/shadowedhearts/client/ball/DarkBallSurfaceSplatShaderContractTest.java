package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSurfaceSplatShaderContractTest {
    private static final Pattern SWEEP_MATRIX_UNIFORM = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"BoneRouteNode[0-7]\""
                    + ".*?\"values\"\\s*:\\s*\\[(.*?)]\\s*}",
            Pattern.DOTALL);
    private static final Pattern TRANSPORT_SCALAR_UNIFORM = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"(Transport(?:ActivationHalfWidth"
                    + "|FrontSpan|MinimumCrossSection|ThroatCoordinate"
                    + "|TerminalCoordinate|RetirementWidth"
                    + "|CollarExitCoordinate|CollarExitWidth))\""
                    + "\\s*,\\s*\"type\"\\s*:\\s*\"float\""
                    + "\\s*,\\s*\"count\"\\s*:\\s*1"
                    + "\\s*,\\s*\"values\"\\s*:\\s*\\[(.*?)]\\s*}",
            Pattern.DOTALL);
    private static final String RESOURCE_ROOT =
            "assets/shadowedhearts/shaders/core/darkball/";
    private static final String SHADER_NAME =
            "dark_ball_surface_splat";
    private static final String RESOLVE_SHADER_NAME =
            "dark_ball_surface_splat_resolve";

    @Test
    void splatShaderAccumulatesBeforeMaterialDecode()
            throws IOException {
        ClassLoader loader =
                DarkBallSurfaceSplatShaderContractTest.class
                        .getClassLoader();
        String json = resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".json");
        String vertex = resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".vsh");
        String compactVertex = compact(vertex);
        int mainStart = vertex.indexOf("void main()");
        assertTrue(mainStart >= 0);
        String mainVertex = vertex.substring(mainStart);
        String compactMainVertex = compact(mainVertex);
        String fragment = resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".fsh");
        String compactFragment = compact(fragment);
        String resolveJson = resource(loader,
                RESOURCE_ROOT + RESOLVE_SHADER_NAME + ".json");
        String resolveFragment = resource(loader,
                RESOURCE_ROOT + RESOLVE_SHADER_NAME + ".fsh");
        String compactResolve = compact(resolveFragment);

        assertTrue(json.contains("\"vertex\": "
                + "\"shadowedhearts:darkball/" + SHADER_NAME + "\""));
        assertTrue(json.contains("\"fragment\": "
                + "\"shadowedhearts:darkball/" + SHADER_NAME + "\""));
        assertTrue(json.contains("\"UV1\""));
        assertTrue(json.contains("\"name\": \"SplatRadius\""));
        assertTrue(json.contains(
                "\"name\": \"SplatDiagnosticMode\""));
        assertTrue(vertex.contains(
                "uniform float SplatDiagnosticMode;"));
        assertTrue(json.contains(
                "\"name\": \"SiphonTangentLocal\""));
        assertTrue(json.contains("\"name\": \"BoneRouteEnabled\""));
        assertTrue(json.contains("\"name\": \"BoneRoutePathScale\""));
        assertTrue(json.contains("\"name\": \"BoneRouteNode0\""));
        assertTrue(json.contains(
                "\"name\": \"TransportActivationHalfWidth\""));
        assertTrue(json.contains(
                "\"name\": \"TransportFrontSpan\""));
        assertTrue(json.contains(
                "\"name\": \"TransportMinimumCrossSection\""));
        assertTrue(json.contains(
                "\"name\": \"TransportThroatCoordinate\""));
        assertTrue(json.contains(
                "\"name\": \"TransportTerminalCoordinate\""));
        assertTrue(json.contains(
                "\"name\": \"TransportRetirementWidth\""));
        assertTrue(json.contains(
                "\"name\": \"TransportCollarExitCoordinate\""));
        assertTrue(json.contains(
                "\"name\": \"TransportCollarExitWidth\""));
        assertTrue(json.contains("\"dstrgb\": \"one\""),
                "surface samples must add into an intermediate field");
        assertFalse(json.contains("\"geometry\""));
        assertFalse(json.contains("SceneDepthSampler"));
        assertTrue(fragment.contains(
                "if (SplatDiagnosticMode > 1.5)"));
        assertTrue(fragment.contains(
                "max(length(cameraRelativePosition), 0.0001)"),
                "the front-depth replay must write scalar linear distance");
        assertTrue(fragment.contains(
                "if (SplatDiagnosticMode > 0.5)"));
        assertTrue(fragment.contains(
                "diagnosticIdentityColor(splatIdentity)"));

        assertTrue(vertex.contains("in ivec2 UV1;"));
        assertFalse(vertex.contains("gl_VertexID % 4"),
                "Iris must not control billboard corner identity through "
                        + "the indexed-quad vertex id");
        assertTrue(vertex.contains(
                "int cornerIndex = packedAttachmentTAndCorner & 3;"),
                "each explicit triangle vertex must carry its billboard "
                        + "corner in the persistent payload");
        assertTrue(vertex.contains(
                "splatIdentity = gl_VertexID / 6;"),
                "both triangles of each diagnostic splat need one stable "
                        + "identity");
        assertTrue(vertex.contains(
                "const float MAX_SAFE_FOOTPRINT_NDC = 0.35;"));
        assertTrue(vertex.contains(
                "vec2(MAX_SAFE_FOOTPRINT_NDC)"),
                "malformed Iris footprints must retire as a whole before "
                        + "they can become screen-wide slabs");
        assertFalse(mainVertex.contains("compressionScale("),
                "the splat body must not retain the old uniform-looking "
                        + "compression path");
        assertTrue(vertex.contains("releaseRemaining(Color.g)"));
        assertTrue(vertex.contains("SpikeFlowTime"));
        assertTrue(vertex.contains("DeformationFlowTime"));
        assertTrue(vertex.contains("TurbulenceComplexityBlend"));
        assertTrue(vertex.contains(
                "const float BODY_SPIKE_MAX_DISPLACEMENT = 0.245;"));
        assertTrue(vertex.contains(
                "const float BODY_INDENT_MAX_DISPLACEMENT = 0.140;"));
        assertTrue(vertex.contains(
                "BODY_UPLOAD_INSET_THICKNESS_FRACTION = 0.15"));
        assertTrue(vertex.contains(
                "BODY_MAX_TOTAL_INWARD_THICKNESS_FRACTION = 0.40"));
        assertTrue(vertex.contains(
                "BODY_DYNAMIC_INWARD_RESERVE_VOXELS = 0.50"));
        assertTrue(compactMainVertex.contains(
                "floatmaximumDynamicInward=max("
                        + "localThickness*("
                        + "BODY_MAX_TOTAL_INWARD_THICKNESS_FRACTION-"
                        + "BODY_UPLOAD_INSET_THICKNESS_FRACTION)"
                        + "-VoxelSize*BODY_DYNAMIC_INWARD_RESERVE_VOXELS,"
                        + "0.0);"));
        assertTrue(compactMainVertex.contains(
                "floatunconstrainedEdgeDisplacement=max("
                        + "rawEdgeDisplacement,-maximumDynamicInward);"));
        assertTrue(compactMainVertex.contains(
                "floatunconstrainedLiveEdgeDisplacement=max("
                        + "rawLiveEdgeDisplacement,"
                        + "-maximumDynamicInward);"),
                "indent and modal motion must share one thickness-safe "
                        + "inward budget");
        assertTrue(vertex.contains(
                "mix(broadPeak, detailPeak, 0.21)"),
                "neighboring splats need a coherent carrier before fusion");
        assertTrue(vertex.contains(
                "mix(sineEaseIn, quinticEaseIn, 0.21)"),
                "violent peaks still need a broad attached base");
        assertTrue(vertex.contains(
                "float retainedExpansion = uniformExpansion"),
                "indent troughs must be allowed to cancel local expansion");
        assertTrue(compactMainVertex.contains(
                "floattransportSection=saturate(UV0.x);"),
                "every splat must retain its full-precision local release "
                        + "coordinate");
        assertFalse(mainVertex.contains("sweepPositionAndNormal("),
                "the shared mesh spine must not move body splats");
        assertFalse(mainVertex.contains("sampleSweep("),
                "the shared sweep frame must remain outside the live splat "
                        + "path");
        assertTrue(mainVertex.contains("boneRoutePosition("),
                "released surfels should follow their compact parent chain");
        assertTrue(vertex.contains("for (int step = 0; step < 8; step++)"),
                "bone routing needs a fixed Intel-safe parent walk");
        assertTrue(vertex.contains("vec4 boneRouteNode(int nodeIndex)"),
                "eight matrices must decode as thirty-two vec4 nodes");
        assertTrue(vertex.contains(
                "vec3 polarRouteSegmentPosition("),
                "each anatomical segment must interpolate direction and "
                        + "radius independently");
        assertTrue(vertex.contains(
                "float routedBaseRadius ="),
                "the monotone bone carrier must own routed distance");
        assertFalse(vertex.contains(
                "* directRemaining;\n    routedLocal"),
                "guided routes must not inherit the old global radial-shrink "
                        + "envelope");
        assertTrue(vertex.contains("transportActivation"));
        assertTrue(vertex.contains("transportTravel"));
        assertTrue(vertex.contains("transportFrontTarget"));
        assertTrue(vertex.contains("transportedCoordinate"));
        assertTrue(vertex.contains("transportPathProgress"));
        assertTrue(vertex.contains("transportCrossSectionScale"));
        assertTrue(vertex.contains("transportBodyOwnership"));
        assertFalse(vertex.contains("transportCollarOwnership"),
                "body splats must retire by completed route progress; the "
                        + "independent siphon mesh owns the terminal collar");
        assertTrue(compactVertex.contains(
                "clamp(max(ReleaseFront,section),section,terminal)"),
                "the advancing release front must bound the transport "
                        + "target");
        assertTrue(compactVertex.contains(
                "mix(section,frontTarget,"
                        + "saturate(travelProgress))"),
                "travel easing must follow the current front instead of "
                        + "jumping directly to the terminal");
        assertTrue(compactVertex.contains(
                "saturate((transportedSection-section)"
                        + "/max(terminal-section,0.0001))"),
                "narrowing must follow actual normalized path distance");
        assertFalse(vertex.contains(
                "routedRestSweepS + longitudinalAdvance"),
                "routed sections must not outrun the release front");
        assertFalse(mainVertex.contains("localOffset.xy"),
                "shared-spine offsets must remain compatibility-only data");
        assertTrue(vertex.contains(
                "TRANSPORT_DEFORMATION_FADE_START"),
                "turbulence must travel with a released patch until the "
                        + "inlet handoff");
        assertTrue(vertex.contains(
                "TRANSPORT_DEFORMATION_FADE_END = 0.985"),
                "wild edge motion must survive almost to the sink");
        assertTrue(compactVertex.contains(
                "localPathProgress);floatretainedExpansion"),
                "deformation attachment must be driven by local travel, not "
                        + "the old reference-mask remaining value");
        assertTrue(vertex.contains(
                "* localVisibleOwnership"),
                "both current and remaining splat fields must retire "
                        + "locally");
        assertTrue(vertex.contains(
                "gl_Position = vec4(2.0, 2.0, 2.0, 1.0)"),
                "fully retired splats must consume no fragment bandwidth");
        assertFalse(vertex.contains(
                "terminalCoverageAfterThroat"),
                "local ownership replaces the early global body cutoff");
        assertTrue(vertex.contains(
                "FINAL_SAFETY_CUTOFF_START = 1.285"),
                "only a final near-1.30 safety cutoff may remain global");
        assertTrue(vertex.contains(
                "const float DIRECT_CURL_START_PROGRESS = 0.54;"));
        assertTrue(vertex.contains(
                "const float DIRECT_CURL_MAX_ANGLE = 0.40;"));
        assertTrue(compactVertex.contains(
                "4.0*windowProgress*(1.0-windowProgress)"),
                "curl must be zero both before its window and at the inlet");
        assertTrue(compactVertex.contains(
                "return1.0-saturate(pathProgress);"),
                "remaining inlet distance must decrease with local progress");
        assertTrue(compactMainVertex.contains(
                "vec3displayedLocal=InletLocal"
                        + "+displayedDirection*displayedRadius;"),
                "confident splats must replace radial transport with their "
                        + "anatomical parent route");
        assertTrue(compactMainVertex.contains(
                "vec3displayedDirection=safeNormalize("
                        + "routedRemainingVector,terminalAxis);"),
                "the validated anatomical route must be the only source of "
                        + "released center direction");
        assertFalse(compactMainVertex.contains(
                        "directRemainingVector"),
                "the old radial center direction must have no shader "
                        + "authority");
        assertTrue(vertex.contains(
                "return vec4(InletLocal, -1.0);"),
                "palette root zero must terminate at the current frozen inlet");
        assertTrue(compactMainVertex.contains(
                "vec3routedBaseLocal=boneRoutePosition("
                        + "Position,directProgress,"),
                "route lengths must start from the same undeformed inset "
                        + "carrier prepared on the CPU");
        assertTrue(compactVertex.contains(
                "smoothstep(1.0-retirementWidth,1.0,"
                        + "saturate(pathProgress))"),
                "body splats must retire only after reaching the end of their "
                        + "anatomical route");
        assertTrue(compactMainVertex.contains(
                "vec3directDisplayedNormal=rotateAroundAxis("
                        + "modalNormal,terminalAxis,curlAngle);"),
                "the bounded modal and curl rotations must preserve the "
                        + "captured local frame");
        assertTrue(compactVertex.contains(
                "outfloatcoherentRoutePhase"),
                "bone routing must provide a shared anatomical curl phase");
        assertTrue(compactVertex.contains(
                "coherentRoutePhase=validRoute"
                        + "?branchCurlPhase:0.0;"),
                "only validated anatomical routes may carry center curl");
        assertFalse(compactMainVertex.contains(
                "sin(UV0.y*TAU)"),
                "individual transport order must not send surfel centers "
                        + "down independent helical paths");
        assertFalse(compactMainVertex.contains(
                "sin(UV0.y*TAU*0.50+1.17)"),
                "the secondary center bend must share the anatomical phase");
        assertTrue(compactMainVertex.contains(
                "sin(coherentRoutePhase*0.50+1.17)"
                        + "*boneRouteWeight"),
                "curl and bend must remain coherent on each validated "
                        + "anatomical route");
        assertTrue(vertex.contains(
                "ROUTE_CORRIDOR_CLAMP_START_PROGRESS = 0.08"));
        assertTrue(vertex.contains(
                "ROUTE_CORRIDOR_END_SPLAT_RADII = 0.38"));
        assertTrue(compactMainVertex.contains(
                "floatrouteCorridorRadius=max("
                        + "VoxelSize*ROUTE_CORRIDOR_MIN_VOXELS,"),
                "the center corridor needs a voxel-safe lower bound");
        assertTrue(compactMainVertex.contains(
                "mix(routeLateralOffset,"
                        + "clampedRouteLateralOffset,"
                        + "routeCorridorAuthority)"),
                "only lateral center warp should be clamped to the tapered "
                        + "anatomical corridor");
        assertTrue(compactMainVertex.indexOf(
                        "routeCorridorAuthority")
                        < compactMainVertex.indexOf(
                        "floatrawHookReach"),
                "center containment must run before the independent "
                        + "connected footprint hook");
        assertTrue(compactVertex.contains(
                "cross(unitAxis,value)*sine"),
                "the Intel-safe Rodrigues rotation must preserve radius");
        assertFalse(mainVertex.contains("displayedLocal = mix("),
                "body splats must never reconstruct the global hook");
        assertTrue(vertex.contains(
                "MAX_SPLAT_TANGENT_STRETCH = 2.00"),
                "the terminal handoff footprint must remain bounded");
        assertTrue(vertex.contains(
                "MAX_BODY_SPLAT_TANGENT_STRETCH = 1.62"),
                "ordinary body splats must not become whole spike needles");
        assertTrue(vertex.contains(
                "MAX_NARROW_SPLAT_TANGENT_STRETCH = 1.42"));
        assertTrue(vertex.contains(
                "MAX_BODY_SPLAT_ANISOTROPY = 4.80"),
                "body covariance singular values must have a bounded ratio");
        assertTrue(vertex.contains(
                "MAX_PRINCIPAL_TANGENT_AUTHORITY = 0.60"),
                "object-space PCA must not override the projected contour");
        assertTrue(vertex.contains(
                "MIN_SPLAT_NORMAL_STRETCH = 0.28"),
                "edge-on splats need a narrow transverse footprint");
        assertTrue(vertex.contains(
                "HIGH_SPIKE_TANGENT_MULTIPLIER = 0.55"));
        assertTrue(vertex.contains(
                "HIGH_SPIKE_NORMAL_MULTIPLIER = 0.78"),
                "spike tips must narrow instead of reinflating the "
                        + "silhouette");
        assertTrue(vertex.contains(
                "spikeBridgeLength"));
        assertTrue(compactMainVertex.contains(
                "vec2contourScreenTangent=safeNormalize2("
                        + "mix(localScreenTangent,screenTangent,"
                        + "normalContourTrust),screenTangent);"),
                "the major support axis must use the neighborhood principal "
                        + "direction before trusting a noisy local contour "
                        + "normal");
        assertTrue(compactMainVertex.contains(
                "principalTangentConfidence"
                        + "*MAX_PRINCIPAL_TANGENT_AUTHORITY"));
        assertTrue(compactMainVertex.contains(
                "mix(contourScreenTangent,"
                        + "siphonScreenTangent,"
                        + "cohesiveHandoff)"),
                "near-inlet splats must rotate toward the real siphon "
                        + "tangent without a second tube");
        assertTrue(vertex.contains(
                "MIN_TRANSVERSE_NEIGHBOR_SCALE = 0.72"));
        assertTrue(vertex.contains(
                "MAX_TRANSVERSE_NEIGHBOR_SCALE = 1.22"));
        assertTrue(compactMainVertex.contains(
                "floatlongitudinalSpacingScale=neighborSpacingScale;"));
        assertTrue(compactMainVertex.contains(
                "floattransverseSpacingScale=clamp("
                        + "neighborSpacingScale,"
                        + "MIN_TRANSVERSE_NEIGHBOR_SCALE,"
                        + "MAX_TRANSVERSE_NEIGHBOR_SCALE);"),
                "local spacing must influence both footprint axes while "
                        + "remaining bounded");
        assertFalse(compactMainVertex.contains(
                "*thicknessRadiusScale*neighborSpacingScale"),
                "neighbor spacing must not isotropically inflate the base "
                        + "splat radius");
        assertTrue(vertex.contains(
                "FEATURE_TRANSVERSE_THICKNESS_FRACTION = 0.48"));
        assertTrue(compactMainVertex.contains(
                "floattransverseRadius=min("
                        + "baseSplatRadius*ellipseYStretch,"
                        + "featureHalfWidth);"),
                "fine features must cap their transverse footprint using "
                        + "captured local thickness");
        assertTrue(compactMainVertex.contains(
                "floatcollapseFootprintScale=max("
                        + "sqrt(saturate(localCrossSection)),"
                        + "COLLAPSE_FOOTPRINT_SCALE_FLOOR);"),
                "footprints must contract with their transported section");
        assertTrue(compactMainVertex.contains(
                "ellipseYStretch*covarianceAnisotropyLimit"),
                "local covariance shaping must clamp unsupported major-axis "
                        + "stretch");
        assertTrue(compactMainVertex.contains(
                "floatrobustFeatureSupport=featureSupport*featureSupport;"));
        assertTrue(compactMainVertex.contains(
                "footprintViolence*=footprintViolence;"),
                "long hooks must ramp later than center deformation");
        assertTrue(compactMainVertex.contains(
                "smoothstep(0.20,0.78,TurbulenceBlend)"),
                "footprint violence must use the declared amplitude uniform");
        assertFalse(vertex.contains("TurbulenceAmplitudeBlend"),
                "the manifest-side amplitude name is not a splat-shader "
                        + "uniform");
        assertTrue(compactMainVertex.contains(
                "*mix(0.08,1.0,robustFeatureSupport)"),
                "unsupported hook tips must be strongly attenuated");
        assertTrue(vertex.contains("decodeTangentFrameMetadata"));
        assertFalse(vertex.contains("int packed ="),
                "packed is a reserved GLSL storage qualifier and cannot be "
                        + "used as a local identifier");
        assertTrue(compactMainVertex.contains(
                "principalTangentConfidence"),
                "the neighborhood principal direction must survive packing");
        assertTrue(compactMainVertex.contains(
                "*mix(1.0,1.20,auraStrength*liveInwardField"
                        + "*attachment*(1.0-smoothstep("
                        + "0.0,0.10,directProgress)))"),
                "an inward trough must gain bounded pre-transport support "
                        + "instead of narrowing into a coverage gap");
        assertTrue(vertex.contains(
                "SPLAT_HANDOFF_LONGITUDINAL_STRETCH = 2.00"));
        assertTrue(vertex.contains(
                "SPLAT_HANDOFF_TRANSVERSE_SCALE = 0.48"));
        assertTrue(vertex.contains(
                "KELVINLET_MAX_PINCH = 0.18"));
        assertTrue(vertex.contains(
                "MODAL_TWIST_MAX_ANGLE = 0.16"));
        assertTrue(compactMainVertex.contains(
                "Position,UV0.y,SpikeFlowTime"),
                "the monotonic sink center must retain a frozen deformation "
                        + "carrier");
        assertTrue(compactMainVertex.contains(
                "Position,UV0.y,DeformationFlowTime"),
                "spike and indent presentation must continue moving during "
                        + "collapse");
        assertTrue(vertex.contains("analyticalBoundaryCurl"));
        assertTrue(vertex.contains("analyticalVortex"));
        assertTrue(vertex.contains("hookStrength"));
        assertTrue(vertex.contains("hookReach"));
        assertTrue(vertex.contains("hookBend"));
        assertTrue(compactMainVertex.contains(
                "floathookCarrier=smoothstep("
                        + "0.46,0.82,liveOutwardField);"
                        + "hookCarrier*=hookCarrier;"),
                "large crescents must be restricted to coherent outward "
                        + "peaks");
        assertFalse(compactMainVertex.contains(
                "floathookCarrier=mix("),
                "every silhouette splat must not receive a minimum hook "
                        + "contribution");
        assertTrue(compactMainVertex.contains(
                "+DeformationFlowTime*0.32"),
                "modal twist must remain live during collapse");
        assertTrue(vertex.contains("animatedEdgeShift"));
        assertTrue(compactMainVertex.contains(
                "if(SplatDiagnosticMode>1.5)"),
                "the isolated depth replay must orient each footprint plane");
        assertTrue(compactMainVertex.contains(
                "viewPosition.z+=clamp(planeDepthOffset,"
                        + "-diagnosticPlaneDepthLimit,"
                        + "diagnosticPlaneDepthLimit);"),
                "oriented surfel depth must remain bounded");
        assertTrue(compactMainVertex.contains(
                "cameraRelativePosition=SplatDiagnosticMode>1.5"
                        + "?viewPosition.xyz:cameraRelativeCenter;"),
                "only the depth replay may move the scalar depth payload "
                        + "off the surfel center");
        assertTrue(vertex.contains("liveInwardField"));
        assertTrue(compactMainVertex.contains(
                "floatinwardAnimationLimit=baseSplatRadius"
                        + "*ellipseYStretch*0.75;"));
        assertTrue(compactMainVertex.contains(
                "animatedEdgeShift=clamp(animatedEdgeDelta,"
                        + "-inwardAnimationLimit,"
                        + "min(baseSplatRadius*0.85,"
                        + "featureHalfWidth*0.90));"),
                "temporal motion must not outrun either the footprint or "
                        + "the captured feature width");
        assertTrue(vertex.contains(
                "decodePackedRange("));
        assertTrue(vertex.contains(
                "float areaCompensation = clamp("));
        assertTrue(vertex.contains(
                "float compressionDensityFade = mix("));
        assertFalse(mainVertex.contains("float covarianceBlend"));
        assertFalse(mainVertex.contains("float sinkBlend"));
        assertFalse(mainVertex.contains("float rawDeterminant"),
                "the shared-spine covariance eigensystem must not execute");
        assertTrue(compactVertex.contains(
                "EffectFade*localVisibleOwnership"),
                "current coverage must retain only local retirement");
        assertTrue(compactVertex.contains(
                "remaining*EffectFade*localVisibleOwnership"),
                "undeformed coverage must use the same local retirement");
        assertTrue(vertex.contains(
                "DEFORMED_OWNERSHIP_MIN_DISPLACEMENT_VOXELS = 0.30"),
                "mask bypass must require a material world-space "
                        + "displacement");
        int deformationTagStart = mainVertex.indexOf(
                "float positiveShapedDisplacement");
        assertTrue(deformationTagStart >= 0,
                "the contour-authority tag must remove uniform expansion "
                        + "from shaped displacement");
        String deformationTagAndDiagnostics =
                mainVertex.substring(deformationTagStart);
        int amplitudeDiagnosticStart =
                deformationTagAndDiagnostics.indexOf(
                        "if (SplatDiagnosticMode < -0.5)");
        assertTrue(amplitudeDiagnosticStart >= 0);
        String deformationTag = deformationTagAndDiagnostics.substring(
                0, amplitudeDiagnosticStart);
        String compactDeformationTag = compact(deformationTag);
        assertTrue(compactDeformationTag.contains(
                "floatpositiveShapedDisplacement=max("
                        + "edgeDisplacement-retainedExpansion,0.0);"));
        assertTrue(compactDeformationTag.contains(
                "floattrueDeformationDistance=max("
                        + "positiveShapedDisplacement,"
                        + "max(sheetDeformationDistance,"
                        + "max(max(animatedEdgeShift,0.0),"
                        + "transportDisplacement)));"));
        assertTrue(compactDeformationTag.contains(
                "deformedSurface=step(max(VoxelSize"
                        + "*DEFORMED_OWNERSHIP_MIN_DISPLACEMENT_VOXELS,"
                        + "0.00002),trueDeformationDistance);"));
        assertFalse(deformationTag.contains("directProgress"),
                "dimensionless transport progress must never grant an "
                        + "entire billboard authority outside the exact "
                        + "mask");
        assertFalse(deformationTag.contains("uniformExpansion"),
                "uniform aura padding must remain clipped by the exact "
                        + "mask");
        assertFalse(compactDeformationTag.contains(
                        "max(inwardDisplacement"),
                "inward splats must not gain whole-billboard authority "
                        + "outside the exact mask");
        assertFalse(compactDeformationTag.contains(
                        "max(liveInwardDisplacement"),
                "live inward motion must be repaired at resolve time, not "
                        + "by bypassing contour clipping");
        assertFalse(mainVertex.contains("covarianceWeightScale"));
        assertFalse(vertex.contains("outerProduct"),
                "Intel GLSL 150 uses the scalar covariance path");

        assertTrue(fragment.contains("compactCoverageKernel"));
        assertTrue(fragment.contains(
                "float crescentArc = sin(PI * hookProgress);"),
                "boundary coverage must curve back into a true crescent");
        assertTrue(fragment.contains(
                "float kernel = max(coreKernel, hookKernel);"),
                "the connected hook may add coverage but never remove the "
                        + "hole-safe core");
        assertFalse(fragment.contains("exp("),
                "two connected kernels must stay cheaper than the old "
                        + "exponential Gaussian on integrated GPUs");
        assertTrue(fragment.contains(
                "in float spikeTipStrength;"));
        assertTrue(fragment.contains("in float hookStrength;"));
        assertTrue(fragment.contains(
                "const float HIGH_SPIKE_TIP_WIDTH = 0.50;"));
        assertTrue(fragment.contains(
                "splatCoordinate.x / max(tipWidth, 0.0001)"));
        assertTrue(fragment.contains("float currentWeight"));
        assertTrue(fragment.contains("float remainingWeight"));
        assertTrue(fragment.contains(
                "const float MAX_ACCUMULATION_WEIGHT_SCALE = 4.0;"),
                "the fragment stage must preserve the targeted support "
                        + "encoded by classified thin-sheet surfels");
        assertTrue(fragment.contains("discard;"));
        assertTrue(compactFragment.contains(
                "fragColor=vec4(currentWeight,remainingWeight,"
                        + "currentWeight*inverseDepthMoment,"
                        + "currentWeight*deformedSurface);"),
                "individual splats must write weighted accumulation only");

        assertTrue(resolveJson.contains(
                "\"fragment\": \"shadowedhearts:darkball/"
                        + RESOLVE_SHADER_NAME + "\""));
        assertTrue(resolveJson.contains("\"name\": \"ResolveMode\""));
        assertTrue(resolveJson.contains(
                "\"name\": \"AccumulationGain\""));
        assertTrue(resolveFragment.contains("filteredAccumulation()"));
        assertFalse(resolveFragment.contains("Direction * 2.0"),
                "the fusion pass must not dilate the silhouette by two "
                        + "pixels per axis");
        assertTrue(resolveFragment.contains("ResolveMode == 0"));
        assertTrue(resolveFragment.contains(
                "const float MIN_FUSION_SUPPORT = 0.95;"));
        assertTrue(resolveFragment.contains(
                "const float FULL_FUSION_SUPPORT = 1.50;"));
        assertTrue(resolveFragment.contains(
                "currentCoverage *= fusionSupport;"));
        assertTrue(resolveFragment.contains(
                "remainingCoverage *= fusionSupport;"));
        assertTrue(resolveFragment.contains(
                "inversesqrt(inverseDepthMean) - 1.0"));
        assertTrue(resolveFragment.contains(
                "1.0\n            - exp(-currentWeight * safeGain)"));
        assertTrue(compactResolve.contains(
                "fragColor=vec4(clamp(currentCoverage,0.0,1.0),"
                        + "0.0,clamp(remainingCoverage,0.0,1.0),"
                        + "depthPayload);"),
                "only the resolved field may emit the final material payload");
    }

    @Test
    void thinSheetsShareCentersAndRedirectNormalTurbulence()
            throws IOException {
        ClassLoader loader =
                DarkBallSurfaceSplatShaderContractTest.class
                        .getClassLoader();
        String vertex = resource(
                loader,
                RESOURCE_ROOT + SHADER_NAME + ".vsh");
        String compactVertex = compact(vertex);
        String renderer = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallSurfaceSplatRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(vertex.contains("decodeThinSheetScore()"));
        assertTrue(vertex.contains(
                "THIN_SHEET_NORMAL_THICKNESS_FRACTION"));
        assertTrue(vertex.contains(
                "THIN_SHEET_TANGENTIAL_REDIRECT"));
        assertTrue(compactVertex.contains(
                "sheetSafeEdgeDisplacement=clamp("
                        + "unconstrainedEdgeDisplacement,"
                        + "-thinSheetNormalBudget,"
                        + "thinSheetNormalBudget)"));
        assertTrue(compactVertex.contains(
                "Position+sourceNormal*edgeDisplacement"
                        + "+frozenSheetOffset"));
        assertTrue(compactVertex.contains(
                "*(1.0-thinSheetScore)"),
                "paired sheets must not emit independent boundary hooks");
        assertTrue(vertex.contains(
                "const float THIN_SHEET_FUSION_SUPPORT = 4.0;"),
                "a one-pixel thin-sheet carrier must survive both halves "
                        + "of the separable fusion filter");
        assertTrue(vertex.contains(
                "THIN_SHEET_TRANSVERSE_RADIUS_FRACTION"));
        assertTrue(compactVertex.contains(
                "thinSheetCoverageHalfWidth="
                        + "baseSplatRadius"
                        + "*THIN_SHEET_TRANSVERSE_RADIUS_FRACTION"),
                "flat sheets need in-plane coverage based on sampling "
                        + "radius rather than near-zero physical thickness");
        assertTrue(compactVertex.contains(
                "thinSheetFusionConfidence=smoothstep("
                        + "0.18,0.72,thinSheetScore)"));
        assertTrue(compactVertex.contains(
                "accumulationWeightScale=max("
                        + "regularAccumulationWeight,"
                        + "thinSheetAccumulationFloor)"),
                "only classified thin sheets may bypass the normal "
                        + "multi-splat support requirement");
        assertTrue(renderer.contains(
                "encodeAttachmentAndThinSheetMetadata"));
        assertTrue(renderer.contains(
                "DarkBallSplatThinSheetPlan.stabilize"));
        assertTrue(renderer.contains("samples.thinSheetScore()"));
    }

    @Test
    void rendererUsesOnePersistentQuadPerQualityCappedSample()
            throws IOException {
        String renderer = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallSurfaceSplatRenderer.java"),
                StandardCharsets.UTF_8);
        String capture = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallCaptureVfx.java"),
                StandardCharsets.UTF_8);
        String pipeline = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/render/DensityFboPipeline.java"),
                StandardCharsets.UTF_8);
        String densityFbo = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallDensityFBO.java"),
                StandardCharsets.UTF_8);
        String compact = compact(renderer);

        assertTrue(renderer.contains(
                "new VertexBuffer(VertexBuffer.Usage.STATIC)"));
        assertTrue(renderer.contains(
                "SURFACE_INSET_THICKNESS_FRACTION = 0.15f"),
                "the CPU inset and shader's compositional inward budget "
                        + "must stay synchronized");
        assertTrue(compact.contains(
                "uploadedVertexCount=Math.multiplyExact(sampleCount,6)"));
        assertTrue(compact.contains(
                "uploadedIndexCount=uploadedVertexCount"));
        assertTrue(renderer.contains("VertexFormat.Mode.TRIANGLES"));
        assertTrue(renderer.contains(
                "TRIANGLE_CORNERS = {0, 1, 2, 0, 2, 3}"));
        assertFalse(renderer.contains("emitSdfClippedEncodedSplat"));
        assertTrue(renderer.contains("case LOW -> 1350"));
        assertTrue(renderer.contains("case MEDIUM -> 2200"));
        assertTrue(renderer.contains("case HIGH -> 3200"));
        assertTrue(renderer.contains(
                "MAX_PROJECTED_SPLAT_REACH = 12.75f"),
                "CPU projected reach must cover the widest curved footprint");
        assertTrue(renderer.contains("buildAreaStratifiedSamples"));
        assertTrue(renderer.contains("writeStratifiedDistribution"));
        assertTrue(renderer.contains(
                "BLUE_NOISE_CANDIDATE_MULTIPLIER = 3"));
        assertTrue(renderer.contains("selectBlueNoiseSubset"));
        assertTrue(renderer.contains(
                "compatibleSampleDistanceSquared"));
        assertTrue(renderer.contains("triangleFeatureSignal"));
        assertTrue(renderer.contains(
                "FEATURE_SAMPLE_FRACTION = 0.30f"),
                "a fixed part of the existing budget must be reserved for "
                        + "thin and curved features");
        assertTrue(renderer.contains(
                "DarkBallSplatNeighborhoodPlan.build"));
        assertTrue(renderer.contains(
                "samples.spacingScale()"));
        assertTrue(renderer.contains(
                "samples.tangentAngle()"));
        assertTrue(renderer.contains(
                "samples.tangentConfidence()"));
        assertTrue(renderer.contains(
                "DarkBallSplatBoneRoutePlan.Result boneRoutePlan"));
        assertTrue(renderer.contains(
                "configureBoneRouteShader"));
        assertTrue(renderer.contains(
                "boneRoutePaletteMatrix"));
        assertTrue(renderer.contains(
                "encodeBoneRouteMetadata"));
        assertTrue(renderer.contains(
                "samples.spacingScale()"));
        assertTrue(renderer.contains(
                "GlStateManager.DestFactor.ONE"));
        assertTrue(renderer.contains("RenderSystem.depthMask(false)"));
        assertTrue(renderer.contains("boolean renderFrontDepth(Camera camera)"));
        assertTrue(renderer.contains(
                "setFloat(shader, \"SplatDiagnosticMode\", 2.0f);"));
        assertTrue(renderer.contains("RenderSystem.depthMask(true)"),
                "the isolated pass must let hardware select the nearest "
                        + "oriented surfel");
        assertTrue(capture.contains(
                ".beginSurfaceSplatFrontDepthPass()"));
        assertTrue(capture.contains(
                ".markSurfaceSplatFrontDepthRendered()"));
        assertTrue(pipeline.contains(
                "GL30.GL_R16F"),
                "scalar front depth must not allocate another RGBA16F field");
        assertTrue(pipeline.contains(
                "surfaceSplatFrontDepthTarget"));
        assertTrue(pipeline.contains(
                "surfaceSplatFrontDepthEnabled"),
                "shared FBO users must not allocate the Dark Ball depth "
                        + "attachment");
        assertTrue(densityFbo.contains(
                "setSurfaceSplatFrontDepthEnabled(true)"));
        assertFalse(renderer.contains(
                "evenlyDistributedSourceVertex"),
                "marching-cubes vertex order must not place the surfels");
        assertTrue(renderer.contains("mesh.hasFiniteSplatSamples()"));
        assertFalse(renderer.contains("mesh.isRenderable()"),
                "topology validation must not reject finite splat samples");
        assertTrue(renderer.indexOf("if (!mesh.hasFiniteSplatSamples())")
                        > renderer.indexOf("uploadedMesh == mesh"),
                "the immutable mesh finite-data scan must happen only after "
                        + "the cached-VBO fast path");
    }

    @Test
    void splatShaderIsRegisteredOnBothPlatforms() throws IOException {
        String modShaders = rootSource(
                "common/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ModShaders.java");
        String fabric = rootSource(
                "fabric/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/fabric/ModShadersPlatformImpl.java");
        String neoforge = rootSource(
                "neoforge/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/neoforge/ModShadersPlatformImpl.java");

        assertTrue(modShaders.contains(
                "DARK_BALL_SURFACE_SPLAT"));
        assertTrue(modShaders.contains(
                "DARK_BALL_SURFACE_SPLAT_RESOLVE"));
        assertTrue(fabric.contains(
                "darkball/dark_ball_surface_splat"));
        assertTrue(fabric.contains(
                "darkball/dark_ball_surface_splat_resolve"));
        assertTrue(neoforge.contains(
                "darkball/dark_ball_surface_splat"));
        assertTrue(neoforge.contains(
                "darkball/dark_ball_surface_splat_resolve"));
    }

    @Test
    void everySweepMatrixDeclaresExactlySixteenValues()
            throws IOException {
        String json = resource(
                DarkBallSurfaceSplatShaderContractTest.class.getClassLoader(),
                RESOURCE_ROOT + SHADER_NAME + ".json");
        Matcher matcher = SWEEP_MATRIX_UNIFORM.matcher(json);
        int matrixCount = 0;
        while (matcher.find()) {
            String values = matcher.group(1).trim();
            int valueCount = values.isEmpty()
                    ? 0
                    : values.split("\\s*,\\s*").length;
            assertEquals(16, valueCount,
                    "Minecraft rejects a matrix4x4 uniform unless its JSON "
                            + "default has exactly 16 values");
            String[] components = values.split("\\s*,\\s*");
            for (int component = 0; component < components.length;
                    component++) {
                double expected = component % 5 == 0 ? 1.0 : 0.0;
                assertEquals(expected,
                        Double.parseDouble(components[component]),
                        "disabled/unconfigured sweep nodes must default "
                                + "to identity matrices");
            }
            matrixCount++;
        }
        assertEquals(8, matrixCount,
                "the splat sweep contract requires exactly eight nodes");
    }

    @Test
    void compactBoneRouteMetadataUsesTwoBytesWithoutReplacingCoverage()
            throws IOException {
        for (int corner = 0; corner < 4; corner++) {
            int packedAttachment = DarkBallSurfaceSplatRenderer
                    .encodeAttachmentTAndCorner(0.625f, corner);
            assertEquals(corner, packedAttachment & 3);
            assertEquals(
                    Math.round(0.625f * 63.0f),
                    packedAttachment >>> 2,
                    "the explicit corner must leave six bits for the "
                            + "bone-segment attachment coordinate");
        }

        int packed = Short.toUnsignedInt(
                DarkBallSurfaceSplatRenderer.encodeBoneRouteMetadata(
                        7,
                        0.625f,
                        0.375f));
        int confidence = packed & 255;
        int normalizedPath = (packed >>> 8) & 255;
        assertEquals(Math.round(0.625f * 255.0f), confidence);
        assertEquals(Math.round(0.375f * 255.0f), normalizedPath);
        assertEquals(0, Short.toUnsignedInt(
                DarkBallSurfaceSplatRenderer.encodeBoneRouteMetadata(
                        -1,
                        1.0f,
                        1.0f)));
        int minimumPacked = Short.toUnsignedInt(
                DarkBallSurfaceSplatRenderer.encodeBoneRouteMetadata(
                        1,
                        0.0f,
                        0.0f));
        assertEquals(1, minimumPacked & 255,
                "assigned confidence must survive byte packing");
        assertEquals(2, (minimumPacked >>> 8) & 255,
                "assigned path length must survive byte packing");

        int tangentPacked = Short.toUnsignedInt(
                DarkBallSurfaceSplatRenderer
                        .encodeTangentFrameMetadata(
                                (float) Math.PI * 0.625f,
                                0.8f));
        int encodedAngle = tangentPacked & 4095;
        int encodedConfidence = (tangentPacked >>> 12) & 15;
        assertEquals(
                0.625f,
                encodedAngle / 4095.0f,
                1.0f / 4095.0f);
        assertEquals(
                Math.round(0.8f * 15.0f),
                encodedConfidence);

        String renderer = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallSurfaceSplatRenderer.java"),
                StandardCharsets.UTF_8);
        String compactRenderer = compact(renderer);
        assertTrue(compactRenderer.contains(
                "address+COLOR_OFFSET+2L,"
                        + "(byte)encodedAttachmentNode"));
        assertTrue(compactRenderer.contains(
                "address+COLOR_OFFSET+3L,"
                        + "(byte)encodedAttachmentT"));
        assertTrue(compactRenderer.contains(
                "samples.spacingScale()[sample]"));

        String vertexShader = Files.readString(
                Path.of("src/main/resources/assets/shadowedhearts/shaders/"
                        + "core/darkball/dark_ball_surface_splat.vsh"),
                StandardCharsets.UTF_8);
        String compactVertexShader = compact(vertexShader);
        assertTrue(compactVertexShader.contains(
                "routeWeight=validRoute?1.0:0.0"),
                "validated routes must fully own center transport");
        assertFalse(compactVertexShader.contains(
                        "returnmix(sourcePosition,InletLocal,"),
                "invalid metadata must never select direct inlet motion");
        assertTrue(compactVertexShader.contains(
                "if(boneRouteWeight<=0.5"
                        + "&&directProgress>0.0001)"),
                "every released surfel without a validated route must retire");
        assertTrue(compactVertexShader.contains(
                        "if(nextNode==0)"));
        assertTrue(compactVertexShader.contains(
                        "terminalVisibility=1.0-smoothstep("
                                + "0.0,0.82,terminalProgress)"),
                "the final anatomical node must fade in place instead of "
                        + "traversing the inlet chord");
        assertTrue(compactVertexShader.contains(
                        "returnsegmentStart;"));
        assertFalse(compactVertexShader.contains(
                        "mix(safeNormalize(directRemainingVector,"));
        assertFalse(compactVertexShader.contains(
                        "floatdisplayedRadius=min(mix("),
                "direct center transport must not retain blending authority");
        assertFalse(vertexShader.contains("principalTangentView"),
                "the retirement branch must only initialize declared "
                        + "surface-splat varyings");
        assertFalse(vertexShader.contains("principalTangentStrength"));
        assertFalse(vertexShader.contains("thinSheetCoverageFloor"));
        assertFalse(vertexShader.contains("thinSheetPatchPhase"));
        assertFalse(compactVertexShader.contains(
                "routeWeight=0.0;initialTangent"),
                "the GPU monotonic guard must clamp rather than switch "
                        + "transport families");
        assertTrue(compactRenderer.contains(
                "samples.coherence()[sample]"));
        assertTrue(compactRenderer.contains(
                "samples.tangentAngle()[sample]"));
        assertTrue(compactRenderer.contains(
                "samples.tangentConfidence()[sample]"));
    }

    @Test
    void everyTransportUniformIsOneScalarMatchingTheJavaDefaults()
            throws IOException {
        String json = resource(
                DarkBallSurfaceSplatShaderContractTest.class.getClassLoader(),
                RESOURCE_ROOT + SHADER_NAME + ".json");
        Matcher matcher = TRANSPORT_SCALAR_UNIFORM.matcher(json);
        DarkBallSplatTransportMath.Parameters defaults =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        int scalarCount = 0;
        while (matcher.find()) {
            String[] values = matcher.group(2).trim()
                    .split("\\s*,\\s*");
            assertEquals(1, values.length,
                    "transport uniforms must declare exactly one value");
            float actual = Float.parseFloat(values[0]);
            float expected = switch (matcher.group(1)) {
                case "TransportActivationHalfWidth" ->
                        defaults.activationHalfWidth();
                case "TransportFrontSpan" ->
                        defaults.travelFrontSpan();
                case "TransportMinimumCrossSection" ->
                        defaults.minimumCrossSectionScale();
                case "TransportThroatCoordinate" ->
                        defaults.throatCoordinate();
                case "TransportTerminalCoordinate" ->
                        defaults.terminalCoordinate();
                case "TransportRetirementWidth" ->
                        defaults.retirementWidth();
                case "TransportCollarExitCoordinate" ->
                        defaults.collarExitCoordinate();
                case "TransportCollarExitWidth" ->
                        defaults.collarExitWidth();
                default -> throw new AssertionError(
                        "unexpected transport uniform "
                                + matcher.group(1));
            };
            assertEquals(expected, actual, 0.000001f);
            scalarCount++;
        }
        assertEquals(8, scalarCount,
                "all transport parameters need scalar shader defaults");
    }

    @Test
    void boundedResolveRunsBeforeSiphonMaterial() throws IOException {
        String densityFbo = rootSource(
                "common/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallDensityFBO.java");
        String captureVfx = rootSource(
                "common/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallCaptureVfx.java");
        String pipeline = rootSource(
                "common/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/render/DensityFboPipeline.java");

        assertTrue(densityFbo.contains(
                "resolveSurfaceSplatField"));
        assertTrue(densityFbo.contains(
                "PIPELINE.processDensityToTemp"));
        assertTrue(densityFbo.contains(
                "PIPELINE.processTempToDensity"));
        assertTrue(pipeline.contains(
                "clearColorAttachmentUnscissored"));
        assertTrue(densityFbo.contains("finally {"));
        assertTrue(densityFbo.contains(
                "PIPELINE.resumeDensityPass();"));
        assertTrue(captureVfx.contains(
                "depthRestored = !depthWriteSubmitted"));
        assertFalse(captureVfx.contains(
                "directDrawSubmitted = bodyRendered"),
                "depth-read-only body splats must not force a depth blit");
        assertTrue(captureVfx.contains(
                "resetDensityPassAfterFailedDirectDraw"));
        assertTrue(captureVfx.contains(
                "SURFACE_SPLAT_MAX_OUTWARD_TURBULENCE = 0.27f"),
                "sweep bounds must cover expansion plus the maximum spike");

        int resolve = captureVfx.indexOf(
                ".resolveSurfaceSplatField(bodyBounds)");
        int siphon = captureVfx.indexOf(
                "buildSiphonSurfaceMesh(siphonProgress)",
                Math.max(resolve, 0));
        assertTrue(resolve >= 0);
        assertTrue(siphon > resolve,
                "body accumulation must be decoded before siphon material");
    }

    @Test
    void malformedOrNearEyeFootprintsRetireBeforeAccumulation()
            throws IOException {
        ClassLoader loader =
                DarkBallSurfaceSplatShaderContractTest.class
                        .getClassLoader();
        String vertex = compact(resource(loader,
                RESOURCE_ROOT + SHADER_NAME + ".vsh"));

        int centerProjection = vertex.indexOf(
                "vec4centerClipPosition=ProjMat*viewPosition;");
        int centerGuard = vertex.indexOf(
                "if(invalidCenterProjection)", centerProjection);
        int cornerSelection = vertex.indexOf(
                "intcornerIndex=packedAttachmentTAndCorner&3;",
                centerGuard);
        int completeFootprintLoop = vertex.indexOf(
                "for(intfootprintCornerIndex=0;"
                        + "footprintCornerIndex<4;"
                        + "footprintCornerIndex++)", cornerSelection);
        int completeFootprintGuard = vertex.indexOf(
                "if(invalidExpandedFootprint)", completeFootprintLoop);
        int cornerSpecificDomain = vertex.indexOf(
                "vec2hookDomainCoordinate=vec2(",
                completeFootprintGuard);
        int expansion = vertex.indexOf(
                "viewPosition.xy+=splatOffset;", cornerSpecificDomain);
        int finalPosition = vertex.indexOf(
                "gl_Position=ProjMat*viewPosition;", expansion);
        int accumulation = vertex.indexOf(
                "bodyCoverage=saturate(", finalPosition);

        assertTrue(centerProjection >= 0);
        assertTrue(centerGuard > centerProjection);
        assertTrue(cornerSelection > centerGuard,
                "the center eye-plane guard must run before corner"
                        + " selection");
        assertTrue(completeFootprintLoop > cornerSelection);
        assertTrue(completeFootprintGuard > completeFootprintLoop,
                "all four candidate corners must be validated before the"
                        + " shared whole-splat rejection branch");
        assertTrue(cornerSpecificDomain > completeFootprintGuard);
        assertTrue(expansion > cornerSpecificDomain,
                "the shared footprint guard must run before any"
                        + " invocation-specific billboard expansion");
        assertTrue(finalPosition > expansion);
        assertTrue(accumulation > finalPosition,
                "an invalid expanded footprint must retire before it can"
                        + " contribute weighted material");

        String completeFootprintValidation = vertex.substring(
                completeFootprintLoop, completeFootprintGuard);
        assertTrue(completeFootprintValidation.contains(
                "vec2footprintCorner=vec2("));
        assertFalse(completeFootprintValidation.contains("corner.x"),
                "whole-splat validation must not depend on the current"
                        + " invocation corner");
        assertFalse(completeFootprintValidation.contains("corner.y"),
                "whole-splat validation must not depend on the current"
                        + " invocation corner");

        String afterCornerExpansion = vertex.substring(
                expansion, finalPosition);
        assertFalse(afterCornerExpansion.contains(
                        "gl_Position=vec4(2.0,2.0,2.0,1.0)"),
                "a single indexed vertex must never be retired after"
                        + " corner-specific expansion");
        assertFalse(afterCornerExpansion.contains("return;"),
                "a single indexed vertex must never return after"
                        + " corner-specific expansion");
        assertFalse(vertex.contains("invalidExpandedProjection"),
                "the former per-corner rejection path must stay removed");
        assertTrue(vertex.contains("centerClipW<=0.0001"));
        assertTrue(vertex.contains("footprintProjectedW<=0.0001"));
    }

    private static String resource(ClassLoader loader, String path)
            throws IOException {
        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertNotNull(stream, "missing splat shader resource " + path);
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static String rootSource(String source)
            throws IOException {
        return Files.readString(
                Path.of("..").resolve(source),
                StandardCharsets.UTF_8);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
