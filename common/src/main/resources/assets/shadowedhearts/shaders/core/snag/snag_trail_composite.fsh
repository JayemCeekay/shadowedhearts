#version 150

uniform sampler2D Sampler0;   // blurred density buffer texture (R=density, G=heat)
uniform float GameTime;
uniform vec2 ScreenSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Sample accumulated + blurred density (R) and heat (G)
    vec2 densityData = texture(Sampler0, texCoord0).rg;
    float density = densityData.r;
    float heat = densityData.g;

    // Discard empty regions early
    if (density < 0.001) discard;

    // ===== Metaball threshold — smooth edges for soft smoke =====
    float fog = smoothstep(0.0, 0.45, density);
    fog = pow(fog, 0.85);

    if (fog < 0.005) discard;

    // ===== Density gradient for depth cues (dFdx/dFdy rim lighting) =====
    float dx = dFdx(density);
    float dy = dFdy(density);
    float gradient = length(vec2(dx, dy));
    float rimStrength = smoothstep(0.0, 0.15, gradient);

    // ===== Density-responsive coloring =====
    float densityNorm = smoothstep(0.02, 0.35, density);

    // Color palette — warm orange smoke with golden highlights
    vec3 colorDark    = vec3(0.20, 0.07, 0.02);    // lifted warm brown inner
    vec3 colorDeep    = vec3(0.42, 0.16, 0.04);    // brighter burnt orange
    vec3 colorMid     = vec3(0.78, 0.36, 0.08);    // warm orange
    vec3 colorHot     = vec3(1.00, 0.66, 0.20);    // bright golden-orange highlight
    vec3 colorPeak    = vec3(1.00, 0.90, 0.58);    // hot white-gold peak

    // Dense regions → dark core, sparse regions → orange highlights
    vec3 coreColor = mix(colorDark, colorDeep, smoothstep(0.0, 0.4, densityNorm));
    vec3 bodyColor = mix(colorDeep, colorMid, smoothstep(0.2, 0.6, densityNorm));
    vec3 edgeColor = mix(colorMid, colorHot, smoothstep(0.0, 0.3, 1.0 - densityNorm));

    // Layer blend: interior is dark, exterior transitions to orange
    vec3 layerCol = mix(edgeColor, coreColor, densityNorm * 0.75);
    layerCol = mix(layerCol, bodyColor,
        smoothstep(0.2, 0.5, densityNorm) * (1.0 - smoothstep(0.5, 0.8, densityNorm)));

    // Heat channel drives bright highlights where particles overlap densely
    float heatInfluence = smoothstep(0.1, 0.5, heat) * (1.0 - densityNorm * 0.7);
    layerCol = mix(layerCol, colorPeak, heatInfluence * 0.5);

    // ===== Gradient-based rim lighting — edges glow warm orange =====
    layerCol += colorHot * rimStrength * 0.4;

    // ===== Emissive rim at fog boundary =====
    float rim = 1.0 - smoothstep(0.15, 0.5, fog);
    layerCol += colorHot * rim * 0.15;

    // ===== Opacity — soft, translucent smoke =====
    float opacity = 0.62;
    float alpha = fog * opacity;
    alpha = min(alpha, 1.0);

    // Subtle emission boost for warm glow feel
    layerCol *= 1.45;

    if (alpha < 0.005) discard;

    fragColor = vec4(layerCol, alpha);
}
