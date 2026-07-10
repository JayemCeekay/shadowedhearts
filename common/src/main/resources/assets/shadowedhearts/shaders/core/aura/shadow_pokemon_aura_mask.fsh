#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float textureAlpha = texture(Sampler0, texCoord0).a;
    if (textureAlpha < 0.08) {
        discard;
    }

    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    float strength = clamp(textureAlpha * vertexColor.a * ColorModulator.a * fogFade, 0.0, 1.0);
    if (strength < 0.01) {
        discard;
    }

    float hotWeight = clamp(vertexColor.r, 0.0, 1.0);
    float coreWeight = clamp(vertexColor.g, 0.0, 1.0);

    float broadDensity = strength * mix(0.24, 0.38, coreWeight);
    float sparkDensity = strength * hotWeight * 0.08;
    float wispDensity = strength * mix(0.08, 0.18, coreWeight);
    float coverage = strength * 0.42;

    fragColor = vec4(broadDensity, sparkDensity, wispDensity, coverage);
}
