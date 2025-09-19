// shadow_aura_fog.fsh  — relative-scale (size-invariant) aura
#version 150

// ===== From VS =====
in vec3 vPosWS;
in vec3 vRayDirWS;

// ===== Uniforms =====
uniform mat4  uInvModel;          // world -> object
uniform vec3  uCameraPosWS;
uniform float uTime;

// Proxy shape
uniform float uProxyRadius;       // capsule/sphere radius (R)
uniform float uCapsuleHalf;       // 0 => sphere, >0 => capsule half-height (H)

// Global fade & colors
uniform float uAuraFade;          // [0..1]
uniform float uFadeGamma;         // e.g., 1.5
uniform vec3  uColorA;            // deep base
uniform vec3  uColorB;            // highlight

// Density/absorption
uniform float uDensity;           // base density
uniform float uAbsorption;        // Beer extinction along inDist

// Rim (subtle)
uniform float uRimStrength;       // 0..1
uniform float uRimPower;          // 1..4

// Thickness / edges (ABSOLUTE units; keep your radius scaling in Java)
uniform float uMaxThickness;      // skin thickness under surface
uniform float uThicknessFeather;  // softness at 0 and uMaxThickness
uniform float uEdgeKill;          // erase very near the surface
uniform float uShellFeather;      // softness of each radial band

// ===== Relative controls (size-invariant look) =====
uniform float uNoiseScaleRel;         // features per radius (e.g., 3.0)
uniform float uScrollSpeedRel;        // radii per second (e.g., 0.5)
uniform float uLayersAcrossThickness; // shells across the skin thickness (e.g., 14)

// Limb controls (kill silhouette cleanly) + normalization clamp
uniform float uLimbSoft;       // 0.15–0.30
uniform float uLimbHardness;   // 1.5–3.0
uniform float uMinPathNorm;    // 0.10–0.25

// Emission shaping (inkier darks, punchier brights)
uniform float uGlowGamma;      // 1.4–2.4
uniform float uBlackPoint;     // 0.00–0.20

// Small warp for extra curl
uniform float uWarpAmp;        // 0..0.5

// === Pixel look toggles ===
uniform float uPixelsPerRadius;   // 0=off. Try 16–24 to match MC pixels per block (relative to aura radius)
uniform float uPosterizeSteps;    // 0=off. Try 4.0 (3–6 works well)
uniform float uDitherAmount;      // 0..1. Try 0.6–0.8


out vec4 FragColor;

// ===== Tunables =====
#define N_STEPS 24
#define FBM_OCTAVES 12

// ===== Helpers =====
float saturate(float x){ return clamp(x,0.0,1.0); }

float hash31(vec3 p){
    p = fract(p*0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x+p.y)*p.z);
}
float noise3(vec3 p){
    vec3 i = floor(p), f = fract(p);
    vec3 u = f*f*(3.0-2.0*f);
    float n000 = hash31(i+vec3(0,0,0));
    float n100 = hash31(i+vec3(1,0,0));
    float n010 = hash31(i+vec3(0,1,0));
    float n110 = hash31(i+vec3(1,1,0));
    float n001 = hash31(i+vec3(0,0,1));
    float n101 = hash31(i+vec3(1,0,1));
    float n011 = hash31(i+vec3(0,1,1));
    float n111 = hash31(i+vec3(1,1,1));
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
    for(int i=0;i<FBM_OCTAVES;++i){
        f += a * noise3(p);
        p = p*2.02 + vec3(31.416,47.0,19.19);
        a *= 0.5;
    }
    return f;
}

// SDFs
float sdSphere(vec3 p, float r){ return length(p) - r; }
float sdCapsuleY(vec3 p, float h, float r){
    vec3 q = p; q.y -= clamp(p.y, -h, h);
    return length(q) - r;
}
// Numerical SDF normal (only if rim enabled)
vec3 sdfNormal(vec3 p, float h, float r, bool capsule){
    const float e=0.0025;
    float dx = (capsule?sdCapsuleY(p+vec3(e,0,0),h,r):sdSphere(p+vec3(e,0,0),r))
    - (capsule?sdCapsuleY(p-vec3(e,0,0),h,r):sdSphere(p-vec3(e,0,0),r));
    float dy = (capsule?sdCapsuleY(p+vec3(0,e,0),h,r):sdSphere(p+vec3(0,e,0),r))
    - (capsule?sdCapsuleY(p-vec3(0,e,0),h,r):sdSphere(p-vec3(0,e,0),r));
    float dz = (capsule?sdCapsuleY(p+vec3(0,0,e),h,r):sdSphere(p+vec3(0,0,e),r))
    - (capsule?sdCapsuleY(p-vec3(0,0,e),h,r):sdSphere(p-vec3(0,0,e),r));
    return normalize(vec3(dx,dy,dz));
}

// Ray-sphere (object space)
bool intersectSphere(vec3 ro, vec3 rd, float r, out float t0, out float t1){
    float b = dot(ro, rd);
    float c = dot(ro, ro) - r*r;
    float h = b*b - c;
    if(h < 0.0) return false;
    h = sqrt(h);
    t0 = -b - h; t1 = -b + h;
    return t1 > 0.0;
}

float bayer4x4(vec2 frag) {
    // 4x4 Bayer matrix, 0..15 -> 0..1
    ivec2 ip = ivec2(mod(frag, 4.0));
    int idx = (ip.y << 2) | ip.x;
    const float M[16] = float[16](
    0.0,  8.0,  2.0, 10.0,
    12.0, 4.0, 14.0,  6.0,
    3.0, 11.0,  1.0,  9.0,
    15.0, 7.0, 13.0,  5.0
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


void main(){
    float fade = saturate(uAuraFade);
    if(fade <= 0.0001){ discard; return; }

    // Build ray in object space
    vec3 roWS = uCameraPosWS;
    vec3 rdWS = normalize(vRayDirWS);
    vec3 ro   = (uInvModel * vec4(roWS,1.0)).xyz;
    vec3 rd   = normalize((uInvModel * vec4(rdWS,0.0)).xyz);

    // Bounding sphere for both sphere & capsule
    float R = max(uProxyRadius, 1e-6);
    float H = max(uCapsuleHalf, 0.0);
    float boundR = (H > 0.0) ? (H + R) : R;

    float t0, t1;
    if(!intersectSphere(ro, rd, boundR, t0, t1)){ discard; return; }
    t0 = max(t0, 0.0);

    // March setup + limb fade
    float rawPath = max(t1 - t0, 1e-4);
    float stepLen = rawPath / float(N_STEPS);

    float minLen  = (2.0*boundR) * uMinPathNorm;                // clamp for normalization
    float densNorm= uDensity * (float(N_STEPS) / max(rawPath, minLen)) * fade;

    float pathNorm= clamp(rawPath / (2.0*boundR), 0.0, 1.0);    // 0 at silhouette, 1 through center
    float limb    = pow(smoothstep(0.0, uLimbSoft, pathNorm), uLimbHardness);

    // Accum
    vec3 accumRGB = vec3(0.0);
    float accumA  = 0.0;

    bool capsuleMode = (H > 0.0);
    float jitter = hash31(vec3(gl_FragCoord.xy, uTime)) - 0.5;

    for(int i=0;i<N_STEPS;++i){
        float ti = t0 + ( (float(i) + 0.5 + jitter) * stepLen );
        if(ti > t1) break;

        vec3 p = ro + rd * ti; // object space sample

        // SDF distance (negative inside)
        float d = capsuleMode ? sdCapsuleY(p, H, R) : sdSphere(p, R);
        if(d > 0.0) continue;

        float inDist = -d;

        // Thin skin band under the surface
        float bandInner = smoothstep(0.0, uThicknessFeather, inDist);
        float bandOuter = 1.0 - smoothstep(uMaxThickness - uThicknessFeather, uMaxThickness, inDist);
        float shellBand = bandInner * bandOuter;

        // Erase very near the geometric surface (avoid visible rim)
        float lipKill = smoothstep(0.0, uEdgeKill, inDist);

        // --- Relative coords (size-invariant) ---
        vec3 pRel = capsuleMode ? (p / vec3(R, H + R, R)) : (p / R);

        // Even shell count across thickness, independent of units
        float layersAcross = max(uLayersAcrossThickness, 1e-3);
        float layerFreq    = layersAcross / max(uMaxThickness, 1e-6);
        float f            = fract(inDist * layerFreq);
        float shells       = smoothstep(0.0, uShellFeather, f)
        * (1.0 - smoothstep(1.0 - uShellFeather, 1.0, f));


        // pixelate the coordinate in RELATIVE object space (size-invariant)
        vec3 pPix = quantize3D(pRel, uPixelsPerRadius);

        // coarser FBM + slightly reduced warp for a chunkier look
        vec3 pw = pPix * uNoiseScaleRel + vec3(0.0, uScrollSpeedRel * uTime, 0.0);
        pw += (fbm(pPix * (uNoiseScaleRel * 0.5)) - 0.5) * (uWarpAmp * 0.8);

        // quantize the noise and its brightness
        float n = fbm(pw); // 0..1
        n = posterize01(n, uPosterizeSteps, uDitherAmount);

        float bright = saturate((n - uBlackPoint) / (1.0 - uBlackPoint));
        bright = pow(bright, uGlowGamma);
        //bright = posterize01(bright, uPosterizeSteps, uDitherAmount);

        // (optional) make the shell bands more graphic:
         //shells = posterize01(shells, max(uPosterizeSteps - 1.0, 0.0), uDitherAmount * 0.5);


        // FBM in relative space with relative upward scroll + tiny warp
        //vec3 pw = pRel * uNoiseScaleRel + vec3(0.0, uScrollSpeedRel * uTime, 0.0);
        //pw += (fbm(pRel * (uNoiseScaleRel * 0.5)) - 0.5) * (uWarpAmp * 2.0);

        //pw = pixelate(pw, uPixelSize);
        //float n = fbm(pw); // 0..1
        // Noise-only brightness with black point + gamma
       // float bright = saturate((n - uBlackPoint) / (1.0 - uBlackPoint));
        //bright = pow(bright, uGlowGamma);

        // Base mask (where aura is allowed) * NOISE ONLY (transparent base)
        float densitySample = (shellBand * lipKill * shells) * bright;

        // Subtle rim enhancement (optional)
        if(uRimStrength > 0.0){
            vec3 nrm = sdfNormal(p, H, R, capsuleMode);
            float rim = pow(saturate(1.0 - abs(dot(normalize(rd), nrm))), uRimPower);
            densitySample *= (1.0 + rim * uRimStrength);
        }

        // Limb fade kills silhouette regardless of step count
        densitySample *= limb;

        // Per-step alpha (cap to avoid solid fill)
        float a = 1.0 - exp(-densNorm * max(densitySample, 0.0));
        //a = min(a, 0.15);
        a *= limb;

        // Emissive color follows noise; absorb with depth inside
        vec3 col = uColorB * bright;
        col *= exp(-uAbsorption * inDist);

        // Front-to-back premultiplied accumulate
        float premul = a * (1.0 - accumA);
        accumRGB += col * premul;
        accumA   += premul;

        if(accumA > 0.98) break;
    }

    float alpha = accumA * fade;
    //alpha = posterize01(alpha, uPosterizeSteps, uDitherAmount);

    float fadeColor = pow(fade, max(uFadeGamma, 0.0001));
    FragColor = vec4(accumRGB * fadeColor, alpha);
}
