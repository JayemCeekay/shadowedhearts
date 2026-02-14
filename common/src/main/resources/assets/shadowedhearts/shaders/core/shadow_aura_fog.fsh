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
uniform float uProxyRadius;// sphere radius (R)

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

// SDFs (sphere only)
float sdSphere(vec3 p, float r){ return length(p) - r; }

// Numerical SDF normal for sphere (only if rim enabled)
vec3 sdfNormalSphere(vec3 p, float r){
    const float e=0.0025;
    float dx = sdSphere(p+vec3(e, 0, 0), r) - sdSphere(p-vec3(e, 0, 0), r);
    float dy = sdSphere(p+vec3(0, e, 0), r) - sdSphere(p-vec3(0, e, 0), r);
    float dz = sdSphere(p+vec3(0, 0, e), r) - sdSphere(p-vec3(0, 0, e), r);
    return normalize(vec3(dx, dy, dz));
}

// Ray-sphere (object space)
bool intersectSphere(vec3 ro, vec3 rd, float r, out float t0, out float t1){
    float b = dot(ro, rd);
    float c = dot(ro, ro) - r*r;
    float h = b*b - c;
    if (h < 0.0) return false;
    h = sqrt(h);
    t0 = -b - h; t1 = -b + h;
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


float posterize01f(float v, float steps, float ditherAmt) {
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

    // Height 0..1 through the proxy sphere
    float h = clamp(pRel.y * 0.5 + 0.5, 0.0, 1.0);

    // 1) Vertical stretch (taller toward the top)
    //    Small stretch reads as flame/teardrop. Tune 0.0..0.5
    float stretchY = mix(1.0, 1.25, pow(h, 1.1));

    // 2) XZ taper (narrower crown, fuller base)
    //    BaseScale > TopScale → teardrop profile
    float baseScale = 1.05;  // slight bulge near base
    float topScale  = 0.70;  // pinched crown
    float taperXZ   = mix(baseScale, topScale, pow(h, 1.2));

    // Apply anisotropic scaling in a frame aligned with upOS
    // Build a basis (u,v,w): w=upOS, u/v any perpendicular axes
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
    //    Reuse your rotateAroundAxis; axis is perpendicular to up and lateral motion.
    float speedLat = length(vLatOS);
    float speed01  = clamp(speedLat * 0.08, 0.0, 1.0);
    vec3 axis      = normalize(cross(upOS, vLatOS) + 1e-5);
    float maxAngle = radians(18.0);
    float angle    = maxAngle * pow(h, 1.2) * speed01; // more bend near crown and when moving
    vec3 pBend     = rotateAroundAxis(pTaper, axis, angle);

    return pBend;
}

void main(){
    // Main entry: raymarch emissive fog within a deformed sphere SDF.
    // Pipeline overview per pixel:
    // 1) Early-out if the aura is globally faded to zero.
    // 2) Build a ray in object space and intersect it with the bounding sphere to get the in-volume segment.
    // 3) March a fixed number of steps across that segment.
    // 4) At each step:
    //    - Use relative coordinates (divide by R) so all features scale with proxy size.
    //    - Build world-aware flow/advection and a height-based tilt so the aura leans with motion/wind.
    //    - Apply a teardrop deformation BEFORE evaluating the SDF so the silhouette follows the style.
    //    - Compute inward SDF distance to drive buffers, absorption, and optional rim.
    //    - Sample stable fbm noise; only the advection component scrolls over time to avoid global sliding.
    //    - Shape brightness, compute per-step alpha, then premultiply and front-to-back accumulate.
    // 5) Apply global fade gamma and output premultiplied color/alpha.
    float fade = saturate(uAuraFade);
    if (fade <= 0.0001){ discard; return; }

    // Build ray in object space
    // We trace in object space so the SDF and radius R are simple and stable.
    // roWS/rdWS come from the vertex stage. We transform the world-space ray origin (w=1)
    // and direction (w=0) into object space using uInvModel. The direction is renormalized
    // to avoid non-uniform scaling affecting step distribution.
    vec3 roWS = uCameraPosWS;
    vec3 rdWS = normalize(vRayDirWS);
    vec3 ro   = (uInvModel * vec4(roWS, 1.0)).xyz;
    vec3 rd   = normalize((uInvModel * vec4(rdWS, 0.0)).xyz);

    // Bounding sphere
    // R is the per-instance proxy radius from Java. Because this shader is sphere-only,
    // the bound sphere equals the SDF shape. We intersect the object-space ray with this
    // sphere to get entry/exit distances t0/t1 along the ray. If there is no hit, we discard.
    float R = max(uProxyRadius, 1e-6);
    float boundR = R;

    float t0, t1;
    if (!intersectSphere(ro, rd, boundR, t0, t1)){ discard; }
    t0 = max(t0, 0.0);

    // March setup + limb fade
    // rawPath is the inside-the-sphere segment length. stepLen divides this into N_STEPS uniform samples.
    // densNorm normalizes per-step density so visual thickness is consistent independent of path length.
    // We also compute a limb factor based on how central the path is (silhouette vs through-center). This can
    // optionally attenuate emission toward the silhouette to avoid a hard edge; see usage below.
    float rawPath = max(t1 - t0, 1e-4);
    float stepLen = rawPath / float(N_STEPS);

    float minLen  = (2.0*boundR) * uMinPathNorm;// clamp for normalization
    float densNorm= uDensity * (float(N_STEPS) / max(rawPath, minLen)) * fade;

    float pathNorm= clamp(rawPath / (2.0*boundR), 0.0, 1.0);// 0 at silhouette, 1 through center
    float limb    = pow(smoothstep(0.0, uLimbSoft, pathNorm), uLimbHardness);

    // Accum
    // We accumulate using premultiplied alpha to get correct front-to-back compositing.
    // accumRGB stores color already multiplied by alpha at each step; accumA stores the running coverage.
    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;

    // Per-pixel stratified jitter shifts sample phases to reduce coherent banding and Mach effects.
    // Using hash of pixel coords and time provides a temporally-stable randomness with slight evolution.
    float jitter = hash31(vec3(gl_FragCoord.xy, uTime)) - 0.5;

    // Fixed-step stochastic raymarch across the in-sphere segment. Early-out when we either leave the sphere
    // or we have accumulated near-opaque coverage to save work.
    for (int i=0;i<N_STEPS;++i){
        float ti = t0 + ((float(i) + 0.5 + jitter) * stepLen);
        if (ti > t1) break;

        vec3 p = ro + rd * ti;// object space sample

        // --- Relative coords (size-invariant) ---
        // Divide by R so that spatial frequencies and deformation profiles remain stable as the proxy
        // radius changes between entities. This keeps the look consistent across sizes.
        vec3 pRel = p / R;

        // Height from -1..+1 in relative object space -> 0..1
        // We use y01 both for shaping (base vs tip) and for tilt/bend profiles, so it is helpful to
        // have a clean 0..1 mapping independent of radius.
        float y01 = saturate(0.5 * (pRel.y + 1.0));

        // Inertial, height-aware advection relative to object radius
        vec3 advRel = computeAdvectionLagged(pRel, uTime);

        // World up (relative OS) still available if needed later
        vec3 upOS  = (uInvModel * vec4(normalize(uGravityDir), 0.0)).xyz;
        vec3 upRel = normalize(upOS / R);

        // SDF distance (negative inside) on deformed coordinate (sphere only)
        // We evaluate the signed distance on the deformed coordinate pShape, so d==0 follows the stylized surface.
        // Convention: d==0 on the surface, d>0 outside (discard), d<0 inside. inDist=-d is the inward distance
        // measured along the SDF’s shortest path, and drives absorption, rim suppression, and cavity masks.
        float d = sdSphere(p, R);
        if (d > 0.0) continue;

        float inDist = -d;

        // Erase very near the geometric surface (avoid visible rim)
        // We form a radial “cavity” band: nearFade ramps in from a standoff gap below the surface, while
        // farFade ramps out again when approaching uMaxThickness (an inner depth limit). Multiplying them
        // yields a soft band where the aura is allowed to exist. This keeps noise off the literal surface
        // and prevents a crunchy halo.
        float nearFade = smoothstep(uThicknessFeather, uEdgeKill, inDist);
        float farFade = 1.0 - smoothstep(uMaxThickness - uEdgeKill, uMaxThickness, inDist);
        float cavity = nearFade * farFade;

        // pixelate the coordinate in RELATIVE object space (size-invariant)
        // This optional quantization aligns the evaluation grid to a virtual voxel lattice measured in
        // “voxels per radius,” which helps the aura read like pixel art and remain consistent at scale.
        vec3 pPix = quantize3D(pRel, uPixelsPerRadius);

        // Final noise sample position: base stays fixed; only the advected offset scrolls
        // pPix provides the stationary frame; we subtract advection to push the evaluation
        // point upstream. Scaling by uNoiseScaleRel keeps frequencies per-radius invariant.
        // Upward scroll term (monotonic) in relative units to restore rising motion.
        vec3 advScrollRel = upRel * (uScrollSpeedRel * uTime);

        // Height-aware sample-space shear opposite motion to make the crown lean back.
        vec3 vNowOS_forShear = (uInvModel * vec4(uEntityVelWS, 0.0)).xyz;
        vec3 vNowLat_forShear = vNowOS_forShear - dot(vNowOS_forShear, upOS) * upOS;
        vec3 tailHatRel = -vNowLat_forShear;
        // Use y01 for height ramp; stronger shear near top.
        vec3 shearRel = tailHatRel * ((-uSpeed) * pow(y01, max(uLagGamma, 0.0001)));

        // Main FBM sample position (stationary domain; field moves)
        vec3 pw = (pPix * uNoiseScaleRel) - (advRel + advScrollRel) * uNoiseScaleRel + shearRel * uNoiseScaleRel;

        // Patchiness mask: low-frequency noise, advected and sheared with the main field,
        // and strengthened by height so the crown is more broken up than the base.
        float patchH = pow(y01, max(uPatchGamma, 0.0001));
        float patchStrength = clamp(uPatchStrengthTop * patchH, 0.0, 1.0);
        float patchThresh = mix(uPatchThreshBase, uPatchThreshTop, patchH);
        vec3 pwPatch = (pPix * uPatchScaleRel) - (advRel + advScrollRel) * uPatchScaleRel + shearRel * uPatchScaleRel;
        float nPatch = noise3(pwPatch);
        float patchMask = smoothstep(patchThresh - uPatchSharpness, patchThresh + uPatchSharpness, nPatch);
        float patchMix = mix(1.0, patchMask, patchStrength);

        // Small warp for extra curl (still evaluated in relative/pixelated space)
        // A subtle second-order fbm warp breaks up straight streamlines and adds turbulence.
        // Using pPix keeps the warp aligned with pixelation so features don’t shimmer.
        pw += (fbm(pPix * (uNoiseScaleRel * 0.5)) - 0.5) * (uWarpAmp * 0.8);

        // quantize the noise and its brightness
        // Posterizing the noise (with ordered dithering) yields cleaner, stylized bands that compress better
        // and avoid subtle banding. Set uPosterizeSteps=0 to disable; increase uDitherAmount to reduce contouring.
        float n = fbm(pw);// 0..1
        n = posterize01f(n, uPosterizeSteps, uDitherAmount);

        // Brightness shaping:
        // - uBlackPoint lifts the darkest noise values toward 0 to get inkier shadows.
        // - uGlowGamma (>1) compresses midtones and boosts highlights for a punchy emissive look.
        float bright = saturate((n - uBlackPoint) / (1.0 - uBlackPoint));
        bright = pow(bright, uGlowGamma);
        //bright = posterize01(bright, uPosterizeSteps, uDitherAmount);

        // (optional) make the shell bands more graphic:
        //shells = posterize01(shells, max(uPosterizeSteps - 1.0, 0.0), uDitherAmount * 0.5);

        // Base mask (where aura is allowed) * NOISE ONLY (transparent base)
        float densitySample = bright * cavity;

        // Height-dependent fade so fog dissipates toward the top as it scrolls up
        float heightFade = mix(1.0, uHeightFadeMin, pow(y01, max(uHeightFadePow, 0.0001)));
        densitySample *= heightFade;

        // Apply patchiness mask (stronger at the crown)
        densitySample *= patchMix;

        // Subtle rim enhancement (optional)
        // Uses an SDF-derived normal and view vector to produce a glancing-angle boost. Keep it subtle; it’s
        // easy to over-brighten the silhouette. Disabled by default via uRimStrength=0.
        if (uRimStrength > 0.0){
            vec3 nrm = sdfNormalSphere(p, R);
            float rim = pow(saturate(1.0 - abs(dot(normalize(rd), nrm))), uRimPower);
            densitySample *= (1.0 + rim * uRimStrength);
        }

        // Limb fade kills silhouette regardless of step count
        // If enabled, this attenuates density near silhouette rays (shorter pathNorm) to avoid a bright outline,
        // independent of the number of march steps. We keep it optional because cavity + absorption already help.
        //densitySample *= limb;

        // Per-step alpha (cap to avoid solid fill)
        // Convert density into an opacity contribution using an exponential falloff. densNorm already
        // accounts for step count and path normalization so fewer/more steps maintain similar opacity.
        float a = 1.0 - exp(-densNorm * max(densitySample, 0.0));
        //a = min(a, 0.15);
        // Apply the radial cavity window so near-surface gap and inner limit are respected
        a *= limb * cavity;

        // Emissive color follows noise; absorb with depth inside
        // We tint by uColorB and then apply Beer–Lambert extinction exp(-sigma * inDist) so deeper samples
        // are darker, selling volumetric depth. uAbsorption is sigma; tune it relative to R from Java.
        vec3 col = uColorB * bright;
        col *= exp(-uAbsorption * inDist);

        // Front-to-back premultiplied accumulate
        // Standard over operator in premultiplied form: newColor += srcColor * (1 - accumA),
        // newAlpha += srcAlpha * (1 - accumA). We also early-out when nearly opaque to save work.
        float premul = a * (1.0 - accumA);
        accumRGB += col * premul;
        accumA   += premul;

        if (accumA > 0.98) break;
    }

    // Final alpha applies the global aura fade to the accumulated coverage.
    // You may posterize/dither alpha as well for a harder, more stylized silhouette.
    float alpha = accumA * fade;
    //alpha = posterize01(alpha, uPosterizeSteps, uDitherAmount);

    // Fade gamma lets color fade nonlinearly with uAuraFade so we can keep color visible a bit longer
    // than alpha if desired (uFadeGamma>1 darkens color faster). Output is premultiplied by alpha.
    float fadeColor = pow(fade, max(uFadeGamma, 0.0001));
    FragColor = vec4(accumRGB * fadeColor, alpha);
}
