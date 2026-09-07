#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_present.glsl>

uniform sampler2D Sampler0;
uniform vec2 GridOrigin;
uniform vec2 PixelSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    fragColor = shadowPokemonAuraPresent(
            Sampler0, gl_FragCoord.xy, GridOrigin, PixelSize);
}
