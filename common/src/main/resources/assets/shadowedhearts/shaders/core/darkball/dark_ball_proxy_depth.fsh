#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float u_proxyAlpha;
uniform float u_ignoreTextureAlpha;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float coverage = mix(texColor.a, vertexColor.a * ColorModulator.a,
            step(0.5, u_ignoreTextureAlpha)) * u_proxyAlpha;
    if (coverage <= 0.01) {
        discard;
    }

    // RG32F: retain device-depth precision and store coverage separately.
    fragColor = vec4(gl_FragCoord.z, coverage, 0.0, 1.0);
}
