#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Direction;
uniform float BlurRadius;

in vec2 texCoord0;
out vec4 fragColor;

// Match the existing soft aura filter and scene-depth rejection. The heat/core
// channel is sampled from this pixel on every pass, so it never joins the
// repeated spatial blur of broad haze, violet halo, and their coverage.
const float DEPTH_THRESHOLD = 0.002;

void main() {
    vec2 off = Direction * (BlurRadius + 0.5);
    vec4 centerColor = texture(Sampler0, texCoord0);
    float centerDepth = texture(Sampler1, texCoord0).r;
    vec4 sum = centerColor * 6.0;
    float totalWeight = 6.0;
    vec2 densitySize = vec2(textureSize(Sampler0, 0));
    vec2 offsets[4] = vec2[4](
        vec2(-off.x, -off.y),
        vec2( off.x, -off.y),
        vec2(-off.x,  off.y),
        vec2( off.x,  off.y)
    );
    for (int i = 0; i < 4; i++) {
        // Align color taps with density texel centers. A fractional linear color
        // sample can straddle an occluder even when its nearest depth tap passes.
        vec2 sampleUV = (floor((texCoord0 + offsets[i]) * densitySize) + 0.5)
                / densitySize;
        float sampleDepth = texture(Sampler1, sampleUV).r;
        float w = 0.5 * step(abs(sampleDepth - centerDepth), DEPTH_THRESHOLD);
        sum += texture(Sampler0, sampleUV) * w;
        totalWeight += w;
    }
    vec4 filtered = sum / totalWeight;
    fragColor = vec4(filtered.r, centerColor.g, filtered.b, filtered.a);
}
