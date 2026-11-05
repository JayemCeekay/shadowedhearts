// Independent classic responses: R violet, G core/motes, B halo/warm light.
// Colosseum R/A hold the violet falloff and dark center of each puff; XD A
// retains its coverage meaning. Centers dissolve in the per-puff density pass.
#moj_import <shadowedhearts:shadow_pokemon_aura_palette.glsl>
vec4 shadowPokemonAuraColosseumStyle(vec4 densityData) {
    vec4 d = max(densityData, vec4(0.0));
    float violet = 1.0 - exp(-d.r * 2.25);
    float cloud = 1.0 - exp(-d.r * 2.25 - d.a * 4.0);
    float fold = 1.0 - exp(-d.b * 1.8);
    float mote = 1.0 - exp(-d.g * 2.8);
    float darkShare = d.a / max(d.r + d.a, 0.0001);
    float blackCore = smoothstep(0.38, 0.80, darkShare);
    vec3 smokeBlack = shadowPokemonAuraModeColor(vec3(0.006, 0.002, 0.011));
    vec3 deepViolet = shadowPokemonAuraModeColor(vec3(0.18, 0.018, 0.32));
    vec3 smokeViolet = shadowPokemonAuraModeColor(vec3(0.47, 0.075, 0.74));
    vec3 softViolet = shadowPokemonAuraModeColor(vec3(0.57, 0.19, 0.83));
    vec3 paleMote = shadowPokemonAuraModeColor(vec3(0.97, 0.88, 1.0));
    float warmMote = smoothstep(0.25, 0.65,
            (d.b - d.r * 0.10) / max(d.g, 0.0001));
    vec3 moteColor = mix(paleMote, vec3(1.0, 0.62, 0.22), warmMote);
    vec3 color = mix(deepViolet, smokeViolet, 0.40 + violet * 0.48);
    color = mix(color, softViolet, fold * 0.30);
    color = mix(color, smokeBlack, blackCore);
    color = mix(color, moteColor, pow(mote, 1.20) * 0.94);
    // Young dark centers absorb daylight; the broad violet falloff stays soft.
    // The same response shades a fully violet puff after its core dissolves.
    // Leave some view of the model even where overlapping smoke saturates.
    // Apply this only to smoke; luminous accents keep their own response.
    float smokeAlpha = cloud * mix(0.82, 0.97, blackCore) * 0.86;
    float alpha = min(1.0 - (1.0 - smokeAlpha)
            * (1.0 - fold * 0.20) * (1.0 - mote * 0.84), 0.97);
    return vec4(color, alpha);
}

vec4 shadowPokemonAuraXdFaithfulStyle(vec4 densityData) {
    vec4 d = max(densityData, vec4(0.0));
    float foundation = 1.0 - exp(-d.r * 2.4 - d.a * 0.90);
    float halo = 1.0 - exp(-d.b * 2.2);
    float core = 1.0 - exp(-d.g * 3.8);
    vec3 shadowViolet = shadowPokemonAuraModeColor(vec3(0.17, 0.018, 0.34));
    vec3 violet = shadowPokemonAuraModeColor(vec3(0.39, 0.065, 0.66));
    vec3 lilac = shadowPokemonAuraModeColor(vec3(0.69, 0.27, 0.96));
    vec3 pinkEdge = shadowPokemonAuraModeColor(vec3(0.96, 0.64, 1.0));
    vec3 paleCore = shadowPokemonAuraModeColor(vec3(0.99, 0.92, 1.0));
    // R-dominant filaments remain absorptive dark violet. The two populations
    // can overlap without a little luminous density bleaching the entire field.
    float darkShare = d.r / max(d.r + d.g * 1.5 + d.b * 0.45, 0.0001);
    // R traces the darker strand and A its broader connecting shoulder. A-only
    // shoulders remain visible violet against dark models and foliage instead
    // of becoming the same almost-black color as the filament centers.
    float strandShare = d.r / max(d.r + d.a * 2.0, 0.0001);
    vec3 color = mix(violet, shadowViolet, smoothstep(0.18, 0.78, strandShare) * darkShare);
    color = mix(color, lilac, halo * (1.0 - darkShare * 0.60));
    float point = smoothstep(2.2, 3.8, d.g / max(d.b, 0.0001));
    float luminousShare = core * (1.0 - darkShare * 0.52);
    color = mix(color, mix(pinkEdge, paleCore, point), luminousShare * 0.96);
    color += shadowPokemonAuraModeColor(vec3(0.16, 0.11, 0.19)) * smoothstep(0.28, 0.90, core) * luminousShare;
    float alpha = 1.0 - (1.0 - foundation * 0.66)
            * (1.0 - halo * 0.48) * (1.0 - core * 0.72);
    return vec4(color, min(alpha, 0.84));
}
