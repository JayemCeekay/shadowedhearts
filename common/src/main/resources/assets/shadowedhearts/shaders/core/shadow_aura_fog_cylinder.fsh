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
uniform float uRimPower; // 1..4

// Thickness / edges (ABSOLUTE units; keep your radius scaling in Java)
uniform float uMaxThickness;// skin thickness under surface
uniform float uThicknessFeather;// softness at 0 and uMaxThickness
uniform float uEdgeKill;// erase very near the surface

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
uniform float uPatchScaleRel;     // low-frequency mask scale (per radius)
uniform float uPatchThreshBase;   // threshold at base (0..1)
uniform float uPatchThreshTop;    // threshold at top (0..1)
uniform float uPatchSharpness;    // smoothstep half-width
uniform float uPatchGamma;        // height ramp exponent for patch strength
uniform float uPatchStrengthTop;  // max patch strength at top (0..1)

// Height fade controls
uniform float uHeightFadeMin;     // residual density at top (0..1)
uniform float uHeightFadePow;     // exponent for height fade

// --- Inertial tail helpers (02 §5 Mission Entrance flow) ---
// Uniforms for lagged advection and sampling-space shear
uniform vec3  uVelLagWS;    // lagged world-space velocity
uniform float uFieldFreq;   // advection field frequency (relative)
uniform float uShearK;      // crown lean amount
uniform float uLagGamma;    // height ramp for lag
uniform float uBaseAdv;     // base advection magnitude
uniform float uVelInfluence;// velocity influence on magnitude

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

vec3 getUpOS(mat4 invM){ return normalize((invM * vec4(0,1,0,0)).xyz); }

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
    vec3 vLagOS   = (uInvModel * vec4(uVelLagWS,    0.0)).xyz;
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
    vec3 flowDir = normalize( 1.0*dirVel + 0.9*swirl + 1.0*upOS );

    // Speed response controls “energy” (how much it stretches)
    float speed   = length(vNowLat);
    float speed01 = clamp(speed * 0.08, 0.0, 1.0);
    float advMag  = uBaseAdv + uVelInfluence * smoothstep(0.0, 1.0, speed01);

    // Calm the top a little so it looks anchored
    float heightTaper = mix(1.0, 0.55, h);

    // Optional: small bending of flow like a flame
    flowDir = bendFlow(flowDir, upOS, vNowLat, h, speed01);

    return flowDir * advMag * heightTaper; // relative units/sec
}

// SDFs (cylinder variant)
// Finite-height (capped) cylinder around Y with radius r and half-height h.
float sdCappedCylinderY(vec3 p, float r, float h){
    // Standard capped-cylinder SDF: d = min(max(d.x,d.y),0) + length(max(d,0))
    // where d = abs(vec2(radial, y)) - vec2(r, h)
    vec2 d = abs(vec2(length(p.xz), p.y)) - vec2(r, h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

// Numerical SDF normal for capped cylinder (only if rim enabled)
vec3 sdfNormalCylinderY(vec3 p, float r, float h){
    const float e = 0.0025;
    float dx = sdCappedCylinderY(p+vec3(e, 0, 0), r, h) - sdCappedCylinderY(p-vec3(e, 0, 0), r, h);
    float dy = sdCappedCylinderY(p+vec3(0, e, 0), r, h) - sdCappedCylinderY(p-vec3(0, e, 0), r, h);
    float dz = sdCappedCylinderY(p+vec3(0, 0, e), r, h) - sdCappedCylinderY(p-vec3(0, 0, e), r, h);
    return normalize(vec3(dx, dy, dz));
}

// Ray-infinite-cylinder around Y (object space)
bool intersectCylinderInfY(vec3 ro, vec3 rd, float r, out float t0, out float t1){
    float a = rd.x*rd.x + rd.z*rd.z;
    float b = ro.x*rd.x + ro.z*rd.z;
    float c = ro.x*ro.x + ro.z*ro.z - r*r;
    if (a <= 1e-8) return false;
    float disc = b*b - a*c;
    if (disc < 0.0) return false;
    float h = sqrt(disc);
    t0 = (-b - h) / a;
    t1 = (-b + h) / a;
    return t1 > 0.0;
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

// Height-aware teardrop deformation (relative OS)
vec3 teardropDeformRel(vec3 pRel, mat4 uInvModel, vec3 uEntityVelWS){
    // Up and lateral velocity in object space
    vec3 upOS    = normalize((uInvModel * vec4(0,1,0,0)).xyz);
    vec3 vNowOS  = (uInvModel * vec4(uEntityVelWS, 0.0)).xyz;
    vec3 vLatOS  = vNowOS - dot(vNowOS, upOS) * upOS; // lateral only

    // Height 0..1 through the proxy radius
    float h = clamp(pRel.y * 0.5 + 0.5, 0.0, 1.0);

    // 1) Vertical stretch (taller toward the top)
    float stretchY = mix(1.0, 1.25, pow(h, 1.1));

    // 2) XZ taper (narrower crown, fuller base)
    float baseScale = 1.05;  // slight bulge near base
    float topScale  = 0.70;  // pinched crown
    float taperXZ   = mix(baseScale, topScale, pow(h, 1.2));

    // Apply anisotropic scaling in a frame aligned with upOS
    vec3 w = upOS;
    vec3 u = normalize(abs(w.y) < 0.99 ? cross(w, vec3(0,1,0)) : cross(w, vec3(1,0,0)));
    vec3 v = cross(w, u);
    mat3 B = mat3(u, v, w);       // columns are basis vectors
    mat3 BT = transpose(B);

    // Transform pRel into that basis, scale, and back
    vec3 q = BT * pRel;
    q.xy *= taperXZ;     // taper in plane perpendicular to up
    q.z  *= stretchY;    // stretch along up
    vec3 pTaper = B * q;

    // 3) Optional bend opposite current motion (small angle increasing with height)
    float speedLat = length(vLatOS);
    float speed01  = clamp(speedLat * 0.08, 0.0, 1.0);
    vec3 axis      = normalize(cross(upOS, vLatOS) + 1e-5);
    float maxAngle = radians(18.0);
    float angle    = maxAngle * pow(h, 1.2) * speed01; // more bend near crown and when moving
    vec3 pBend     = rotateAroundAxis(pTaper, axis, angle);

    return pBend;
}

void main(){
    float fade = saturate(uAuraFade);
    if (fade <= 0.0001){ discard; return; }

    // Build ray in object space
    vec3 roWS = uCameraPosWS;
    vec3 rdWS = normalize(vRayDirWS);
    vec3 ro   = (uInvModel * vec4(roWS, 1.0)).xyz;
    vec3 rd   = normalize((uInvModel * vec4(rdWS, 0.0)).xyz);

    // Bounding cylinder (infinite along Y)
    float R = max(uProxyRadius, 1e-6);
    float t0, t1;
    if (!intersectCylinderInfY(ro, rd, R, t0, t1)){ discard; }
    t0 = max(t0, 0.0);

    // March setup + limb fade
    float rawPath = max(t1 - t0, 1e-4);
    float stepLen = rawPath / float(N_STEPS);

    // Use the cylinder diameter as normalization baseline similar to sphere case
    float minLen  = (2.0*R) * uMinPathNorm;// clamp for normalization
    float densNorm= uDensity * (float(N_STEPS) / max(rawPath, minLen)) * fade;

    float pathNorm= clamp(rawPath / (2.0*R), 0.0, 1.0);// 0 at silhouette, 1 through center
    float limb    = pow(smoothstep(0.0, uLimbSoft, pathNorm), uLimbHardness);

    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;

    float jitter = hash31(vec3(gl_FragCoord.xy, uTime)) - 0.5;

    for (int i=0;i<N_STEPS;++i){
        float ti = t0 + ((float(i) + 0.5 + jitter) * stepLen);
        if (ti > t1) break;

        vec3 p = ro + rd * ti;// object space sample

        // --- Relative coords (size-invariant) ---
        vec3 pRel = p / R;

        // Height from -1..+1 in relative object space -> 0..1
        float y01 = saturate(0.5 * (pRel.y + 1.0));

        // Inertial, height-aware advection relative to object radius
        vec3 advRel = computeAdvectionLagged(pRel, uTime);

        // World up (relative OS)
        vec3 upOS  = (uInvModel * vec4(normalize(uGravityDir), 0.0)).xyz;
        vec3 upRel = normalize(upOS / R);

        // SDF distance (negative inside) using finite-height cylinder SDF
        float H = max(uProxyHalfHeight, 1e-6);
        float d = sdCappedCylinderY(p, R, H);
        if (d > 0.0) continue;

        float inDist = -d;

        // Erase very near the geometric surface (avoid visible rim)
        float nearFade = smoothstep(uThicknessFeather, uEdgeKill, inDist);
        float farFade = 1.0 - smoothstep(uMaxThickness - uEdgeKill, uMaxThickness, inDist);
        float cavity = nearFade * farFade;

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
        vec3 pw = (pPix * uNoiseScaleRel) - (advRel + advScrollRel) * uNoiseScaleRel + shearRel * uNoiseScaleRel;

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
        float densitySample = bright * cavity;

        // Height-dependent fade so fog dissipates toward the top
        float heightFade = mix(1.0, uHeightFadeMin, pow(y01, max(uHeightFadePow, 0.0001)));
        densitySample *= heightFade;

        // Apply patchiness mask (stronger at the crown)
        densitySample *= patchMix;

        // Subtle rim enhancement (optional)
        if (uRimStrength > 0.0){
            vec3 nrm = sdfNormalCylinderY(p, R, H);
            float rim = pow(saturate(1.0 - abs(dot(normalize(rd), nrm))), uRimPower);
            densitySample *= (1.0 + rim * uRimStrength);
        }

        //densitySample *= limb; // optional limb fade

        // Per-step alpha (cap to avoid solid fill)
        float a = 1.0 - exp(-densNorm * max(densitySample, 0.0));
        a *= limb * cavity;

        // Emissive color follows noise; absorb with depth inside
        vec3 col = uColorB * bright;
        col *= exp(-uAbsorption * inDist);

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
