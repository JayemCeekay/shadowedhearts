#version 150

// ===== From VS (camera-relative world-aligned ray direction) =====
in vec3 vRayDirWS;

// ===== Uniforms =====
uniform float uNodes[144];   // node positions in camera-relative world-aligned space
uniform float uNumNodes;
uniform float uTime;
uniform float uTrailMaxDist;
uniform vec3  uCameraPosWS;  // actual camera world position (for noise anchoring)

// Forward view+proj for depth comparison (p is world-aligned, need to project to clip)
uniform mat4  uView;
uniform mat4  uProj;
uniform mat4  uInvProj;
uniform vec2  uScreenSize;

// Depth buffer sampler
uniform sampler2D uDepth;

// === Relative controls (size-invariant, matching fog_cylinder) ===
uniform float uNoiseScaleRel;   // features per radius (e.g., 2.0)
uniform float uScrollSpeedRel;  // radii per second   (e.g., 0.8)

// Emission shaping
uniform float uGlowGamma;      // 0.5
uniform float uBlackPoint;      // 0.0

// Small warp
uniform float uWarpAmp;         // 0.075

// Density-weighted shadow
uniform float uShadowMix;       // 1.75
uniform float uShadowKnee;      // 0.15
uniform float uShadowGamma;     // 1.40
uniform float uShadowDesat;     // 0.25

// Density/absorption
uniform float uDensity;         // base density
uniform float uAbsorption;      // Beer extinction along inDist
uniform float uTubeRadius;      // capsule radius near player
uniform float uTubeRadiusEnd;   // capsule radius at objective (tapered)
uniform float uMaxThickness;    // skin thickness under surface
uniform float uCorePow;         // SDF profile exponent

// Patchiness (height-aware, matching fog_cylinder)
uniform float uPatchScaleRel;   // low-frequency mask scale (per radius)
uniform float uPatchThreshBase; // threshold at base (0..1)
uniform float uPatchThreshTop;  // threshold at top  (0..1)
uniform float uPatchSharpness;  // smoothstep half-width
uniform float uPatchGamma;      // height ramp exponent for patch strength
uniform float uPatchStrengthTop;// max patch strength at top (0..1)

// Height fade controls (matching fog_cylinder)
uniform float uHeightFadeMin;   // residual density at top (0..1)
uniform float uHeightFadePow;   // exponent for height fade

// Limb controls (matching fog_cylinder)
uniform float uLimbSoft;        // 0.22
uniform float uLimbHardness;    // 2.25
uniform float uMinPathNorm;     // 0.18

// Pixel look toggles (matching fog_cylinder)
uniform float uPixelsPerRadius; // 0=off. Try 24.0
uniform float uPosterizeSteps;  // 0=off. Try 3.0
uniform float uDitherAmount;    // 0..1.  Try 1.4

// Colors & fade
uniform vec3  uColorA;
uniform vec3  uColorB;
uniform float uAuraFade;
uniform float uFadeGamma;
uniform vec4  ColorModulator;

// Player exclusion (in camera-relative world-aligned space)
uniform vec3  uPlayerPosCS;
uniform float uNearFadeMin;
uniform float uNearFadeMax;

// Hunt state uniforms
uniform float uTension;       // 0.0 (calm) to 1.0 (maximum tension)
uniform float uTrailQuality;  // 1.0 (clean) to 0.0 (maximum noise/interference)

out vec4 fragColor;

// ===== Tunables =====
#define MAX_STEPS 128
#define FBM_OCTAVES 8
#define MIN_STEP 0.15
#define STEP_SAFETY 0.8

// ===== Helpers (matching fog_cylinder) =====
float saturate(float x){ return clamp(x, 0.0, 1.0); }

float hash31(vec3 p){
    p = fract(p*0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x+p.y)*p.z);
}

float sh_noise3(vec3 p){
    vec3 i = floor(p), f = fract(p);
    vec3 u = f*f*(3.0-2.0*f);
    float n000 = hash31(i+vec3(0, 0, 0));
    float n100 = hash31(i+vec3(1, 0, 0));
    float n010 = hash31(i+vec3(0, 1, 0));
    float n110 = hash31(i+vec3(1, 1, 0));
    float n001 = hash31(i+vec3(0, 0, 1));
    float n101 = hash31(i+vec3(1, 0, 1));
    float n011 = hash31(i+vec3(0, 1, 1));
    float n111 = hash31(i+vec3(1, 1, 1));
    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm(vec3 p){
    float a = 0.5, f = 0.0;
    for (int i=0;i<FBM_OCTAVES;++i){
        f += a * sh_noise3(p);
        p = p*2.02 + vec3(31.416, 47.0, 19.19);
        a *= 0.5;
    }
    return f;
}

// Approximate curl of noise field (matching fog_cylinder)
vec3 curl(vec3 p){
    float e = 0.10;
    float nx1 = sh_noise3(p + vec3(e, 0, 0));
    float nx0 = sh_noise3(p - vec3(e, 0, 0));
    float ny1 = sh_noise3(p + vec3(0, e, 0));
    float ny0 = sh_noise3(p - vec3(0, e, 0));
    float nz1 = sh_noise3(p + vec3(0, 0, e));
    float nz0 = sh_noise3(p - vec3(0, 0, e));
    vec3 g = vec3(nx1 - nx0, ny1 - ny0, nz1 - nz0);
    vec3 c = vec3(g.y - g.z, g.z - g.x, g.x - g.y);
    return normalize(c + 1e-6);
}

// --- Bayer dithering (matching fog_cylinder) ---
float bayer4x4(vec2 frag) {
    ivec2 ip = ivec2(mod(frag, 4.0));
    int idx = (ip.y << 2) | ip.x;
    const float M[16] = float[16](
        0.0, 8.0, 2.0, 10.0,
        12.0, 4.0, 14.0, 6.0,
        3.0, 11.0, 1.0, 9.0,
        15.0, 7.0, 13.0, 5.0
    );
    return (M[idx] + 0.5) / 16.0;
}

// --- Quantization (matching fog_cylinder) ---
vec3 quantize3D(vec3 p, float voxelsPerRad) {
    if (voxelsPerRad <= 0.0) return p;
    return floor(p * voxelsPerRad + 0.5) / max(voxelsPerRad, 1.0);
}

// --- Posterization (matching fog_cylinder) ---
float posterize01f(float v, float steps, float ditherAmt) {
    if (steps <= 0.5) return v;
    float t = clamp(ditherAmt, 0.0, 1.0) * bayer4x4(gl_FragCoord.xy);
    return floor(v * steps + t) / steps;
}

// --- SDF Utilities ---
float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

// Returns closest point on segment a->b to point p
vec3 closestPointOnSegment(vec3 p, vec3 a, vec3 b) {
    vec3 ba = b - a;
    float t = clamp(dot(p - a, ba) / dot(ba, ba), 0.0, 1.0);
    return a + ba * t;
}

void main() {
    float fade = clamp(uAuraFade, 0.0, 1.0);
    if (fade <= 0.0001) { discard; return; }

    int numSegments = int(uNumNodes) - 1;
    if (numSegments < 1) discard;

    // Ray origin at camera (always 0,0,0 in camera-relative space)
    // uCameraPosWS holds the actual world position for noise anchoring.
    vec3 ro = vec3(0.0);
    vec3 rd = normalize(vRayDirWS);

    // Sample scene depth for occlusion comparison
    vec2 screenUV = gl_FragCoord.xy / uScreenSize;
    float sceneDepth = texture(uDepth, screenUV).r;

    // Reconstruct scene view-space Z for robust occlusion checking.
    // Nearer objects have larger (less negative) Z values in GL view space.
    vec4 sceneClipP = vec4(screenUV * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
    vec4 sceneViewP = uInvProj * sceneClipP;
    float sceneZ = sceneViewP.z / sceneViewP.w;

    float jitter = hash31(vec3(gl_FragCoord.xy, uTime)) - 0.5;

    // Tube radius base value (will be refined per-step with taper)
    float R = max(uTubeRadius, 1e-4);

    // --- Sphere tracing raymarch ---
    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;
    float t = 0.0;

    for (int i = 0; i < MAX_STEPS; i++) {
        if (t > uTrailMaxDist) break;
        if (accumA > 0.98) break;

        vec3 p = ro + rd * (t + jitter);

        // Depth buffer comparison: compare view-space Z for Z-direction independence
        vec3 viewP = mat3(uView) * p;
        if (viewP.z < sceneZ) break;

        // Player exclusion sphere (uPlayerPosCS is in camera-relative world-aligned space)
        float distToPlayer = length(p - uPlayerPosCS);
        if (distToPlayer < uNearFadeMin) {
            t += max(uNearFadeMin - distToPlayer, MIN_STEP);
            continue;
        }

        // Find minimum distance to any capsule segment + closest point + segment index
        // (uNodes are in camera-relative world-aligned space, same as p)
        float minDist = 1e6;
        int closestSeg = 0;
        vec3 closestPt = p;

        for (int j = 0; j < 47; j++) {
            if (j >= numSegments) break;

            vec3 p1 = vec3(uNodes[j*3], uNodes[j*3+1], uNodes[j*3+2]);
            vec3 p2 = vec3(uNodes[(j+1)*3], uNodes[(j+1)*3+1], uNodes[(j+1)*3+2]);

            // Tapered volume: interpolate radius from thick (player) to thin (objective)
            float segT = float(j) / float(numSegments);
            float segR = mix(uTubeRadius, uTubeRadiusEnd, segT);
            float d = sdCapsule(p, p1, p2, segR);
            if (d < minDist) {
                minDist = d;
                closestSeg = j;
                closestPt = closestPointOnSegment(p, p1, p2);
            }
        }

        // Sphere tracing: if far from surface, take large SDF-driven steps
        if (minDist > uMaxThickness) {
            t += max(minDist * STEP_SAFETY, MIN_STEP);
            continue;
        }

        // Inside the density extent — compute shading
        float stepLen = max(minDist * STEP_SAFETY, MIN_STEP);

        // Tapered R: use the radius at the closest segment for proper normalization
        float taperedT = float(closestSeg) / max(float(numSegments), 1.0);
        R = max(mix(uTubeRadius, uTubeRadiusEnd, taperedT), 1e-4);

        // SDF depth (matching fog_cylinder core01: 0 at surface, 1 deep inside)
        float inDist = max(-minDist, 0.0);
        float core01 = saturate(inDist / max(uMaxThickness, 1e-4));
        core01 = pow(core01, uCorePow);

        // Convert to absolute world space for noise anchoring.
        // p is camera-relative; adding uCameraPosWS gives a fixed world position
        // so the noise domain doesn't shift when the camera moves or rotates.
        vec3 pAbsWorld = p + uCameraPosWS;

        // === Relative coordinates in absolute world space (size-invariant) ===
        vec3 pRel = pAbsWorld / R;

        // Displacement from closest segment centerline to sample point
        // (camera offset cancels: (p+cam) - (closestPt+cam) = p - closestPt)
        vec3 displacement = p - closestPt;
        float yDisp = displacement.y;  // world-up is stable (y-axis is always up)
        float y01 = clamp(yDisp / (R + uMaxThickness) * 0.5 + 0.5, 0.0, 1.0);

        // === Limb fade (matching fog_cylinder lines 426-427) ===
        // pathNorm: 0 at silhouette, 1 through center
        float pathNorm = clamp(inDist / (2.0 * R), 0.0, 1.0);
        float limb = pow(smoothstep(0.0, uLimbSoft, pathNorm), uLimbHardness);

        // === Pixelation in relative space (matching fog_cylinder line 465) ===
        vec3 pPix = quantize3D(pRel, uPixelsPerRadius);

        // === Upward scrolling (matching fog_cylinder line 468) ===
        // World up in relative space, scroll upward
        vec3 upRel = vec3(0.0, 1.0 / R, 0.0);
        vec3 advScrollRel = upRel * (uScrollSpeedRel * uTime);

        // === Main FBM sample position (matching fog_cylinder line 477) ===
        // Stationary domain; field moves via advection scroll
        vec3 pw = (pPix * uNoiseScaleRel) - advScrollRel * uNoiseScaleRel;

        // === Patchiness mask (height-aware, matching fog_cylinder lines 480-486) ===
        float patchH       = pow(y01, max(uPatchGamma, 0.0001));
        float patchStrength= clamp(uPatchStrengthTop * patchH, 0.0, 1.0);
        float patchThresh  = mix(uPatchThreshBase, uPatchThreshTop, patchH);
        vec3  pwPatch      = (pPix * uPatchScaleRel) - advScrollRel * uPatchScaleRel;
        float nPatch       = sh_noise3(pwPatch);
        float patchMask    = smoothstep(patchThresh - uPatchSharpness,
                                         patchThresh + uPatchSharpness, nPatch);
        float patchMix     = mix(1.0, patchMask, patchStrength);

        // === Small warp (matching fog_cylinder line 489) ===
        pw += (fbm(pPix * (uNoiseScaleRel * 0.5)) - 0.5) * (uWarpAmp * 0.8);

        // === FBM noise + posterization (matching fog_cylinder lines 492-493) ===
        float n = fbm(pw);
        n = posterize01f(n, uPosterizeSteps, uDitherAmount);

        // === Brightness shaping (matching fog_cylinder lines 496-497) ===
        float bright = saturate((n - uBlackPoint) / (1.0 - uBlackPoint));
        bright = pow(bright, uGlowGamma);

        // === Build density (matching fog_cylinder lines 500-510) ===
        float densitySample = bright;

        // Height-dependent fade so fog dissipates upward (matching fog_cylinder line 503)
        float heightFade = mix(1.0, uHeightFadeMin, pow(y01, max(uHeightFadePow, 0.0001)));
        densitySample *= heightFade;

        // Apply patchiness (matching fog_cylinder line 507)
        densitySample *= patchMix;

        // Apply volumetric SDF profile (matching fog_cylinder line 510)
        densitySample *= core01;

        // Trail distance fade: dissipate toward the tail end
        float trailT    = float(closestSeg) / float(numSegments);
        float trailFade = mix(1.0, 0.25, pow(trailT, 1.6));
        densitySample  *= trailFade;

        // Dynamic energy pulse: traveling wave of intensity from player toward objective
        // Tension increases pulse amplitude and speed
        float pulseAmp = 0.5 + uTension * 0.4;
        float pulseSpeed = 5.0 + uTension * 3.0;
        densitySample *= (1.0 + pulseAmp * sin(trailT * 10.0 - uTime * pulseSpeed));

        // Trail quality effects: low quality adds noise/flicker
        float qualityNoise = 1.0;
        if (uTrailQuality < 0.8) {
            float noiseIntensity = (1.0 - uTrailQuality) * 0.6;
            float flicker = sh_noise3(pAbsWorld * 2.0 + vec3(uTime * 3.0));
            qualityNoise = mix(1.0, flicker, noiseIntensity);
        }
        densitySample *= qualityNoise;

        // Tension-driven visual instability: at high tension, add interference flicker
        if (uTension > 0.4) {
            float tensionFlicker = sh_noise3(pAbsWorld * 0.5 + vec3(uTime * 8.0, 0.0, 0.0));
            float flickerStrength = (uTension - 0.4) * 0.5;
            densitySample *= mix(1.0, 0.5 + tensionFlicker, flickerStrength);
        }

        // Proximity fade: fade out near the player to avoid sudden popping
        float nearFade  = smoothstep(uNearFadeMin, uNearFadeMax, distToPlayer);
        densitySample  *= nearFade;

        // === Per-step alpha (matching fog_cylinder lines 520-521) ===
        float densNorm = uDensity * fade;
        float a = 1.0 - exp(-densNorm * max(densitySample, 0.0) * stepLen);
        a *= limb;

        // === Emissive color (matching fog_cylinder line 524) ===
        vec3 col = uColorB * bright;
        //col *= exp(-uAbsorption * inDist);

        // Core darkening + rim preservation (matching fog_cylinder lines 530-534)
        float rimW      = pow(saturate(1.0 - core01), 1.75);
        float coreShade = mix(0.75, 1.0, rimW);
        //col *= coreShade;
        //col *= (1.0 + 0.06 * rimW);

        // Density-weighted shadow mix (matching fog_cylinder lines 538-545)
        float dMask = saturate((densitySample - uShadowKnee) / max(1e-5, 1.0 - uShadowKnee));
        dMask = pow(dMask, uShadowGamma);
        //col = mix(col, uColorA, dMask * uShadowMix);

        // Desaturate very dark results (matching fog_cylinder lines 543-545)
        float L       = dot(col, vec3(0.2126, 0.7152, 0.0722));
        float darkAmt = smoothstep(0.0, 0.25, 0.25 - L);
        //col = mix(col, vec3(L), darkAmt * uShadowDesat);

        // Front-to-back premultiplied accumulate
        float premul = a * (1.0 - accumA);
        accumRGB += col * premul;
        accumA   += premul;

        t += stepLen;
    }

    float alpha = accumA * fade * uAuraFade;
    if (alpha < 0.01) discard;
    float fadeColor = pow(fade, max(uFadeGamma, 0.0001));
    fragColor = vec4(accumRGB * fadeColor * uAuraFade, alpha);
}
