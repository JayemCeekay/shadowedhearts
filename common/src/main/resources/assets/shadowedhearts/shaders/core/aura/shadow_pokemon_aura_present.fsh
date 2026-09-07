#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_present.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 GridOrigin;
uniform vec2 PixelSize;
uniform vec2 LowerPixelSize;
uniform vec2 UpperPixelSize;
uniform float LodBlend;
uniform float FractionalMipEnabled;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (FractionalMipEnabled > 0.5) {
        fragColor = shadowPokemonAuraFractionalMipPresent(
                Sampler0,
                Sampler1,
                gl_FragCoord.xy,
                GridOrigin,
                LowerPixelSize,
                UpperPixelSize,
                LodBlend);
    } else {
        fragColor = shadowPokemonAuraPresent(
                Sampler0, gl_FragCoord.xy, GridOrigin, PixelSize);
    }
}
