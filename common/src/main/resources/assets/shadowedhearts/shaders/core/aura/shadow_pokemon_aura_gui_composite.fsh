#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>

uniform sampler2D Sampler0;
uniform float AuraTime;
uniform vec2 AuraScreenSize;
uniform vec2 AuraRenderSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 compositeColor;
    if (!shadowPokemonAuraComposite(
            texture(Sampler0, texCoord0),
            texCoord0,
            AuraTime,
            AuraScreenSize,
            AuraRenderSize,
            compositeColor
    )) discard;

    fragColor = compositeColor;
}
