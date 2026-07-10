#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    // Sample the soft radial gradient texture (soft_glow.png)
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.01) discard;

    // Use texture alpha as radial mask (1 at center, 0 at edge)
    float mask = texColor.a;

    // Clean Gaussian-like falloff — no FBM noise, for defined energy streaks
    float falloff = exp(-3.5 * (1.0 - mask) * (1.0 - mask));

    // Vertex color alpha carries per-splat density strength (from Java)
    vec4 vCol = vertexColor * ColorModulator;
    float densityStrength = vCol.a;

    // Density output — tuned for purple/magenta energy readability
    float outDensity = falloff * densityStrength * 1.4;
    float heat = falloff * falloff * densityStrength * 1.0;

    fragColor = vec4(outDensity, heat, 0.0, 1.0);
}
