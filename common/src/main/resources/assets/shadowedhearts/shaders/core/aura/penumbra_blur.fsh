#version 150

uniform sampler2D Sampler0;    // density color texture
uniform sampler2D Sampler1;    // density FBO depth texture (scene depth at half-res)
uniform vec2 Direction;        // (1/w, 0) for horizontal, (0, 1/h) for vertical
uniform float BlurRadius;      // Kawase offset multiplier (iteration index)

in vec2 texCoord0;

out vec4 fragColor;

// Depth-aware bilateral blur threshold.
// Samples whose depth differs from the center by more than this are rejected.
const float DEPTH_THRESHOLD = 0.002;

void main() {
    // Kawase blur: sample center + 4 diagonal offsets
    // Each iteration uses a larger offset (BlurRadius = 0, 1, 2, ...)
    vec2 off = Direction * (BlurRadius + 0.5);

    // Center sample — always included
    vec4 centerColor = texture(Sampler0, texCoord0);
    float centerDepth = texture(Sampler1, texCoord0).r;

    vec4 sum = centerColor * 6.0;
    float totalWeight = 6.0;

    // 4 diagonal neighbor samples with depth-aware rejection
    vec2 offsets[4] = vec2[4](
        vec2(-off.x, -off.y),
        vec2( off.x, -off.y),
        vec2(-off.x,  off.y),
        vec2( off.x,  off.y)
    );

    for (int i = 0; i < 4; i++) {
        vec2 sampleUV = texCoord0 + offsets[i];
        float sampleDepth = texture(Sampler1, sampleUV).r;

        // Reject samples across depth discontinuities (geometry edges)
        float depthDiff = abs(sampleDepth - centerDepth);
        float depthWeight = step(depthDiff, DEPTH_THRESHOLD);

        float w = 0.5 * depthWeight;
        sum += texture(Sampler0, sampleUV) * w;
        totalWeight += w;
    }

    fragColor = sum / totalWeight;
}
