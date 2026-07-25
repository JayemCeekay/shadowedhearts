#version 150

uniform sampler2D Sampler0;   // blurred density buffer texture (R=density, G=heat)
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

    // ===== Metaball threshold - tighter than penumbra for defined energy streaks =====
    float fog = smoothstep(0.05, 0.30, density);
    fog = pow(fog, 0.7);  // moderate gamma for readable energy shapes

    if (fog < 0.005) discard;

    // ===== Density gradient for subtle rim lighting =====
    float dx = dFdx(density);
    float dy = dFdy(density);
    float gradient = length(vec2(dx, dy));
    float rimStrength = smoothstep(0.0, 0.12, gradient);

    // ===== Density-responsive coloring =====
    float densityNorm = smoothstep(0.02, 0.35, density);

    // Color palette - purple/magenta snag energy with orange/white hot cores
    vec3 colorCore   = vec3(1.0, 0.95, 0.90);        // bright warm white core
    vec3 colorMid    = vec3(0.90, 0.55, 0.30);       // warm orange mid
    vec3 colorEdge   = vec3(0.55, 0.22, 0.85);       // deep purple edge
    vec3 colorHot    = vec3(1.0, 0.78, 0.35);        // bright orange-gold highlight
    vec3 colorRim    = vec3(0.70, 0.35, 0.90);        // purple-magenta rim glow

    // Wide purple rim, orange only at high density, white only at peak
    vec3 layerCol = mix(colorEdge, colorMid, smoothstep(0.35, 0.72, densityNorm));
    layerCol = mix(layerCol, colorCore, smoothstep(0.78, 1.0, densityNorm));

    // Heat channel drives orange highlights only at high density
    float heatInfluence = smoothstep(0.12, 0.45, heat);
    layerCol = mix(layerCol, colorHot, heatInfluence * 0.45);

    // Push only the absolute hottest/densest areas toward white
    float whiteHot = smoothstep(0.50, 0.85, heat) * smoothstep(0.6, 1.0, densityNorm);
    layerCol = mix(layerCol, vec3(1.0, 0.97, 0.92), whiteHot * 0.4);

    // ===== Gradient-based rim lighting - thick purple-magenta glow =====
    layerCol += colorRim * rimStrength * 0.85;

    // ===== Emissive rim at fog boundary - wider purple fringe =====
    float rim = 1.0 - smoothstep(0.10, 0.55, fog);
    layerCol += colorEdge * rim * 0.4;

    // ===== Additive composite - energy glows on top of scene =====
    float alpha = fog * 0.85;
    alpha = min(alpha, 1.0);

    // Moderate brightness for energy feel
    layerCol *= 1.05;

    if (alpha < 0.005) discard;

    fragColor = vec4(layerCol, alpha);
}
