// Classic materials share noise primitives, not Signature's density response.
#moj_import <shadowedhearts:shadow_pokemon_aura_density.glsl>

float shadowClassicMask(float alpha, float maskFalloff) {
    float gate = smoothstep(0.01, 0.12, alpha);
    // The shared authored texture peaks at 195/255, not one. Treat that as the
    // cloud center; treating the peak as an edge attenuated broad smoke.
    float radial = clamp(alpha / (195.0 / 255.0), 0.0, 1.0);
    return gate * exp(-max(maskFalloff * 0.70, 0.25) * (1.0 - radial) * (1.0 - radial));
}

vec4 shadowPokemonAuraColosseumDensity(
        vec4 texColor, vec4 particleColor, vec3 samplePosition,
        float auraTime, float noiseScale, float maskFalloff
) {
    float seconds = auraTime * 1200.0 / 20.0;
    vec3 p = samplePosition * max(noiseScale, 0.001) * 1.65;
    // Noise gently roughens each puff; its own texture defines the center and
    // surrounding violet falloff. It must not paint unrelated black patches.
    p.y -= seconds * 0.14;
    vec3 warp = vec3(
            shadowAuraNoise3(p * 0.65 + vec3(0.0, seconds * 0.07, 0.0)),
            shadowAuraNoise3(p * 0.65 + vec3(17.0, 5.0, 9.0)),
            shadowAuraNoise3(p * 0.65 + vec3(3.0, 13.0, 21.0)));
    vec3 folded = p + (warp - 0.5) * 0.90;
    float billow = shadowAuraNoise3(folded);
    float mask = shadowClassicMask(texColor.a, maskFalloff);
    float strength = mask * max(particleColor.a, 0.0);
    // Low G carries this individual smoke puff's normalized age / 2. High G
    // remains a preview mote marker, with R as its rare warm-accent flag.
    float isMote = step(0.75, particleColor.g);
    float age = clamp(particleColor.g * 2.0, 0.0, 1.0);
    // Preserve the black center through most of the rise. Squaring the smooth
    // transition slows its early erosion while retaining a violet fading tail.
    float dissolution = smoothstep(0.32, 1.0, age);
    dissolution *= dissolution;
    float radial = clamp(texColor.a / (195.0 / 255.0), 0.0, 1.0);
    float contour = clamp(radial + (billow - 0.5) * 0.12 * radial, 0.0, 1.0);
    float core = smoothstep(0.30 + dissolution * 0.16,
                           0.72 + dissolution * 0.10, contour);
    // The center shrinks and becomes violet while the puff is still present.
    // CPU age-driven alpha subsequently dissipates the entire violet puff.
    core *= 1.0 - dissolution;
    float mass = strength * particleColor.r * (1.0 - isMote)
            * mix(0.90, 1.10, billow);
    float broad = mass * (1.0 - core) * 2.50;
    float darkDensity = mass * core * 4.20;
    float wisp = strength * particleColor.b * isMote * mix(0.24, 0.74, billow);
    // The authored puff peaks at 195/255 alpha. Normalize its center before
    // tightening highlights, otherwise fourth/fifth powers erase tiny motes.
    float sparkMask = pow(clamp(texColor.a / 0.75, 0.0, 1.0), 4.0);
    float heat = sparkMask * particleColor.g * max(particleColor.a, 0.0) * isMote;
    // Local B/G excess carries warm light through the same soft filter.
    wisp += heat * isMote * step(0.5, particleColor.r) * 0.90;
    // R/A are the violet falloff and dark center of the same puff, deposited
    // together. They are not separate particle populations.
    return vec4(broad, heat, wisp, darkDensity);
}

vec4 shadowPokemonAuraXdDensity(
        vec4 texColor, vec4 particleColor, vec3 samplePosition,
        float auraTime, float noiseScale, float maskFalloff
) {
    float seconds = auraTime * 1200.0 / 20.0;
    vec3 p = samplePosition * max(noiseScale, 0.001) * vec3(2.15, 1.65, 2.15);
    p.y -= seconds * 0.50;
    float fold = shadowAuraNoise3(p + vec3(seconds * 0.12, 0.0, seconds * 0.16));
    p.xz += vec2(fold - 0.5, shadowAuraNoise3(p + 13.7) - 0.5) * 0.85;
    float body = shadowAuraNoise3(p);
    float fine = shadowAuraNoise3(p * 2.35 + vec3(0.0, -seconds * 0.15, 5.0));
    float mask = shadowClassicMask(texColor.a, maskFalloff);
    float strength = mask * max(particleColor.a, 0.0);
    // The foundation carries translucent violet depth between rolling sheets.
    // Advected openings vary its overlap without erasing the entire field.
    float broad = strength * particleColor.r * mix(0.32, 0.90, body) * 3.4;
    float wisp = strength * particleColor.b
            * smoothstep(0.20, 0.80, body * 0.70 + fine * 0.30);
    float heat = pow(clamp(texColor.a / 0.75, 0.0, 1.0), 5.0)
            * particleColor.g * max(particleColor.a, 0.0);
    float coverage = strength * (particleColor.r * 0.95 + particleColor.b * 0.52);
    return vec4(broad, heat, wisp, coverage);
}
