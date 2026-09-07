#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>
#moj_import <shadowedhearts:shadow_pokemon_aura_style_composite.glsl>

uniform sampler2D Sampler0;
uniform float AuraTime;
uniform vec2 AuraScreenSize;
uniform vec2 AuraRenderSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 densityData = texture(Sampler0, texCoord0);
    vec4 compositeColor;
    if (!shadowPokemonAuraComposite(
            densityData,
            texCoord0,
            AuraTime,
            AuraScreenSize,
            AuraRenderSize,
            compositeColor
    )) discard;

    fragColor = shadowPokemonAuraColosseumStyle(densityData, compositeColor);
}
