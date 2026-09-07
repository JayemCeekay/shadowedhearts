#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_xd_filament.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FilamentSeed;
uniform float FilamentPhase;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;

    fragColor = shadowPokemonAuraXdFilamentDensity(
            texColor,
            vertexColor * ColorModulator,
            texCoord0,
            GameTime,
            FilamentSeed,
            FilamentPhase
    );
    if (fragColor.g < 0.001 && fragColor.b < 0.001) discard;
}
