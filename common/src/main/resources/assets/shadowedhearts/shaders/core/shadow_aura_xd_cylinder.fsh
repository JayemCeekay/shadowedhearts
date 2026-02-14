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
uniform vec3  uGravityDir;// world up (0,1,0)

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

// Hash still used for jitter/dither
float hash31(vec3 p){
    p = fract(p*0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x+p.y)*p.z);
}

// ===== 3D Simplex Noise (Ashima) =====
// Returns approximately [-1, +1]
vec4 permute(vec4 x){ return mod(((x*34.0)+1.0)*x, 289.0); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314 * r; }
float snoise(vec3 v){
    const vec2  C = vec2(1.0/6.0, 1.0/3.0);
    const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i  = floor(v + dot(v, C.yyy));
    vec3 x0 = v - i + dot(i, C.xxx);

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy; // 2.0*C.x = 1/3
    vec3 x3 = x0 - D.yyy;      // -1.0 + 3.0*C.x = -1/2

    // Permutations
    i = mod(i, 289.0);
    vec4 p = permute(permute(permute(
    i.z + vec4(0.0, i1.z, i2.z, 1.0))
    + i.y + vec4(0.0, i1.y, i2.y, 1.0))
    + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    // Gradients: 7x7x6 points over a cube, mapped onto an octahedron.
    float n_ = 1.0/7.0;
    vec3  ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);  // mod(p,7*7)
    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);

    vec4 x = x_ * ns.x + ns.yyyy;
    vec4 y = y_ * ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0)*2.0 + 1.0;
    vec4 s1 = floor(b1)*2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    // Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2,p2), dot(p3,p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m*m, vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3)));
}

// ===== Noise Suite =====
float noise01(vec3 p){ return 0.5 + 0.5 * snoise(p); }                 // smooth value 0..1
float turb01(vec3 p){ return abs(snoise(p)); }                          // turbulence 0..1-ish
float ridge01(vec3 p){ float n = 1.0 - abs(snoise(p)); return n*n; }    // ridged (filaments)

float fbm01(vec3 p){
    float f = 0.0;
    float a = 0.5;
    for(int i=0;i<6;i++){
        f += a * noise01(p);
        p = p*2.02 + vec3(31.416, 47.0, 19.19);
        a *= 0.5;
    }
    return f;
}

float fbmTurb01(vec3 p){
    float f = 0.0;
    float a = 0.5;
    for(int i=0;i<5;i++){
        f += a * turb01(p);
        p = p*2.08 + vec3(13.7, 9.2, 17.1);
        a *= 0.55;
    }
    return f;
}

float fbmRidge01(vec3 p){
    float f = 0.0;
    float a = 0.6;
    for(int i=0;i<5;i++){
        f += a * ridge01(p);
        p = p*2.05 + vec3(7.7, 19.3, 3.1);
        a *= 0.55;
    }
    return f;
}

// Domain warp vector (0..1 components)
vec3 warp3(vec3 p){
    return vec3(
    fbm01(p + vec3(17.0, 0.0, 0.0)),
    fbm01(p + vec3(0.0, 59.0, 0.0)),
    fbm01(p + vec3(0.0, 0.0, 101.0))
    );
}

// Approximate curl noise (divergence-free-ish) built from simplex finite differences
vec3 curlNoise(vec3 p){
    float e = 0.10;
    float n1 = snoise(p + vec3(0,e,0));
    float n2 = snoise(p - vec3(0,e,0));
    float a  = (n1 - n2) / (2.0*e);

    n1 = snoise(p + vec3(0,0,e));
    n2 = snoise(p - vec3(0,0,e));
    float b = (n1 - n2) / (2.0*e);

    n1 = snoise(p + vec3(e,0,0));
    n2 = snoise(p - vec3(e,0,0));
    float c = (n1 - n2) / (2.0*e);

    // Simple constructed curl-like vector
    vec3 v = vec3(a - b, b - c, c - a);
    return normalize(v + 1e-6);
}

// 3D Worley / cellular (F1) in [0..~1]
vec3 hash33(vec3 p){
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.xxy + p.yzz) * p.zyx);
}

float worleyF1(vec3 p){
    vec3 i = floor(p);
    vec3 f = fract(p);
    float md = 1e9;
    for(int z=-1; z<=1; z++){
        for(int y=-1; y<=1; y++){
            for(int x=-1; x<=1; x++){
                vec3 g = vec3(float(x), float(y), float(z));
                vec3 o = hash33(i + g);
                vec3 r = g + o - f;
                float d = dot(r, r);
                md = min(md, d);
            }
        }
    }
    // sqrt for distance-like metric, then clamp to 0..1
    return saturate(sqrt(md));
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

// SDFs (capsule variant)
// Dome-capped cylinder (capsule) around Y with radius r and apex half-extent H.
// H is the total half-extent to the dome apex (matches geometry); the internal
// cylinder segment half-length is hSeg = max(H - r, 0).
float sdCappedCylinderY(vec3 p, float r, float H) {
    vec2 d = vec2(length(p.xz)-2.0*r+1.0, abs(p.y)-H);
    return min(max(d.x,d.y),0.0) + length(max(d,0.0)) - 1.0;
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

float posterize01f(float v, float steps, float ditherAmt) {
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
    vec3 ro   = (uInvModel * vec4(roWS, 1.0)).xyz;
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

        vec3 p = ro + rd * ti;// object space sample

        // --- Relative coords (size-invariant) ---
        vec3 pRel = p / R;

        // Height from -1..+1 in relative object space -> 0..1
        float y01 = saturate(0.5 * (pRel.y + 1.0));

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
        vec3 pPix = pRel;
        //quantize3D(pRel, uPixelsPerRadius);

        // Final noise sample position
        vec3 advScrollRel = -upRel * (uScrollSpeedRel * uTime); // negative so positive scroll speed moves visuals UP

        // Main FBM sample position (stationary domain; field moves)
        vec3 pw = (pPix * uNoiseScaleRel) + (advScrollRel) * uNoiseScaleRel;

        // Patchiness mask
        float patchH = pow(y01, max(uPatchGamma, 0.0001));
        float patchStrength = clamp(uPatchStrengthTop * patchH, 0.0, 1.0);
        float patchThresh = mix(uPatchThreshBase, uPatchThreshTop, patchH);
        vec3 pwPatch = (pPix * uPatchScaleRel) - (advScrollRel) * uPatchScaleRel;
        float patchBase  = fbm01(pwPatch * 0.55);
        float patchCells = 1.0 - worleyF1(pwPatch * 0.35);
        float nPatch = mix(patchBase, patchCells, 0.55);
        float patchMask = smoothstep(patchThresh - uPatchSharpness, patchThresh + uPatchSharpness, nPatch);
        float patchMix = mix(1.0, patchMask, patchStrength);

        // Small warp for extra curl
        vec3 warpV = (warp3(pPix * (uNoiseScaleRel * 0.35) + vec3(0.0, -uTime * 0.12, 0.0)) - 0.5);
        pw += warpV * (uWarpAmp * 6.0);

        // quantize the noise and its brightness
        // XD-style: separate fog density from emissive filaments/sparks.
        // This matches the reference where a soft smoky body carries brighter wisps and occasional spark pockets.
        vec3 q = pw;

        // Secondary warp to create sheet-like curls (avoids "scrolling texture" look)
        q += (warp3(q * 0.33 + vec3(0.0, -uTime * 0.22, 0.0)) - 0.5) * (uWarpAmp * 11.0);
        q += curlNoise(q * 0.22 + vec3(0.0, -uTime * 0.35, 0.0)) * 0.40;

        // === 3-layer XD Gale of Darkness style ===
        // Layer 1: dark purple filamentary plasma w/ outward bursts
        // Layer 2: neon pink/purple filamentary plasma w/ outward bursts
        // Layer 3: purple-white "flakes" drifting upward like feathers/leaves in an updraft
        //
        // All layers share a slow upward scroll (advScrollRel) and additional internal turbulence.

        // Shared turbulent coordinate (domain-warped + curl) for plasma
        vec3 q0 = pw;
        q0 += (warp3(q0 * 0.33 + vec3(0.0, -uTime * 0.18, 0.0)) - 0.5) * (uWarpAmp * 10.0);
        q0 += curlNoise(q0 * 0.22 + vec3(0.0, -uTime * 0.28, 0.0)) * 0.42;

        // Shell bias makes bursts feel like they push outward from the surface
        float shellW = pow(saturate(1.0 - core01), 1.05); // 1 at rim, 0 deep inside

        // Patch gating: use your existing patch mask to create bursty regions
        float burstGate = mix(1.0, patchMask, patchStrength);

        // ---------- Layer 1 (dark plasma) ----------
        vec3 q1 = q0;
        // Stable per-cell burst phase (so it "ignites & fades" without swimming)
        vec3 cellId1 = floor(q1 * 0.75);
        float seed1  = hash31(cellId1);
        float ph1    = fract(uTime * 0.35 + seed1);
        float burst1 = smoothstep(0.00, 0.08, ph1) * smoothstep(1.0, 0.78, ph1);

        float fil1 = fbmRidge01(q1 * 1.10);
        fil1 = saturate((fil1 - 0.55) / 0.45);
        fil1 = pow(fil1, 3.8);

        float pockets1 = 1.0 - worleyF1(q1 * 1.55 - upRel * (uTime * 0.25));
        pockets1 = pow(smoothstep(0.70, 0.95, pockets1), 4.0) * burst1;

        float layer1 = saturate(fil1 * 1.05 + pockets1 * 1.35);
        layer1 *= (0.55 + 0.45 * shellW) * burstGate;

        // ---------- Layer 2 (neon plasma) ----------
        vec3 q2 = q0 * 1.05 + vec3(19.7, 7.3, 13.1);
        vec3 cellId2 = floor(q2 * 0.70);
        float seed2  = hash31(cellId2);
        float ph2    = fract(uTime * 0.32 + seed2);
        float burst2 = smoothstep(0.00, 0.10, ph2) * smoothstep(1.0, 0.72, ph2);

        float fil2 = fbmRidge01(q2 * 1.22);
        fil2 = saturate((fil2 - 0.52) / 0.48);
        fil2 = pow(fil2, 3.4);

        float pockets2 = 1.0 - worleyF1(q2 * 1.65 - upRel * (uTime * 0.30));
        pockets2 = pow(smoothstep(0.72, 0.96, pockets2), 4.8) * burst2;

        float layer2 = saturate(fil2 * 1.10 + pockets2 * 1.55);
        layer2 *= (0.60 + 0.40 * shellW) * burstGate;

        // ---------- Layer 3 (flakes / feathers) ----------
        // High-frequency cellular points, turbulently advected upward.
        float flakeScale = uNoiseScaleRel * 3.2;
        vec3 q3 = (pPix * flakeScale) - (advScrollRel) * flakeScale;
        q3 += curlNoise(q3 * 0.35 + vec3(0.0, -uTime * 0.60, 0.0)) * 0.85;
        q3 += upRel * (uTime * 1.15); // faster drift than plasma

        // Small bright flakes: use cellular minima -> tiny blobs, then sharpen.
        float fl = 1.0 - worleyF1(q3 * 1.35);
        fl = smoothstep(0.80, 0.95, fl);
        // Feather-like irregularity: modulate with turbulence
        float flJit = fbmTurb01(q3 * 0.85);
        fl = pow(saturate(fl * (0.65 + 0.60 * flJit)), 3.2);

        // Slight rim preference so flakes read in silhouette
        float flakes = fl * (0.55 + 0.45 * shellW);

        // ---------- Emission & density ----------
        // Density controls alpha; keep it smoother than emission.
        float densityN = saturate(layer1 * 0.55 + layer2 * 0.55 + flakes * 0.35);

        // Emission is punchier (filaments + pockets + flakes)
        float emissN = saturate(layer1 * 0.70 + layer2 * 1.00 + flakes * 0.95);
        // Optional posterize for stylization (usually off for XD)
        // emissN = posterize01(emissN, uPosterizeSteps, uDitherAmount);

        // Brightness shaping (global)
        float bright = saturate((emissN - uBlackPoint) / (1.0 - uBlackPoint));
        bright = pow(bright, uGlowGamma);

        // Base mask * NOISE ONLY (for alpha)
        float densitySample = densityN;

        // Pre-bright color mix for the 3 layers (later multiplied by bright)
        vec3 colMix =
        (uColorA * 0.70) * layer1
        + (uColorB * 1.15) * layer2
        + (mix(uColorB, vec3(1.0), 0.78) * 1.25) * flakes;

        // Height-dependent fade so fog dissipates toward the top
        float heightFade = mix(1.0, uHeightFadeMin, pow(y01, max(uHeightFadePow, 0.0001)));
        //densitySample *= heightFade;

        // Apply patchiness mask (stronger at the crown)
        // densitySample *= patchMix;

        //apply volumetric SDF shaping
        densitySample *= core01;

        // Subtle rim enhancement (optional)
        if (uRimStrength > 0.0){
            vec3 nrm = sdfNormalCylinderY(p, R, H);
            float rim = pow(saturate(1.0 - abs(dot(normalize(rd), nrm))), uRimPower);
            densitySample *= (1.0 + rim * uRimStrength);
        }

        // Per-step alpha (cap to avoid solid fill)
        float a = 1.0 - exp(-densNorm * max(densitySample, 0.0));
        a *= limb;

        // Emissive color follows noise; absorb with depth inside
        vec3 col = colMix * bright;
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
