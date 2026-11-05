// Hyper/Reverse Mode uses the legacy aura highlight's reddish magenta shift:
// (0.85, 0.30, 1.30) becomes (1.30, 0.30, 0.85).
uniform float AuraModeTint;

vec3 shadowPokemonAuraModeColor(vec3 purpleColor) {
    return mix(purpleColor, purpleColor.bgr, clamp(AuraModeTint, 0.0, 1.0));
}
