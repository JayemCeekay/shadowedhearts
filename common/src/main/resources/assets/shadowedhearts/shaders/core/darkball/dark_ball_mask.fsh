#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, texCoord0);
    float sourceAlpha = textureColor.a
            * vertexColor.a * ColorModulator.a;
    if (sourceAlpha < 0.08) {
        discard;
    }

    // Coverage is topology/ownership data and must not be eroded by fog.
    // Fog affects only the replayed hit-frame color, matching vanilla entities.
    float strength = clamp(sourceAlpha, 0.0, 1.0);

    vec3 litColor = textureColor.rgb * vertexColor.rgb
            * ColorModulator.rgb;
    litColor = mix(overlayColor.rgb, litColor, overlayColor.a);
    litColor *= lightMapColor.rgb;
    litColor = linear_fog(vec4(litColor, strength), vertexDistance,
            FogStart, FogEnd, FogColor).rgb;
    // Store premultiplied color beside authoritative coverage so linear
    // sampling cannot produce a dark fringe during the frozen-model handoff.
    fragColor = vec4(litColor * strength, strength);
}
