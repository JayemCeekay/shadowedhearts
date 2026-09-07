#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>
#moj_import <shadowedhearts:shadow_pokemon_aura_style_composite.glsl>

uniform sampler2D Sampler0;
uniform float GameTime;
uniform vec2 ScreenSize;
uniform vec2 PixelSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 safeScreenSize = max(ScreenSize, vec2(1.0));
    vec2 safePixelSize = max(PixelSize, vec2(1.0));
    vec2 sourcePixelCenter = min(
            floor(gl_FragCoord.xy) * safePixelSize + safePixelSize * 0.5,
            safeScreenSize - vec2(0.5));
    vec2 sampleUv = sourcePixelCenter / safeScreenSize;

    vec4 densityData = texture(Sampler0, sampleUv);
    vec4 compositeColor;
    if (!shadowPokemonAuraComposite(
            densityData,
            sampleUv,
            GameTime,
            ScreenSize,
            safeScreenSize / safePixelSize,
            compositeColor
    )) discard;

    fragColor = shadowPokemonAuraColosseumStyle(densityData, compositeColor);
}
