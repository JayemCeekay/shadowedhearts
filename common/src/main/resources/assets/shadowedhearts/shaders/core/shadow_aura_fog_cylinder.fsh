// shadow_aura_fog_cylinder.fsh — Shadow Aura Fog (cylindrical bounds)
// Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture/attack/combat terms are gameplay mechanics.
//
// Purpose
// - Render a stylized emissive fog “aura” around entities using an infinite vertical cylinder SDF (radius R in XZ).
// - Patterns are scale-invariant: most controls are specified relative to the proxy radius.
// - Visual style: teardrop/flame silhouette, advected noise that leans with entity motion/wind.
//
// Notes
// - This is a near-identical copy of shadow_aura_fog.fsh, but the SDF, SDF normal, and ray bound tests use
//   a Y-axis infinite cylinder instead of a sphere. Uniform names and semantics are unchanged for drop-in use.
#version 150

// ===== From VS =====
in vec3 vPosWS;
in vec3 vRayDirWS;

// ===== Uniforms =====
uniform mat4  uModel;// object -> world
uniform mat4  uInvModel;// world -> object
uniform vec3  uCameraPosWS;
uniform float uTime;

// Physics uniforms
uniform vec3  uEntityVelWS;// world units per tick
uniform float uSpeed;// |uEntityVelWS|
uniform vec3  uGravityDir;// world up (0,1,0)
uniform vec3  uWindWS;// optional wind

// Samplers (optional)
uniform sampler2D uDepth;

// Proxy shape
uniform float uProxyRadius;// cylinder radius (R) in XZ
uniform float uProxyHalfHeight;// cylinder half-height (H) along Y

// Global fade & colors
uniform float uAuraFade;// [0..1]
uniform float uFadeGamma;// e.g., 1.5
uniform vec3  uColorA;// deep base
uniform vec3  uColorB;// highlight

// Density/absorption
uniform float uDensity;// base density
uniform float uAbsorption;// Beer extinction along inDist

// Rim (subtle)
uniform float uRimStrength;// 0..1
uniform float uRimPower;// 1..4

// Thickness (ABSOLUTE units; keep your radius scaling in Java)
uniform float uMaxThickness;// skin thickness under surface

// ===== Relative controls (size-invariant look) =====
uniform float uNoiseScaleRel;// features per radius (e.g., 3.0)
uniform float uScrollSpeedRel;// radii per second (e.g., 0.5)

// Limb controls (kill silhouette cleanly) + normalization clamp
uniform float uLimbSoft;// 0.15–0.30
uniform float uLimbHardness;// 1.5–3.0
uniform float uMinPathNorm;// 0.10–0.25

// Emission shaping (inkier darks, punchier brights)
uniform float uGlowGamma;// 1.4–2.4
uniform float uBlackPoint;// 0.00–0.20

// Small warp for extra curl
uniform float uWarpAmp;// 0..0.5

// === Pixel look toggles ===
uniform float uPixelsPerRadius;// 0=off. Try 16–24 to match MC pixels per block (relative to aura radius)
uniform float uPosterizeSteps;// 0=off. Try 4.0 (3–6 works well)
uniform float uDitherAmount;// 0..1. Try 0.6–0.8

// Patchiness controls (relative units)
uniform float uPatchScaleRel;// low-frequency mask scale (per radius)
uniform float uPatchThreshBase;// threshold at base (0..1)
uniform float uPatchThreshTop;// threshold at top (0..1)
uniform float uPatchSharpness;// smoothstep half-width
uniform float uPatchGamma;// height ramp exponent for patch strength
uniform float uPatchStrengthTop;// max patch strength at top (0..1)

// Height fade controls
uniform float uHeightFadeMin;// residual density at top (0..1)
uniform float uHeightFadePow;// exponent for height fade

// --- Inertial tail helpers ---
// Uniforms for lagged advection and sampling-space shear
uniform vec3  uVelLagWS;// lagged world-space velocity
uniform float uFieldFreq;// advection field frequency (relative)
uniform float uShearK;// crown lean amount
uniform float uLagGamma;// height ramp for lag
uniform float uBaseAdv;// base advection magnitude
uniform float uVelInfluence;// velocity influence on magnitude

uniform float uCorePow;

// Density-weighted black mix controls (uniforms for fast prototyping)
uniform float uShadowMix;   // 0..1, max strength of density-driven mix toward black
uniform float uShadowKnee;  // 0..1, density level where black mix starts
uniform float uShadowGamma; // ~1.0..2.0, response curve for density → black mix
uniform float uShadowDesat; // 0..1, desaturate fraction for very dark regions

out vec4 FragColor;

// ===== Tunables =====
#define N_STEPS 16
#define FBM_OCTAVES 8

// ===== Helpers =====
float saturate(float x){ return clamp(x, 0.0, 1.0); }

float hash31(vec3 p){
    p = fract(p*0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x+p.y)*p.z);
}
float noise3(vec3 p){
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
        f += a * noise3(p);
        p = p*2.02 + vec3(31.416, 47.0, 19.19);
        a *= 0.5;
    }
    return f;
}

// Approximate curl of noise field (normalized)
vec3 curl(vec3 p){
    float e = 0.10;
    float nx1 = noise3(p + vec3(e, 0, 0));
    float nx0 = noise3(p - vec3(e, 0, 0));
    float ny1 = noise3(p + vec3(0, e, 0));
    float ny0 = noise3(p - vec3(0, e, 0));
    float nz1 = noise3(p + vec3(0, 0, e));
    float nz0 = noise3(p - vec3(0, 0, e));
    vec3 g = vec3(nx1 - nx0, ny1 - ny0, nz1 - nz0);
    // Curl from gradient differences, avoid zero vector
    vec3 c = vec3(g.y - g.z, g.z - g.x, g.x - g.y);
    return normalize(c + 1e-6);
}

vec3 getUpOS(mat4 invM){ return normalize((invM * vec4(0, 1, 0, 0)).xyz); }

vec3 rotateAroundAxis(vec3 v, vec3 axis, float angle){
    axis = normalize(axis);
    float c = cos(angle), s = sin(angle);
    return v*c + cross(axis, v)*s + axis*dot(axis, v)*(1.0 - c);
}

vec3 bendFlow(vec3 flowDir, vec3 upOS, vec3 vNowLat, float h, float speed01){
    vec3 axis  = normalize(cross(upOS, vNowLat) + 1e-5);
    float maxAngle = radians(22.0);
    float angle    = maxAngle * pow(h, 1.2) * speed01;
    return rotateAroundAxis(flowDir, axis, angle);
}

vec3 computeAdvectionLagged(vec3 pPixRel, float t){
    // pPixRel is relative object space in [-1..1]
    vec3 upOS     = getUpOS(uInvModel);

    // Current & lagged velocities in object space, strip vertical so buoyancy stays in charge
    vec3 vNowOS   = (uInvModel * vec4(uEntityVelWS, 0.0)).xyz;
    vec3 vLagOS   = (uInvModel * vec4(uVelLagWS, 0.0)).xyz;
    vec3 vNowLat  = vNowOS - dot(vNowOS, upOS) * upOS;
    vec3 vLagLat  = vLagOS - dot(vLagOS, upOS) * upOS;

    // Height in [0..1] where 0=bottom, 1=top
    float h = clamp(pPixRel.y * 0.5 + 0.5, 0.0, 1.0);

    // Mix between current and lagged direction, stronger lag as we go up
    float lagW = pow(smoothstep(0.2, 0.95, h), max(uLagGamma, 0.0001));
    vec3 dirVel = normalize(mix(vNowLat, vLagLat, lagW) + 1e-5);

    // Scroll only the field (prevents UV skating)
    float phase  = mod(t * uScrollSpeedRel, 2048.0);
    // Shear the field position opposite motion so the crown leans back
    vec3 tailHat = normalize(-vNowLat + 1e-5);
    vec3 pSkew   = pPixRel + tailHat * (uShearK * pow(h, max(uLagGamma, 0.0001)));

    // Turbulence field (curl-ish) evaluated in the skewed, upward-phased space
    vec3 pField  = pSkew * uFieldFreq /*+ vec3(0.0, -phase, 0.0)*/;
    vec3 swirl   = normalize(curl(pField));

    // Base flow: buoyancy up + wind if present handled outside; here up + vel + swirl
    vec3 flowDir = normalize(1.0*dirVel + 0.9*swirl + 1.0*upOS);

    // Speed response controls “energy” (how much it stretches)
    float speed   = length(vNowLat);
    float speed01 = clamp(speed * 0.08, 0.0, 1.0);
    float advMag  = uBaseAdv + uVelInfluence * smoothstep(0.0, 1.0, speed01);

    // Calm the top a little so it looks anchored
    float heightTaper = mix(1.0, 0.55, h);

    // Optional: small bending of flow like a flame
    flowDir = bendFlow(flowDir, upOS, vNowLat, h, speed01);

    return flowDir * advMag * heightTaper;// relative units/sec
}

// SDFs (capsule variant)
// Dome-capped cylinder (capsule) around Y with radius r and apex half-extent H.
// H is the total half-extent to the dome apex (matches geometry); the internal
// cylinder segment half-length is hSeg = max(H - r, 0).
float sdCappedCylinderY(vec3 p, float r, float H) {
    vec2 d = vec2(length(p.xz)-2.0*r+1.0, abs(p.y)-H);
    return min(max(d.x,d.y),0.0) + length(max(d,0.0)) - 1.0;
}

float sdCapsule( vec3 p, vec3 a, vec3 b, float r )
{
    vec3 pa = p - a, ba = b - a;
    float h = clamp( dot(pa,ba)/dot(ba,ba), 0.0, 1.0 );
    return length( pa - ba*h ) - r;
}

float sdVerticalCapsule(vec3 p, float h, float r) {
    p.y -= clamp(p.y, 0.0, h);
    return length(p)-r;
}

float sdVerticalCappedCylinder(vec3 p, float h, float r) {
    vec2 d = abs(vec2(length(p.xz), p.y)) - vec2(r,h);
    return min(max(d.x,d.y), 0.0) + length(max(d, 0.0));
}

// Numerical SDF normal for capsule (only if rim enabled)
vec3 sdfNormalCylinderY(vec3 p, float r, float H){
    const float e = 0.0025;
    // sdVerticalCapsule signature is (p, h, r). The previous call swapped h/r causing
    // incorrect normals near the silhouette which could also amplify vanishing.
    float dx = sdVerticalCapsule(p+vec3(e, 0, 0), H, r) - sdVerticalCapsule(p-vec3(e, 0, 0), H, r);
    float dy = sdVerticalCapsule(p+vec3(0, e, 0), H, r) - sdVerticalCapsule(p-vec3(0, e, 0), H, r);
    float dz = sdVerticalCapsule(p+vec3(0, 0, e), H, r) - sdVerticalCapsule(p-vec3(0, 0, e), H, r);
    return normalize(vec3(dx, dy, dz));
}

// Ray–sphere intersection helper.
// Returns nearest positive t, or -1.0 if no hit.
float intersectSphere(vec3 ro, vec3 rd, vec3 center, float radius) {
    vec3 oc = ro - center;
    float a = dot(rd, rd);
    float b = 2.0 * dot(oc, rd);
    float c = dot(oc, oc) - radius * radius;

    float disc = b * b - 4.0 * a * c;
    if (disc < 0.0) return -1.0;

    float sdisc = sqrt(disc);
    float inv2a = 0.5 / a;

    float t0 = (-b - sdisc) * inv2a;
    float t1 = (-b + sdisc) * inv2a;

    float t = 1e30;
    if (t0 > 0.0) t = t0;
    if (t1 > 0.0 && t1 < t) t = t1;

    if (t == 1e30) return -1.0;
    return t;
}

// Intersect ray with vertical capsule:
//   axis: (0,0,0) -> (0,h,0)
//   radius: r
// Returns true if hit, and nearest positive t in tHit.
bool intersectVerticalCapsule(vec3 ro, vec3 rd, float h, float r, out float tHit) {
    tHit = 1e30;
    bool hit = false;

    // --- 1. Cylinder body (infinite in Y, then clipped to [0, h]) ---
    vec2 roXZ = ro.xz;
    vec2 rdXZ = rd.xz;

    float A = dot(rdXZ, rdXZ);
    float B = 2.0 * dot(roXZ, rdXZ);
    float C = dot(roXZ, roXZ) - r * r;

    if (A > 0.0) { // ray not parallel to Y axis
        float disc = B * B - 4.0 * A * C;
        if (disc >= 0.0) {
            float sdisc = sqrt(disc);
            float inv2A = 0.5 / A;

            float t0 = (-B - sdisc) * inv2A;
            float t1 = (-B + sdisc) * inv2A;

            // Try near root
            if (t0 > 0.0) {
                float y0 = ro.y + rd.y * t0;
                if (y0 >= 0.0 && y0 <= h) {
                    tHit = t0;
                    hit = true;
                }
            }

            // Try far root if we didn't get a valid one yet
            if (!hit && t1 > 0.0) {
                float y1 = ro.y + rd.y * t1;
                if (y1 >= 0.0 && y1 <= h) {
                    tHit = t1;
                    hit = true;
                }
            }
        }
    }

    // --- 2. Bottom hemisphere (center at (0, 0, 0)) ---
    float tSphere = intersectSphere(ro, rd, vec3(0.0, 0.0, 0.0), r);
    if (tSphere > 0.0 && tSphere < tHit) {
        tHit = tSphere;
        hit = true;
    }

    // --- 3. Top hemisphere (center at (0, h, 0)) ---
    tSphere = intersectSphere(ro, rd, vec3(0.0, h, 0.0), r);
    if (tSphere > 0.0 && tSphere < tHit) {
        tHit = tSphere;
        hit = true;
    }

    return hit;
}

float bayer4x4(vec2 frag) {
    // 4x4 Bayer matrix, 0..15 -> 0..1
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

vec3 quantize3D(vec3 p, float voxelsPerRad) {
    if (voxelsPerRad <= 0.0) return p;
    vec3 q = floor(p * voxelsPerRad + 0.5) / max(voxelsPerRad, 1.0);
    return q;
}

float posterize01(float v, float steps, float ditherAmt) {
    if (steps <= 0.5) return v;
    float t = clamp(ditherAmt, 0.0, 1.0) * bayer4x4(gl_FragCoord.xy);
    return floor(v * steps + t) / steps;
}

float onion( in float d, in float h )
{
    return abs(d)-h;
}

void main() {
    float fade = clamp(uAuraFade, 0.0, 1.0); // saturate in GLSL 150
    if (fade <= 0.0001) { discard; return; }

    // Build ray in object space
    vec3 roWS = uCameraPosWS;
    vec3 rdWS = normalize(vRayDirWS);

    //Ray Origin in Shader Space
    vec3 ro   = (uInvModel * vec4(roWS, 1.0)).xyz;

    //Ray Direction in Shader Space
    vec3 rd   = normalize((uInvModel * vec4(rdWS, 0.0)).xyz);

    // Capsule proxy params
    float R = max(uProxyRadius,     1e-6); // radius
    float H = max(uProxyHalfHeight, 1e-6); // half-height around origin

    // Our analytic capsule helper is defined on [0, h] along +Y,
    // so we shift the ray so that:
    //   bottom cap is at y = 0
    //   top    cap is at y = 2H
    vec3 roCaps = ro;
    //roCaps.y += H;

    float tHit;
    bool hit = intersectVerticalCapsule(roCaps, rd, H, R, tHit);

    // Detect if the camera (ray origin) starts inside the volume using the same SDF
    // used for sampling. When inside, we should march from the camera out to the
    // first surface (exit). When outside, we march a thin shell past the entry.
    float dCam = sdVerticalCapsule(roCaps, H, R);
    bool cameraInside = (dCam < 0.0);

    if (!hit && !cameraInside) {
        // No intersection and not inside — nothing to render for this fragment.
        discard;
    }

    float tEnter;
    float tExit;
    if (cameraInside) {
        // Start just in front of the camera to avoid self-shadow artifacts.
        tEnter = 1e-3;
        // If we found an intersection, that is our exit; otherwise fall back to a safe bound.
        tExit  = hit ? max(tHit, tEnter + 1e-3) : 2.0 * R;
    } else {
        // Outside: begin at the first surface hit and sample a bounded thickness.
        tEnter = max(tHit, 0.0);
        tExit  = min(tEnter + 4.0 * R, 32.0);
    }

    if (tExit <= tEnter) { discard; }

    // March setup + limb fade
    float rawPath = max(tExit - tEnter, 1e-4);
    float stepLen = rawPath / float(N_STEPS);

    // Use the cylinder diameter as normalization baseline similar to sphere case
    float minLen  = (2.0*R) * uMinPathNorm;// clamp for normalization
    float densNorm= uDensity * (float(N_STEPS) / max(rawPath, minLen)) * fade;

    float pathNorm= clamp(rawPath / (2.0*R), 0.0, 1.0);// 0 at silhouette, 1 through center
    float limb    = pow(smoothstep(0.0, uLimbSoft, pathNorm), uLimbHardness);

    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;

    float jitter = hash31(vec3(gl_FragCoord.xy, uTime)) - 0.5;

    //MARCH
    for (int i=0;i<N_STEPS;++i){
        float ti = tEnter + ((float(i) + 0.5 + jitter) * stepLen);
        if (ti > tExit) break;

        vec3 p = roCaps + rd * ti;// object space sample

        // --- Relative coords (size-invariant) ---
        vec3 pRel = vec3(p.x/R, p.y/R, p.z/R);

        // Height from -1..+1 in relative object space -> 0..1
        float y01 = saturate(0.5 * (pRel.y + 1.0));

        // Inertial, height-aware advection relative to object radius
        vec3 advRel = computeAdvectionLagged(pRel, uTime);

        // World up (relative OS)
        vec3 upOS  = (uInvModel * vec4(normalize(uGravityDir), 0.0)).xyz;
        vec3 upRel = normalize(upOS / R);

        // SDF distance (negative inside) using finite-height cylinder SDF
        float d = sdVerticalCapsule(p, H, R);
        if (d > 0.0) continue;

        float inDist = -d;

        //volumetric SDF profile
        float core01 = saturate(inDist/max(uMaxThickness, 1e-4));
        core01 = pow(core01, uCorePow);

        // pixelate the coordinate in RELATIVE object space (size-invariant)
        vec3 pPix = quantize3D(pRel, uPixelsPerRadius);

        // Final noise sample position
        vec3 advScrollRel = upRel * (uScrollSpeedRel * uTime);

        // Height-aware sample-space shear opposite motion to make the crown lean back.
        vec3 vNowOS_forShear = (uInvModel * vec4(uEntityVelWS, 0.0)).xyz;
        vec3 vNowLat_forShear = vNowOS_forShear - dot(vNowOS_forShear, upOS) * upOS;
        vec3 tailHatRel = -vNowLat_forShear;
        vec3 shearRel = tailHatRel * ((-uSpeed) * pow(y01, max(uLagGamma, 0.0001)));

        // Main FBM sample position (stationary domain; field moves)
        vec3 pw = (pPix * uNoiseScaleRel) - (advRel + advScrollRel) * uNoiseScaleRel;

        // Patchiness mask
        float patchH = pow(y01, max(uPatchGamma, 0.0001));
        float patchStrength = clamp(uPatchStrengthTop * patchH, 0.0, 1.0);
        float patchThresh = mix(uPatchThreshBase, uPatchThreshTop, patchH);
        vec3 pwPatch = (pPix * uPatchScaleRel) - (advRel + advScrollRel) * uPatchScaleRel + shearRel * uPatchScaleRel;
        float nPatch = noise3(pwPatch);
        float patchMask = smoothstep(patchThresh - uPatchSharpness, patchThresh + uPatchSharpness, nPatch);
        float patchMix = mix(1.0, patchMask, patchStrength);

        // Small warp for extra curl
        pw += (fbm(pPix * (uNoiseScaleRel * 0.5)) - 0.5) * (uWarpAmp * 0.8);

        // quantize the noise and its brightness
        float n = fbm(pw);// 0..1
        n = posterize01(n, uPosterizeSteps, uDitherAmount);

        // Brightness shaping
        float bright = saturate((n - uBlackPoint) / (1.0 - uBlackPoint));
        bright = pow(bright, uGlowGamma);

        // Base mask * NOISE ONLY
        float densitySample = bright;

        // Height-dependent fade so fog dissipates toward the top
        float heightFade = mix(1.0, uHeightFadeMin, pow(y01, max(uHeightFadePow, 0.0001)));
        densitySample *= heightFade;

        // Apply patchiness mask (stronger at the crown)
        densitySample *= patchMix;

        //apply volumetric SDF shaping
        densitySample *= core01;

        // Subtle rim enhancement (optional)
        if (uRimStrength > 0.0){
            vec3 nrm = sdfNormalCylinderY(pRel, R, H);
            float rim = pow(saturate(1.0 - abs(dot(normalize(rd), nrm))), uRimPower);
            densitySample *= (1.0 + rim * uRimStrength);
        }

        // Per-step alpha (cap to avoid solid fill)
        float a = 1.0 - exp(-densNorm * max(densitySample, 0.0));
        a *= limb;

        // Emissive color follows noise; absorb with depth inside
        vec3 col = uColorB * bright;
        col *= exp(-uAbsorption * inDist);

        // Core darkening + rim preservation (RGB only)
        // Use SDF thickness to darken the core while keeping the rim bright.
        // core01: 0 at surface, 1 deep inside. Build a rim weight and shade accordingly.
        float rimW = pow(saturate(1.0 - core01), 1.75);
        float coreShade = mix(0.55, 1.0, rimW); // down to ~55% in the core, full brightness at rim
        col *= coreShade;
        // Subtle rim boost to keep the silhouette lively without affecting alpha
        col *= (1.0 + 0.06 * rimW);

        // Density-weighted black mix (inkier voids where fog is dense)
        // Build a mask from the current density sample (0..1), starting at uShadowKnee
        float dMask = saturate((densitySample - uShadowKnee) / max(1e-5, 1.0 - uShadowKnee));
        dMask = pow(dMask, uShadowGamma);
        // Mix RGB toward black based on mask and strength
        col = mix(col, vec3(0.0), dMask * uShadowMix);
        // Optional: desaturate only very dark results to avoid purple-gray glow
        float L = dot(col, vec3(0.2126, 0.7152, 0.0722));
        float darkAmt = smoothstep(0.0, 0.25, 0.25 - L);
        col = mix(col, vec3(L), darkAmt * uShadowDesat);

        // Front-to-back premultiplied accumulate
        float premul = a * (1.0 - accumA);
        accumRGB += col * premul;
        accumA   += premul;

        if (accumA > 0.98) break;
    }

    float alpha = accumA * fade;
    float fadeColor = pow(fade, max(uFadeGamma, 0.0001));
    FragColor = vec4(accumRGB * fadeColor, alpha);
}
