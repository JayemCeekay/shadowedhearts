#version 150

uniform sampler2D Sampler0;
uniform sampler2D SceneDepthSampler;
uniform sampler2D MaskSampler;
uniform sampler2D ProxyFrontSampler;
uniform mat4 InvProjMat;
uniform float TurbulenceBlend;
uniform float SignedBodyAuthority;
uniform float SiphonProgress;
uniform float DeformationTime;
uniform vec2 ProjectedUp;
uniform vec4 BodyUvBounds;
uniform vec2 BallUv;
uniform float SurfaceDepthScale;
uniform int SceneDepthAvailable;
uniform int ProxyFrontAvailable;
uniform int BodyVolumeAvailable;
uniform int DeformationDebugMode;
uniform int EdgeTonguesEnabled;

in vec2 texCoord0;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const float AURA_DEPTH_TAG_BIAS = 2.0;
// The exact captured mask owns the undeformed formation handoff. Once the
// signed body deformation is established, the raymarched R surface owns the
// contour so the captured mask/B projection cannot fill inward cuts back in.
// B may repair only an isolated unresolved body texel surrounded by resolved
// first hits. It must never act as a second, full-size visible silhouette.
const float FALLBACK_BODY_THRESHOLD = 0.055;
const float FALLBACK_REQUIRED_SUPPORT = 2.5;
// The shared deformation clock is intentionally accelerated for the volume
// simulation. Edge tongues need real capture seconds or their lifecycle and
// curl become several times faster than the body motion.
const float EDGE_TIME_TO_SECONDS = 0.3125;
const int PRIMARY_EMITTER_COUNT = 4;
const int ACCENT_EMITTER_COUNT = 3;
const int SEARCH_DISTANCE_COUNT = 12;
const int BOUNDARY_REFINEMENT_STEPS = 3;

bool uvInBounds(vec2 uv) {
    return uv.x >= 0.0 && uv.y >= 0.0
            && uv.x < 1.0 && uv.y < 1.0;
}

ivec2 boundedTexelCoord(vec2 uv, ivec2 extent) {
    return clamp(ivec2(floor(uv * vec2(extent))), ivec2(0),
            max(extent - ivec2(1), ivec2(0)));
}

vec2 texelCenterUv(ivec2 texelCoord, ivec2 extent) {
    return (vec2(texelCoord) + vec2(0.5)) / max(vec2(extent), vec2(1.0));
}

vec4 rawMaterialAt(vec2 uv, ivec2 extent) {
    if (!uvInBounds(uv)) {
        return vec4(0.0);
    }
    return texelFetch(Sampler0, boundedTexelCoord(uv, extent), 0);
}

float textureMaskAt(vec2 uv) {
    if (!uvInBounds(uv)) {
        return 0.0;
    }
    return smoothstep(0.015, 0.12,
            texture(MaskSampler, clamp(uv, vec2(0.0), vec2(1.0))).a);
}

float bodyStorageCoverage(float encodedStorage) {
    // Values above the signed body-storage range carry a siphon-only radius.
    return encodedStorage > 1.5 ? 0.0 : encodedStorage;
}

// The captured texture-alpha mask is the exact formation/core reference. |B|
// supplies its remaining/depleted mass; negative B additionally marks a
// confident intentional carve and positive B remains eligible for bounded
// unresolved-hit recovery. Active signed deformation is resolved separately
// below so neither source becomes an unconditional union that would erase an
// inward displacement. The sign/range of A explicitly identifies a genuine
// deformed first hit while preserving depth.
float exactCoreCoverage(vec4 rawMaterial, vec2 uv) {
    // The voxel projection is storage only. This exact mask is mandatory during
    // initial formation and remains the texture-aware reference thereafter;
    // resolvedMaterialAt hands final contour authority to deformed R instead of
    // exposing B as a swollen body. Only after the siphon starts may |B|'s
    // remaining-mass projection deplete the reference mask.
    float depletionAuthority = BodyVolumeAvailable != 0
            ? smoothstep(0.015, 0.16, clamp(SiphonProgress, 0.0, 1.0))
            : 0.0;
    float storedBodyCoverage = bodyStorageCoverage(rawMaterial.b);
    float remainingCoverage = mix(1.0, abs(storedBodyCoverage),
            depletionAuthority);
    return remainingCoverage * textureMaskAt(uv);
}

float exactCoreAt(vec2 uv, ivec2 extent) {
    return exactCoreCoverage(rawMaterialAt(uv, extent), uv);
}

float liveCoreSeed(float coverage) {
    return step(0.055, coverage);
}

vec2 safeNormalize2(vec2 value, vec2 fallback) {
    float lengthSquared = dot(value, value);
    if (lengthSquared > 0.000001) {
        return value * inversesqrt(lengthSquared);
    }
    float fallbackLengthSquared = dot(fallback, fallback);
    return fallbackLengthSquared > 0.000001
            ? fallback * inversesqrt(fallbackLengthSquared)
            : vec2(0.0, 1.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    vec2 blend = local * local * (3.0 - 2.0 * local);
    float a = hash12(cell);
    float b = hash12(cell + vec2(1.0, 0.0));
    float c = hash12(cell + vec2(0.0, 1.0));
    float d = hash12(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
}

vec3 projectionEyeView() {
    vec4 eye = InvProjMat * vec4(0.0, 0.0, 1.0, 0.0);
    return abs(eye.w) > 0.000001 ? eye.xyz / eye.w : vec3(0.0);
}

float deviceDepthToRayDistance(vec2 uv, float deviceDepth) {
    vec4 view = InvProjMat * vec4(uv * 2.0 - 1.0,
            deviceDepth * 2.0 - 1.0, 1.0);
    view.xyz *= abs(view.w) > 0.000001 ? 1.0 / view.w : 1.0;
    return length(view.xyz - projectionEyeView());
}

float sceneRayDistance(vec2 uv, ivec2 extent) {
    if (SceneDepthAvailable == 0 || !uvInBounds(uv)) {
        return 100000.0;
    }
    ivec2 coord = boundedTexelCoord(uv, extent);
    float deviceDepth = texelFetch(SceneDepthSampler, coord, 0).r;
    if (deviceDepth >= 0.999999) {
        return 100000.0;
    }
    return deviceDepthToRayDistance(texelCenterUv(coord, extent),
            deviceDepth);
}

float resolvedBodyDepth(vec2 uv, ivec2 extent, vec4 rawMaterial,
        float exactCoverage) {
    float rawDepthValid = exactCoverage > 0.002
            && rawMaterial.r > 0.002 && rawMaterial.a > 0.0001
            ? 1.0 : 0.0;
    if (ProxyFrontAvailable != 0 && uvInBounds(uv)) {
        ivec2 coord = boundedTexelCoord(uv, extent);
        vec4 proxyFront = texelFetch(ProxyFrontSampler, coord, 0);
        if (proxyFront.a > 0.01 && proxyFront.r < 0.999999) {
            float proxyDepth = deviceDepthToRayDistance(
                    texelCenterUv(coord, extent), proxyFront.r);
            // This function serves only the untagged exact-core/depletion
            // branch. Its rasterized front surface is the earliest acceptable
            // depth; a valid, deeper depleted-SDF hit may move it inward.
            // Signed deformed hits bypass this clamp through their explicit
            // negative-A depth tag.
            if (rawDepthValid > 0.5) {
                float depletionGate = smoothstep(0.01, 0.12,
                        clamp(SiphonProgress, 0.0, 1.0));
                return mix(proxyDepth, max(proxyDepth, rawMaterial.a),
                        depletionGate);
            }
            return proxyDepth;
        }
    }
    return rawDepthValid > 0.5 ? rawMaterial.a : -1.0;
}

bool deformedSurfaceTagged(vec4 rawMaterial) {
    // This tag means "the resolved first surface differs from the exact core",
    // not merely "the hit lies outside it". Signed outward spikes and inward
    // recesses therefore share this depth-preserving ownership path.
    return rawMaterial.a < -AURA_DEPTH_TAG_BIAS - 0.0001;
}

float decodedDeformedDepth(vec4 rawMaterial) {
    return deformedSurfaceTagged(rawMaterial)
            ? max(-rawMaterial.a - AURA_DEPTH_TAG_BIAS, 0.0001)
            : -1.0;
}

float deformedAuthority() {
    if (BodyVolumeAvailable == 0) {
        return 0.0;
    }
    // This is deliberately independent from TurbulenceBlend. Turbulence has
    // already begun while the frozen model is crossfading; allowing it to own
    // the contour here would replace the opaque exact mask with an incompletely
    // formed raymarched body and create a whole-silhouette alpha valley.
    return clamp(SignedBodyAuthority, 0.0, 1.0);
}

float resolvedBodyNeighbor(vec2 uv, ivec2 extent,
        out float neighborCoverage) {
    vec4 neighbor = rawMaterialAt(uv, extent);
    neighborCoverage = neighbor.r;
    bool depthResolved = neighbor.a > 0.0001
            || deformedSurfaceTagged(neighbor);
    return neighbor.r >= FALLBACK_BODY_THRESHOLD && depthResolved
            ? 1.0 : 0.0;
}

float boundedUnresolvedCoreFallback(vec4 rawMaterial, vec2 uv,
        ivec2 extent, float exactCoverage) {
    // Only repair a B-backed, texture-valid texel for which the volume pass
    // failed to resolve any body depth. A tagged deformed hit is authoritative,
    // and an actual R hit needs no fallback.
    float storedBodyCoverage = bodyStorageCoverage(rawMaterial.b);
    bool intentionalCarve = storedBodyCoverage < -0.002;
    bool unresolved = rawMaterial.a < -0.0001
            && !deformedSurfaceTagged(rawMaterial);
    if (intentionalCarve
            || !unresolved
            || rawMaterial.r >= FALLBACK_BODY_THRESHOLD
            || storedBodyCoverage < FALLBACK_BODY_THRESHOLD
            || exactCoverage <= 0.002) {
        return 0.0;
    }

    vec2 texel = 1.0 / max(vec2(extent), vec2(1.0));
    float leftCoverage;
    float rightCoverage;
    float downCoverage;
    float upCoverage;
    float leftResolved = resolvedBodyNeighbor(uv - vec2(texel.x, 0.0),
            extent, leftCoverage);
    float rightResolved = resolvedBodyNeighbor(uv + vec2(texel.x, 0.0),
            extent, rightCoverage);
    float downResolved = resolvedBodyNeighbor(uv - vec2(0.0, texel.y),
            extent, downCoverage);
    float upResolved = resolvedBodyNeighbor(uv + vec2(0.0, texel.y),
            extent, upCoverage);
    float support = leftResolved + rightResolved
            + downResolved + upResolved;
    if (support < FALLBACK_REQUIRED_SUPPORT) {
        return 0.0;
    }

    // Three-of-four cardinal support closes a single raymarch pinhole while
    // leaving a coherent inward contour (which lacks support on its exterior
    // side) under R's control.
    float supportedCoverage = (leftCoverage * leftResolved
            + rightCoverage * rightResolved
            + downCoverage * downResolved
            + upCoverage * upResolved) / max(support, 1.0);
    return min(exactCoverage, supportedCoverage);
}

vec4 resolvedMaterialAt(vec2 uv, ivec2 extent) {
    vec4 rawMaterial = rawMaterialAt(uv, extent);
    float exactCoverage = exactCoreCoverage(rawMaterial, uv);
    bool surfaceTagged = deformedSurfaceTagged(rawMaterial);
    float surfaceDepth = decodedDeformedDepth(rawMaterial);

    // Positive A is reserved for exact-core/depletion ownership. Keep that
    // branch clipped by the texture-aware reference; a negative tag explicitly
    // admits the complete signed deformed surface on either side of the source
    // contour. Crucially, R remains a factor inside the original mask, so a
    // zero-R inward cut cannot be repopulated by exactCoverage or B.
    float exactClippedBody = min(exactCoverage, rawMaterial.r);
    float taggedDeformedBody = surfaceTagged ? rawMaterial.r : 0.0;
    float activeBodyCoverage = max(exactClippedBody,
            taggedDeformedBody);
    float fallbackCoverage = boundedUnresolvedCoreFallback(rawMaterial, uv,
            extent, exactCoverage);
    activeBodyCoverage = max(activeBodyCoverage, fallbackCoverage);

    // The dedicated authority remains zero for the complete frozen-model to
    // texture-exact silhouette crossfade. It then releases the contour over
    // the remainder of the expansion ramp; afterward only deformed R (plus the
    // isolated fallback above) controls the visible body contour.
    float deformationBlend = deformedAuthority();
    float resolvedCoverage = mix(exactCoverage, activeBodyCoverage,
            deformationBlend);

    bool deformedOwns = surfaceTagged
            && taggedDeformedBody > 0.002;
    float resolvedDepth = deformedOwns
            ? surfaceDepth
            : (resolvedCoverage > 0.002
                    ? resolvedBodyDepth(uv, extent, rawMaterial,
                            max(exactCoverage, fallbackCoverage))
                    : rawMaterial.a);
    return vec4(resolvedCoverage, rawMaterial.g, rawMaterial.b, resolvedDepth);
}

float projectedBodyHeightPixels(vec2 extentPixels, vec2 up) {
    vec2 boundsMinimum = BodyUvBounds.xy * extentPixels;
    vec2 boundsMaximum = BodyUvBounds.zw * extentPixels;
    vec2 boundsHalfExtent = max((boundsMaximum - boundsMinimum) * 0.5,
            vec2(1.0));
    return max(2.0 * dot(abs(up), boundsHalfExtent), 2.0);
}

float projectedBodyWidthPixels(vec2 extentPixels, vec2 up) {
    vec2 boundsMinimum = BodyUvBounds.xy * extentPixels;
    vec2 boundsMaximum = BodyUvBounds.zw * extentPixels;
    vec2 boundsHalfExtent = max((boundsMaximum - boundsMinimum) * 0.5,
            vec2(1.0));
    vec2 side = vec2(up.y, -up.x);
    return max(2.0 * dot(abs(side), boundsHalfExtent), 2.0);
}

float pointedTongueSdf(float alongAxis, float acrossAxis,
        float reach, float baseHalfWidth, float tipHalfWidth,
        float curlSeed, float bendDirection, float tongueTime,
        out float curlCenter, out float currentHalfWidth) {
    float safeReach = max(reach, 0.5);
    float progress = clamp(alongAxis / safeReach, 0.0, 1.0);
    // A true teardrop profile: hold a broad shoulder through the first quarter,
    // then converge smoothly. Width noise is deliberately absent; it previously
    // multiplied away the lobe interior into stippled purple fragments.
    float taper = smoothstep(0.22, 1.0, progress);
    taper = taper * taper * (3.0 - 2.0 * taper);
    float shoulderWindow = sin(PI * clamp(progress / 0.64, 0.0, 1.0));
    float shoulder = 1.0 + shoulderWindow * (1.0 - taper) * 0.12;
    currentHalfWidth = mix(baseHalfWidth, tipHalfWidth, taper) * shoulder;

    // One slow body bend plus a stronger late hook produces a readable flame
    // lick. Low-frequency centerline drift adds life without changing identity.
    float slowBend = sin(tongueTime * 0.58 + curlSeed * TAU);
    float bodyBend = slowBend * baseHalfWidth * 0.28
            * pow(progress, 1.28);
    float hookWindow = pow(smoothstep(0.56, 1.0, progress), 2.0);
    float hookPulse = 0.72 + 0.28
            * sin(tongueTime * 0.36 + curlSeed * 11.7);
    float tipHook = bendDirection * baseHalfWidth * 0.56
            * hookWindow * hookPulse;
    float flow = valueNoise(vec2(curlSeed * 31.7 + progress * 1.55,
            tongueTime * 0.20)) - 0.5;
    curlCenter = bodyBend + tipHook
            + flow * baseHalfWidth * 0.10 * sin(progress * PI);
    float lateralDistance = currentHalfWidth
            - abs(acrossAxis - curlCenter);
    // Sink the broad foot into the exact core so tongues grow from the model
    // edge instead of reading as detached quills or short purple bars.
    float rootDistance = alongAxis + max(1.75, baseHalfWidth * 0.74);
    float tipDistance = reach - alongAxis;
    return min(min(lateralDistance, rootDistance), tipDistance);
}

float evaluateTongueCandidate(vec2 targetUv, vec2 inwardDirection,
        float candidateOutsideDistance, float candidateInsideDistance,
        ivec2 extent, vec2 extentPixels, vec2 texel,
        vec2 projectedUpPixels, float projectedHeightPixels,
        float projectedWidthPixels, float auraStrength, float siphon,
        out float candidateDepth) {
    candidateDepth = -1.0;
    float outsideDistance = candidateOutsideDistance;
    float insideDistance = candidateInsideDistance;
    for (int refinement = 0;
            refinement < BOUNDARY_REFINEMENT_STEPS; refinement++) {
        float middleDistance = (outsideDistance + insideDistance) * 0.5;
        vec2 middleUv = targetUv + inwardDirection
                * middleDistance * texel;
        if (liveCoreSeed(exactCoreAt(middleUv, extent)) > 0.5) {
            insideDistance = middleDistance;
        } else {
            outsideDistance = middleDistance;
        }
    }

    vec2 rootUv = targetUv + inwardDirection * insideDistance * texel;
    vec4 rootMaterial = resolvedMaterialAt(rootUv, extent);
    if (liveCoreSeed(rootMaterial.r) <= 0.5 || rootMaterial.a <= 0.0) {
        return 0.0;
    }

    // A wider derivative is much less sensitive to one-texel voxel/mask
    // changes, keeping the flame axis stable while the contour animates.
    vec2 gradientStep = texel * 2.25;
    float coverageLeft = exactCoreAt(rootUv
            - vec2(gradientStep.x, 0.0), extent);
    float coverageRight = exactCoreAt(rootUv
            + vec2(gradientStep.x, 0.0), extent);
    float coverageDown = exactCoreAt(rootUv
            - vec2(0.0, gradientStep.y), extent);
    float coverageUp = exactCoreAt(rootUv
            + vec2(0.0, gradientStep.y), extent);
    vec2 inwardGradient = vec2(coverageRight - coverageLeft,
            coverageUp - coverageDown);
    vec2 outward = safeNormalize2(-inwardGradient, -inwardDirection);
    vec2 rootPixels = rootUv * extentPixels;
    vec2 screenSide = vec2(projectedUpPixels.y, -projectedUpPixels.x);
    vec2 boundsCenterPixels = (BodyUvBounds.xy + BodyUvBounds.zw)
            * extentPixels * 0.5;
    vec2 rootFromBoundsCenter = rootPixels - boundsCenterPixels;
    float normalizedSide = dot(rootFromBoundsCenter, screenSide)
            / max(projectedWidthPixels * 0.5, 1.0);
    float normalizedUp = dot(rootFromBoundsCenter, projectedUpPixels)
            / max(projectedHeightPixels * 0.5, 1.0);
    float contourBranch = normalizedSide < 0.0 ? -1.0 : 1.0;
    // Camera-facing contour coordinate: bottom=0, side=0.5, top=1 on each
    // branch. Fixed emitters travel along this path instead of every boundary
    // sample inventing an unrelated lane/tongue.
    float contourPath = clamp(1.0
            - atan(abs(normalizedSide), normalizedUp) / PI, 0.0, 1.0);
    float halfWidth = max(projectedWidthPixels * 0.5, 1.0);
    float halfHeight = max(projectedHeightPixels * 0.5, 1.0);
    float contourLength = PI * sqrt(max(0.5
            * (halfWidth * halfWidth + halfHeight * halfHeight), 1.0));

    // Follow the camera-facing outer contour from bottom -> side -> top. At the
    // underside a world-up axis points straight through the Pokemon and the old
    // outwardness gate suppressed the first half of every emitter lifecycle.
    // The branch tangent keeps that root outside while it rolls upward; a small
    // outward peel gives the lobe room for a broad base, and world-up gradually
    // turns the side/top portion into a flame rather than a radial hedgehog ray.
    float outwardUp = dot(outward, projectedUpPixels);
    float outwardSide = dot(outward, screenSide);
    vec2 risingContourTangent = safeNormalize2(contourBranch
            * (screenSide * -outwardUp
            + projectedUpPixels * outwardSide), projectedUpPixels);
    vec2 contourAxis = safeNormalize2(risingContourTangent * 0.86
            + outward * 0.34 + projectedUpPixels * 0.18,
            risingContourTangent);
    float crownBlend = smoothstep(0.70, 0.92, contourPath);
    vec2 crownAxis = safeNormalize2(projectedUpPixels
            + outward * 0.22, projectedUpPixels);
    vec2 axis = safeNormalize2(mix(contourAxis, crownAxis, crownBlend),
            contourAxis);
    vec2 pullPixels = (BallUv - rootUv) * extentPixels;
    if (dot(pullPixels, pullPixels) > 1.0) {
        vec2 pullDirection = safeNormalize2(pullPixels, axis);
        axis = safeNormalize2(mix(axis, pullDirection, siphon * 0.42)
                + outward * siphon * 0.18, axis);
    }

    vec2 targetDelta = (targetUv - rootUv) * extentPixels;
    vec2 targetDirection = safeNormalize2(targetDelta, outward);
    float targetOutwardness = dot(targetDirection, outward);
    float emptyOutside = 1.0 - smoothstep(0.08, 0.26,
            exactCoreAt(rootUv + outward * texel * 1.6, extent));
    // The target must be exterior to the texture-exact core, but it need not be
    // above its root. Lower-contour tongues initially travel sideways around
    // the silhouette before their continuously moving root reaches the flank.
    if (targetOutwardness < 0.035 || emptyOutside < 0.25) {
        return 0.0;
    }

    float tongueTime = DeformationTime * EDGE_TIME_TO_SECONDS;
    float auraCoverage = smoothstep(0.06, 0.22, auraStrength);
    float branchPhase = contourBranch > 0.0 ? 0.075 : 0.0;
    float bestCoverage = 0.0;
    float bestReach = 1.0;
    float bestWidth = 1.0;
    float bestCenter = 0.0;
    float bestAcross = 0.0;
    float bestAlong = 0.0;
    vec2 flameSide = vec2(axis.y, -axis.x);

    // Four persistent macro emitters per visible contour branch. Their centers
    // travel continuously bottom-to-top and wrap only while outside the body.
    for (int emitter = 0; emitter < PRIMARY_EMITTER_COUNT; emitter++) {
        float ordinal = float(emitter);
        float seed = hash12(vec2(ordinal
                + (contourBranch > 0.0 ? 17.0 : 3.0), 7.13));
        float cycle = fract(tongueTime * 0.22
                + ordinal / float(PRIMARY_EMITTER_COUNT)
                + seed * 0.025 + branchPhase);
        float centerPath = mix(-0.12, 1.12, cycle);
        float contourDelta = (contourPath - centerPath) * contourLength;
        // Reconstruct this pixel relative to the emitter's moving root. The
        // sampled root belongs to the target's radial contour ray, not the
        // emitter itself; advancing back along the rising contour tangent makes
        // every pixel in the lobe share one coherent analytic origin.
        vec2 emitterToTarget = targetDelta
                + risingContourTangent * contourDelta;
        float alongAxis = dot(emitterToTarget, axis);
        float acrossAxis = dot(emitterToTarget, flameSide);
        float targetReach = clamp(projectedHeightPixels
                * mix(0.17, 0.27, seed), 17.0, 124.0);
        float reach = targetReach * mix(1.0, 0.58, siphon) * auraStrength;
        float widthSeed = hash12(vec2(seed * 43.7, 11.0));
        float baseWidth = clamp(targetReach * mix(0.22, 0.28, widthSeed),
                6.0, 32.0) * mix(1.0, 0.76, siphon) * auraStrength;
        float tipWidth = clamp(projectedHeightPixels * 0.0055,
                1.15, 3.2) * auraStrength;
        float bendDirection = hash12(vec2(seed * 71.3, 29.0)) < 0.5
                ? -1.0 : 1.0;
        float curlCenter;
        float currentWidth;
        float lobeSdf = pointedTongueSdf(alongAxis, acrossAxis,
                reach, baseWidth, tipWidth, seed, bendDirection, tongueTime,
                curlCenter, currentWidth);
        float coverage = smoothstep(-0.62, 0.24, lobeSdf)
                * auraCoverage;
        if (coverage > bestCoverage) {
            bestCoverage = coverage;
            bestReach = reach;
            bestWidth = currentWidth;
            bestCenter = curlCenter;
            bestAcross = acrossAxis;
            bestAlong = alongAxis;
        }
    }

    // A smaller subordinate bank retains the sharp-energy accent, but each
    // accent is still a filled lobe and can no longer dominate as a needle.
    for (int emitter = 0; emitter < ACCENT_EMITTER_COUNT; emitter++) {
        float ordinal = float(emitter);
        float seed = hash12(vec2(ordinal
                + (contourBranch > 0.0 ? 53.0 : 37.0), 19.37));
        float cycle = fract(tongueTime * 0.32
                + (ordinal + 0.45) / float(ACCENT_EMITTER_COUNT)
                + seed * 0.025 + branchPhase * 0.63);
        float centerPath = mix(-0.11, 1.11, cycle);
        float contourDelta = (contourPath - centerPath) * contourLength;
        vec2 emitterToTarget = targetDelta
                + risingContourTangent * contourDelta;
        float alongAxis = dot(emitterToTarget, axis);
        float acrossAxis = dot(emitterToTarget, flameSide);
        float targetReach = clamp(projectedHeightPixels
                * mix(0.10, 0.16, seed), 10.0, 76.0);
        float reach = targetReach * mix(1.0, 0.64, siphon) * auraStrength;
        float widthSeed = hash12(vec2(seed * 37.1, 23.0));
        float baseWidth = clamp(targetReach * mix(0.18, 0.23, widthSeed),
                4.0, 19.0) * mix(1.0, 0.72, siphon) * auraStrength;
        float tipWidth = clamp(projectedHeightPixels * 0.0040,
                0.95, 2.4) * auraStrength;
        float bendDirection = hash12(vec2(seed * 61.9, 41.0)) < 0.5
                ? -1.0 : 1.0;
        float curlCenter;
        float currentWidth;
        float lobeSdf = pointedTongueSdf(alongAxis, acrossAxis,
                reach, baseWidth, tipWidth, seed, bendDirection, tongueTime,
                curlCenter, currentWidth);
        float coverage = smoothstep(-0.58, 0.22, lobeSdf)
                * auraCoverage * 0.78;
        if (coverage > bestCoverage) {
            bestCoverage = coverage;
            bestReach = reach;
            bestWidth = currentWidth;
            bestCenter = curlCenter;
            bestAcross = acrossAxis;
            bestAlong = alongAxis;
        }
    }

    float tongueCoverage = bestCoverage * mix(1.0, 0.88, siphon);
    if (tongueCoverage <= 0.002 || bestReach <= 0.5) {
        return 0.0;
    }

    float longitudinal = clamp(bestAlong / max(bestReach, 0.5),
            0.0, 1.0);
    float lateral01 = clamp(abs(bestAcross - bestCenter)
            / max(bestWidth, 0.75), 0.0, 1.0);
    float lateralConvexity = sqrt(max(1.0 - lateral01 * lateral01, 0.0));
    float convexProfile = sin(longitudinal * PI) * lateralConvexity
            * smoothstep(0.10, 0.82, tongueCoverage);
    candidateDepth = max(rootMaterial.a
            - SurfaceDepthScale * 0.0105 * convexProfile, 0.0001);
    return tongueCoverage;
}

void main() {
    ivec2 extent = textureSize(Sampler0, 0);
    vec2 extentPixels = max(vec2(extent), vec2(1.0));
    vec2 texel = 1.0 / extentPixels;
    vec4 rawOriginal = texelFetch(Sampler0,
            boundedTexelCoord(texCoord0, extent), 0);

    if (DeformationDebugMode != 0) {
        fragColor = rawOriginal;
        return;
    }

    // This resolve always runs, including before turbulence starts. It removes
    // broad guard/proximity coverage, preserves the texture-exact core, and
    // admits only depth-resolved 3D aura-shell coverage outside that core.
    vec4 original = resolvedMaterialAt(texCoord0, extent);
    if (EdgeTonguesEnabled == 0) {
        fragColor = original;
        return;
    }
    float auraStrength = smoothstep(0.04, 0.82,
            clamp(TurbulenceBlend, 0.0, 1.0));
    // The legacy screen-space tongue bank is allowed only outside the original
    // texture footprint. After signed inward carving, original.r may be zero
    // inside that footprint; without this guard the legacy pass could mistake
    // the intentional recess for empty exterior and paint it back in.
    if (auraStrength <= 0.001 || original.r >= 0.055
            || textureMaskAt(texCoord0) >= 0.055) {
        fragColor = original;
        return;
    }

    vec2 projectedUpPixels = safeNormalize2(
            ProjectedUp * extentPixels, vec2(0.0, 1.0));
    float projectedHeightPixels = projectedBodyHeightPixels(
            extentPixels, projectedUpPixels);
    float projectedWidthPixels = projectedBodyWidthPixels(
            extentPixels, projectedUpPixels);
    // Keep root lookup fixed while the aura grows. Scaling the search radius
    // moved a destination between unrelated contour roots during formation.
    float maximumReach = clamp(projectedHeightPixels * 0.34,
            20.0, 136.0);
    float boundsMargin = maximumReach + 4.0;
    vec2 boundsMarginUv = vec2(boundsMargin) / extentPixels;
    if (texCoord0.x < BodyUvBounds.x - boundsMarginUv.x
            || texCoord0.y < BodyUvBounds.y - boundsMarginUv.y
            || texCoord0.x > BodyUvBounds.z + boundsMarginUv.x
            || texCoord0.y > BodyUvBounds.w + boundsMarginUv.y) {
        fragColor = original;
        return;
    }

    vec2 screenSide = vec2(projectedUpPixels.y, -projectedUpPixels.x);
    vec2 boundsCenterPixels = (BodyUvBounds.xy + BodyUvBounds.zw)
            * extentPixels * 0.5;
    vec2 targetFromBoundsCenter = texCoord0 * extentPixels
            - boundsCenterPixels;
    // One genuinely center-seeking inverse ray gives neighboring pixels the
    // same root family. Including the target's vertical displacement is
    // essential: an always-downward ray can find side/top roots but points away
    // from the model below its midpoint, making bottom-origin tongues
    // impossible. The old three-ray winner also changed direction per pixel
    // and stamped its orientations into visible comb/fan artifacts.
    vec2 inwardDirection = safeNormalize2(-targetFromBoundsCenter,
            -projectedUpPixels);
    float previousDistance = 0.0;
    bool foundCandidateRoot = false;
    float candidateOutsideDistance = 0.0;
    float candidateInsideDistance = 0.0;
    for (int distanceIndex = 0;
            distanceIndex < SEARCH_DISTANCE_COUNT; distanceIndex++) {
        float linearFraction = float(distanceIndex + 1)
                / float(SEARCH_DISTANCE_COUNT);
        float searchFraction = pow(linearFraction, 1.55);
        float candidateDistance = max(1.5, maximumReach * searchFraction);
        vec2 candidateUv = texCoord0
                + inwardDirection * candidateDistance * texel;
        if (liveCoreSeed(exactCoreAt(candidateUv, extent)) > 0.5) {
            candidateOutsideDistance = previousDistance;
            candidateInsideDistance = candidateDistance;
            foundCandidateRoot = true;
            break;
        }
        previousDistance = candidateDistance;
    }
    if (!foundCandidateRoot) {
        fragColor = original;
        return;
    }

    float siphon = smoothstep(0.02, 0.94,
            clamp(SiphonProgress, 0.0, 1.0));
    float sceneDistance = sceneRayDistance(texCoord0, extent);
    float occlusionBias = max(SurfaceDepthScale * 0.012, 0.025);
    float tongueDepth;
    float tongueCoverage = evaluateTongueCandidate(texCoord0,
            inwardDirection, candidateOutsideDistance,
            candidateInsideDistance, extent, extentPixels, texel,
            projectedUpPixels, projectedHeightPixels,
            projectedWidthPixels, auraStrength, siphon, tongueDepth);
    bool visible = tongueDepth > 0.0
            && tongueDepth <= sceneDistance + occlusionBias;
    if (!visible || tongueCoverage <= 0.002) {
        fragColor = original;
        return;
    }
    bool tongueOwns = tongueCoverage > original.r;
    fragColor = vec4(max(original.r, tongueCoverage), original.g,
            original.b, tongueOwns ? tongueDepth : original.a);
}
