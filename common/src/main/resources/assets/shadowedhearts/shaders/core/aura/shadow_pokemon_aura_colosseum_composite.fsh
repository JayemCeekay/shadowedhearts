#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>
#moj_import <shadowedhearts:shadow_pokemon_aura_style_composite.glsl>

uniform sampler2D Sampler0;
uniform float GameTime;
uniform vec2 ScreenSize;
uniform vec2 RenderSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 densityData = texture(Sampler0, texCoord0);
    vec4 compositeColor;
    if (!shadowPokemonAuraComposite(
            densityData,
            texCoord0,
            GameTime,
            ScreenSize,
            RenderSize,
            compositeColor
    )) discard;

    fragColor = shadowPokemonAuraColosseumStyle(densityData, compositeColor);
}
