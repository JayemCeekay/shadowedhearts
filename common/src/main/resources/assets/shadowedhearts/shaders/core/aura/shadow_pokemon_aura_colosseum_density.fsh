#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_density.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float AuraNoiseScale;
uniform float AuraMaskFalloff;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;

    // The Colosseum style starts from the proven Signature density material.
    // Its isolated wrapper allows independent tuning without changing the
    // Signature or XD programs.
    fragColor = shadowPokemonAuraDensity(
            texColor,
            vertexColor * ColorModulator,
            worldPos,
            GameTime,
            AuraNoiseScale,
            AuraMaskFalloff
    );
}
