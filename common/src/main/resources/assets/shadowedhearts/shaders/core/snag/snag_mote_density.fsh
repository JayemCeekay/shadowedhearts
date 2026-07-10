#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;

    float mask = texColor.a;
    float falloff = exp(-4.6 * (1.0 - mask) * (1.0 - mask));
    float coreMask = pow(mask, 4.0);

    vec4 vCol = vertexColor * ColorModulator;
    float twinkle = mix(0.75, 1.25, hash31(worldPos * 17.0));
    float densityStrength = vCol.a * twinkle;

    float density = falloff * densityStrength * 0.95;
    float core = coreMask * densityStrength * 1.35;

    fragColor = vec4(density, core, 0.0, 1.0);
}
