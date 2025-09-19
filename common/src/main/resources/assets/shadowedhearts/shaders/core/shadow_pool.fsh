#version 150

// Shadow Pool (fragment shader)
// Purpose: Renders an inky, swirling “shadow pool” decal with ripples, blobby body,
// subtle inner glow, glossy specular highlights, and faint emissive wisps/rim.
// The geometry remains flat; surface detail is faked via a heightfield-derived normal.
//
// Visual layers (conceptual order):
//  1) Radial mask and soft edge, with center darkening.
//  2) Swirl: angular spin strongest at the center, fades toward the rim.
//  3) Goop body: blobby Voronoi “islands” advected outward and slightly around.
//  4) Ring train: outward-moving gaussian ripples.
//  5) Heightfield and normal: fed by ring + goop for glossy lighting.
//  6) Lighting: two-lobe specular with Fresnel-tinted clear-coat.
//  7) Emission: narrow spiral wisps plus an optional faint rim.
//  8) Inner glow: subtle center-biased tint.
//  9) Alpha: shape, thickness, and ordered-dithered transparency.
//
// Notes:
//  - World-space vWorldPos ensures scale-invariant noise when pool size changes.
//  - All “normalized radius/sec” parameters assume 1.0 == pool radius per second.
//  - Keep emissive values subtle to avoid hard halos when bloom is enabled.

in vec2  vUV;        // 0..1 over the quad (screen-aligned or world decal)
in vec3  vWorldPos;  // world-space pos for goop noise stability across sizes
in vec4  vColor;     // per-instance tint and alpha multiplier
out vec4 fragColor;

// ---------- Animation / placement ----------
// uTime: seconds since start. Drives swirl, ripple motion, and advection speed.
uniform float uTime;                 // seconds
// Center and radius define pool placement and scale in world units.
uniform vec2  uPoolCenterXZ;        // world XZ of pool center
uniform float uRadius;               // pool radius in world units

// ---------- Shape / edge ----------
// uEdgeFeather: 0..1, where closer to 1 means longer soft fade near the rim.
// uCoreDark: 0..1 multiplier that darkens the very center (1=no darkening).
uniform float uEdgeFeather = 0.96;   // soft edge falloff
uniform float uCoreDark    = 0.88;   // center darkening factor

// ---------- Outward ring train (ripples) ----------
// uRingSpacing: fraction of the pool radius between ring centers (0..1 typical 0.1–0.3).
// uRingWidth: gaussian width of each ring (smaller = thinner/sharper).
// uRingSpeed: rings travel outward at this rate in radii per second.
// uRingAmp: contribution of rings to the heightfield (affects spec only).
uniform float uRingSpacing = 0.18;   // normalized spacing (0..1 of radius)
uniform float uRingWidth   = 0.08;   // gaussian width
uniform float uRingSpeed   = 0.30;   // normalized radius/sec
uniform float uRingAmp     = 0.010;  // height contribution

// ---------- Swirl (angular spin) ----------
// uSwirlOmega: angular speed at r=0 (rad/sec), falls off exponentially.
// uSwirlFalloff: larger values damp swirl sooner with increasing radius.
// uSpiralAmp: small radial wobble amplitude added to r for painterly motion.
// uSpiralArms: number of brighter spiral bands in emissive wisps.
uniform float uSwirlOmega   = 0.95;  // rad/sec at the center
uniform float uSwirlFalloff = 1.15;  // larger = dies sooner with radius
uniform float uSpiralAmp    = 0.035; // small radial wobble
uniform float uSpiralArms   = 4.0;   // # of swirling bands

// ---------- Goop “body” (blobby thickness) ----------
// uGoopScale: approximate number of Voronoi cells across the pool diameter.
// uGoopBump: how strongly goop thickness feeds the heightfield (normals only).
// uOutflow: outward advection speed of the goop (radii/sec), with slight swirl.
uniform float uGoopScale = 3.0;      // cells across diameter
uniform float uGoopBump  = 0.006;    // height from goop (for normal)
uniform float uOutflow   = 0.15;     // outward ooze speed (norm radius/sec)

// ---------- Gloss / clear-coat ----------
// uNormalStr: scales screen-space height derivatives into a fake normal.
// uSpec1Pow/Amp: broad Blinn lobe; uSpec2Pow/Amp: tighter clear-coat highlight.
// uSpecColor: color-tinted specular for a magical, inky look.
uniform float uNormalStr  = 1.35;    // height->normal strength
uniform float uSpec1Pow   = 48.0;    // broad lobe exponent
uniform float uSpec1Amp   = 0.22;    // broad lobe intensity
uniform float uSpec2Pow   = 160.0;   // clear-coat highlight exponent
uniform float uSpec2Amp   = 0.35;    // clear-coat intensity
uniform vec3  uSpecColor  = vec3(0.65, 0.25, 0.95);

// ---------- Colors ----------
// uShadowTint: base inky color. uGlowTint: inner glow/emissive tint.
// uGlowAmp: scales the inner glow strength near the center.
uniform vec3  uShadowTint  = vec3(0.05, 0.02, 0.09); // inky purple-black
uniform vec3  uGlowTint    = vec3(0.30, 0.12, 0.65); // subtle inner glow
uniform float uGlowAmp     = 0.10;

// Optional faint rim (keep subtle)
// uRimRadius: where the rim sits in normalized radius. uRimWidth: gaussian width.
// uRimAmp: final emissive strength of the rim; keep small to avoid haloing.
uniform float uRimAmp      = 0.10;   // emission strength
uniform float uRimRadius   = 0.92;   // where the rim sits (0..1)
uniform float uRimWidth    = 0.045;  // gaussian width

// Fake lighting (you can pass real ones if you have them)
// uLightDir: normalized light direction. uViewDir: normalized view direction.
uniform vec3  uLightDir    = normalize(vec3(0.2, 1.0, 0.15));
uniform vec3  uViewDir     = normalize(vec3(0.0, 1.0, 0.0));

// ---------- Global controls ----------
// uGlobalFade: master visibility. 0 = fully hidden, 1 = fully visible.
uniform float uGlobalFade = 1.0;     // 0 = invisible, 1 = fully visible

// ------------------ Utilities ------------------
// Clamp to [0,1].
float saturate(float x){ return clamp(x, 0.0, 1.0); }

// 4x4 ordered Bayer matrix for subtle dithering of alpha. Helps reduce banding
// and avoids temporal popping when alpha is animated.
float bayer(vec2 p){
    int x = int(mod(p.x,4.0)), y = int(mod(p.y,4.0));
    int i = x + y*4;
    float m[16]=float[16](
    0.,8.,2.,10., 12.,4.,14.,6., 3.,11.,1.,9., 15.,7.,13.,5.
    );
    return m[i]/16.0;
}

// Radial mask with soft edge and center darkening. Takes vUV (0..1) and
// returns a multiplier for both color and alpha.
float radialMask(vec2 uv){
    vec2 d = (uv - 0.5) * 2.0;
    float r = length(d);
    float edge   = 1.0 - smoothstep(uEdgeFeather, 1.0, r);
    float center = mix(1.0, uCoreDark, smoothstep(0.0, 0.35, r));
    return edge * center;
}

// Hash + Voronoi for blobby goop
// hash22: simple 2D hash in [0,1)^2. voronoiF1: distance to nearest feature.
vec2 hash22(vec2 p){
    p = vec2(dot(p, vec2(127.1, 311.7)),
    dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}
float voronoiF1(vec2 p){
    vec2 g = floor(p), f = fract(p);
    float res = 1.0;
    for(int j=-1;j<=1;j++)
    for(int i=-1;i<=1;i++){
        vec2 o = vec2(i,j);
        vec2 r = o + hash22(g + o) - f;
        res = min(res, dot(r,r));
    }
    return sqrt(res); // 0..~1
}

// Repeating gaussian pulse for rings. x is phase (0..1 repeats). width
// controls thickness (avoid 0). Returns 0..1.
float ringPulse(float x, float width){
    float s = abs(fract(x) - 0.5) / max(width, 1e-4); // 0 at pulse center
    return exp(-s*s*4.0);
}

// Narrow spiral emissive bands (wispy bright streaks). r: radius (0..1+),
// th: angle in radians, t: time, arms: number of bands, width: band thinness.
float spiralBands(float r, float th, float t, float arms, float width){
    // Archimedean-ish spiral: phase combines radius and angle
    float phase = arms*th + 6.0*r - 2.2*t;  // tweakable constants
    float s = abs(sin(phase));
    // Gaussian band around 0
    float band = exp(-pow(s/width, 2.0));
    // fade at rim to avoid a neon outline
    band *= (1.0 - smoothstep(0.88, 1.0, r));
    return band;
}

void main(){
    // Step 1: Quad-local polar coords (scale-invariant)
    vec2  q  = (vUV - 0.5) * 2.0;            // -1..1 square mapped to unit disk
    float r  = length(q);                    // 0 at center, ~1 near corners
    float th = atan(q.y, q.x);               // angle in radians [-pi, pi]

    // Step 2: Angular swirl strongest at center, decays toward rim
    float swirl = uSwirlOmega * uTime * exp(-r * uSwirlFalloff);
    float thS   = th + swirl;                // swirled angle

    // Step 3: Small radial wobble (painterly helix) for richness
    float helix = uSpiralAmp * sin(thS * uSpiralArms + uTime * 0.7) * (1.0 - r*r);
    float r2    = r + helix;                 // perturbed radius used for rings & wisps

    // Step 4: Outward ring train (gaussian ripples)
    float tNorm = (r2 / max(uRingSpacing, 1e-4)) - uRingSpeed * uTime; // phase 0..1
    float ringG = ringPulse(tNorm, uRingWidth);  // 0..1 ring intensity

    // Step 5: Goop body via Voronoi, advected outward with slight swirl
    vec2 poolSpace = (vWorldPos.xz - uPoolCenterXZ) / max(uRadius, 1e-5); // normalize to radius
    vec2 dir = normalize(q + 1e-6);             // outward unit vector (avoid NaN at center)
    vec2 adv = dir * (uOutflow * uTime) + vec2(cos(thS), sin(thS)) * (0.05 * uTime);
    float f1 = voronoiF1((poolSpace - adv) * uGoopScale);
    float blobs = 1.0 - smoothstep(0.15, 0.75, 1.0 - f1); // 0..1 islands

    // Step 6: Heightfield for glossy normals (shading only; geometry is flat)
    float h = 0.0;
    h += uRingAmp * ringG;                     // rings modulate height
    h += uGoopBump * (blobs*2.0 - 1.0);        // blobby thickness to height

    // Step 7: Build a fake normal from the heightfield (screen-space derivatives)
    float dx = dFdx(h), dy = dFdy(h);
    vec3  n  = normalize(vec3(-dx * uNormalStr, 1.0, -dy * uNormalStr));

    // Step 8: Specular (two-lobe Blinn + Fresnel tint)
    vec3 L = normalize(uLightDir);
    vec3 V = normalize(uViewDir);
    vec3 H = normalize(L + V);
    float ndl = saturate(dot(n, L));
    float ndh = saturate(dot(n, H));
    float ndv = saturate(dot(n, V));
    vec3  F0  = mix(vec3(0.03), uSpecColor, 0.55);     // base reflectance
    vec3  F   = F0 + (1.0 - F0) * pow(1.0 - ndv, 5.0); // Schlick Fresnel
    vec3  spec = (pow(ndh, uSpec1Pow)*uSpec1Amp + pow(ndh, uSpec2Pow)*uSpec2Amp) * F * ndl;

    // Step 9: Spiral emissive wisps (bright streaks)
    float wisps = spiralBands(r2, thS, uTime, uSpiralArms, 0.28);

    // Step 10: Faint rim (bloom-friendly; avoid hard neon edge)
    float rim = exp(-pow((r - uRimRadius)/max(uRimWidth, 1e-4), 2.0)) * uRimAmp;

    // Step 11: Alpha — radial mask, thickness bias, slight shadowing from rings,
    //          then Bayer dither to counter banding/popping.
    float mask  = radialMask(vUV);
    float alpha = mask * mix(1.0, 0.75, blobs) * (1.0 - 0.06 * ringG);
    alpha = saturate(alpha - bayer(gl_FragCoord.xy) * 0.03);

    // Step 12: Inner “depth” glow biased to the center
    float center = 1.0 - length(q);
    float glow   = pow(saturate(center), 3.5) * uGlowAmp;

    // Step 13: Final color — inky base + inner glow + glossy spec + emissive wisps/rim
    vec3 base   = mix(uShadowTint, uGlowTint, glow) * vColor.rgb;
    vec3 color  = base + spec + (uGlowTint * (wisps * 0.55 + rim));

    // Step 14: Output with per-instance color alpha and global fade
    fragColor = vec4(color, alpha * vColor.a * uGlobalFade);
}
