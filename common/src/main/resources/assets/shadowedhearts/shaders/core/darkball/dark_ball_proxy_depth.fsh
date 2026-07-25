#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float u_proxyAlpha;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float coverage = texColor.a * u_proxyAlpha;
    if (coverage <= 0.01) {
        discard;
    }

    fragColor = vec4(gl_FragCoord.z, 0.0, 0.0, coverage);
}
