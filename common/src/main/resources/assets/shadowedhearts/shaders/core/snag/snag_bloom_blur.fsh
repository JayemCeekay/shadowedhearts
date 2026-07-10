#version 150

uniform sampler2D Sampler0;    // source color texture
uniform vec2 Direction;        // (1/w, 0) for horizontal; (0, 1/h) for vertical
uniform float BlurRadius;      // multiplier for offset spread (default 1.0)

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // 9-tap Gaussian kernel (sigma ≈ 2.0)
    // Weights: 0.0162, 0.0540, 0.1218, 0.1872, 0.2416, 0.1872, 0.1218, 0.0540, 0.0162
    const int SAMPLES = 9;
    float weights[SAMPLES] = float[](
        0.0162, 0.0540, 0.1218, 0.1872, 0.2416,
        0.1872, 0.1218, 0.0540, 0.0162
    );
    float offsets[SAMPLES] = float[](
        -4.0, -3.0, -2.0, -1.0, 0.0,
         1.0,  2.0,  3.0,  4.0
    );

    vec4 sum = vec4(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        vec2 uv = texCoord0 + Direction * offsets[i] * BlurRadius;
        sum += texture(Sampler0, uv) * weights[i];
    }

    fragColor = sum;
}
