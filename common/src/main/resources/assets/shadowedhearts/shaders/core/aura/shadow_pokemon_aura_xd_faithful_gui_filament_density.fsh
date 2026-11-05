#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_xd_filament.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float AuraTime;
uniform float FilamentSeed;
uniform float FilamentPhase;

flat in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Ribbon UV spans its whole path; the circular puff mask does not apply.
    vec4 texColor = vec4(1.0);

    fragColor = shadowPokemonAuraXdFilamentDensity(
            texColor,
            vertexColor * ColorModulator,
            texCoord0,
            AuraTime,
            FilamentSeed,
            FilamentPhase
    );
    if (max(max(fragColor.r, fragColor.g), max(fragColor.b, fragColor.a)) < 0.001) discard;
}
