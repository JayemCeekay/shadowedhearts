#version 150

uniform sampler2D Sampler0;   // blurred density buffer texture (R=density, G=heat)
uniform float GameTime;
uniform vec2 ScreenSize;

in vec2 texCoord0;

out vec4 fragColor;

// --- Hash-based value noise for heat variation ---
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);  // smoothstep interpolant
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * vnoise(p);
        p *= 2.0;
        amp *= 0.5;
    }
    return v;
}

void main() {
    // Sample accumulated + blurred density (R) and heat (G)
    vec2 densityData = texture(Sampler0, texCoord0).rg;
    float density = densityData.r;
    float heat = densityData.g;

    // Discard empty regions early
    if (density < 0.001) discard;

    // ===== Metaball threshold — sharper edges for defined wisps (suggestion I) =====
    // Thresholds tuned for reduced density scale (splat outputs ~0.35 max per layer).
    float fog = smoothstep(0.0, 0.45, density);
    fog = pow(fog, 0.85);  // less gamma expansion = less bloat

    if (fog < 0.005) discard;

    // ===== Density gradient for depth cues (dFdx/dFdy rim lighting) =====
    float dx = dFdx(density);
    float dy = dFdy(density);
    float gradient = length(vec2(dx, dy));
    float rimStrength = smoothstep(0.0, 0.15, gradient);

    // ===== Density-responsive coloring =====
    float densityNorm = smoothstep(0.02, 0.35, density);

    // Color palette (shadow aura fog cylinder style)
    vec3 colorBlack = vec3(0.02, 0.005, 0.03);   // near-black inner core
    vec3 colorDeep  = vec3(0.08, 0.02, 0.12);    // very dark purple
    vec3 colorMid   = vec3(0.22, 0.07, 0.30);    // dark purple
    vec3 colorHot   = vec3(0.55, 0.20, 0.70);    // bright purple highlight
    vec3 colorPink  = vec3(0.85, 0.40, 1.00);    // hot pink/magenta peak

    // Dense regions → dark core, sparse regions → purple highlights
    vec3 coreColor = mix(colorBlack, colorDeep, smoothstep(0.0, 0.4, densityNorm));
    vec3 bodyColor = mix(colorDeep, colorMid, smoothstep(0.2, 0.6, densityNorm));
    vec3 edgeColor = mix(colorMid, colorHot, smoothstep(0.0, 0.3, 1.0 - densityNorm));

    // Layer blend: interior is dark, exterior transitions to purple
    vec3 layerCol = mix(edgeColor, coreColor, densityNorm * 0.75);
    layerCol = mix(layerCol, bodyColor,
        smoothstep(0.2, 0.5, densityNorm) * (1.0 - smoothstep(0.5, 0.8, densityNorm)));

    // Heat channel drives bright highlights where particles overlap densely
    float heatInfluence = smoothstep(0.1, 0.5, heat) * (1.0 - densityNorm * 0.7);

    // FBM noise breaks up the flat heat region with organic, wispy variation.
    // Slow GameTime scroll keeps the highlights subtly alive.
    //vec2 heatNoiseUV = texCoord0 * ScreenSize / 64.0 + GameTime * vec2(1.3, -0.9) * 200.0;
    //float heatNoise = fbm(heatNoiseUV);
    // Remap noise to [0.3, 1.0] so heat never fully vanishes, just varies in intensity
    //float noiseMask = mix(0.3, 3.0, heatNoise);
   // heatInfluence *= noiseMask;

    layerCol = mix(layerCol, colorPink, heatInfluence * 0.5);

    // ===== Gradient-based rim lighting — edges glow, interiors stay dark =====
    layerCol += colorHot * rimStrength * 0.4;

    // ===== Emissive rim at fog boundary =====
    float rim = 1.0 - smoothstep(0.15, 0.5, fog);
    layerCol += colorHot * rim * 0.15;

    // ===== Scene darkening — fog absorbs light =====
    float darkenFactor = fog * 0.2;
    float opacity = 0.60;
    float alpha = fog * opacity + darkenFactor;
    alpha = min(alpha, 1.0);

    // Reduce brightness so darkening comes through
    layerCol *= (1.0 - darkenFactor * 0.5);

    // Subtle emission boost
    layerCol *= 1.2;

    if (alpha < 0.005) discard;

    fragColor = vec4(layerCol, alpha);
}
