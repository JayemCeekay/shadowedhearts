#version 150

uniform sampler2D Sampler0;
uniform sampler2D MaskSampler;
uniform sampler2D ProxyFrontSampler;
uniform mat4 InvProjMat;
uniform int ProxyFrontAvailable;
uniform int BodyMaterialAvailable;

in vec2 texCoord0;
out vec4 fragColor;

float proxyRayDistance(ivec2 pixel, ivec2 extent, float deviceDepth) {
    vec2 uv = (vec2(pixel) + vec2(0.5)) / max(vec2(extent), vec2(1.0));
    vec4 view = InvProjMat * vec4(uv * 2.0 - 1.0, deviceDepth * 2.0 - 1.0, 1.0);
    view.xyz *= abs(view.w) > 0.000001 ? 1.0 / view.w : 1.0;
    vec4 eye = InvProjMat * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyePosition = abs(eye.w) > 0.000001 ? eye.xyz / eye.w : vec3(0.0);
    return length(view.xyz - eyePosition);
}

void main() {
    ivec2 extent = textureSize(Sampler0, 0);
    ivec2 pixel = clamp(ivec2(floor(texCoord0 * vec2(extent))),
            ivec2(0), max(extent - ivec2(1), ivec2(0)));
    vec4 material = texelFetch(Sampler0, pixel, 0);
    if (BodyMaterialAvailable != 0) {
        // R is geometric occupancy, B is complementary presentation alpha.
        // The moving energy and source meshes own their tagged first-hit depth.
        float depth = material.a < -2.0001
                ? max(-material.a - 2.0, 0.0001) : material.a;
        fragColor = vec4(material.r, 0.0, material.b, depth);
        return;
    }

    // Exact texture-alpha recovery remains available before a successful body
    // draw and after a failed submission. Its captured front depth is immutable.
    float coverage = smoothstep(0.015, 0.12, texture(MaskSampler, texCoord0).a);
    float depth = -1.0;
    if (coverage > 0.002 && ProxyFrontAvailable != 0) {
        vec4 front = texelFetch(ProxyFrontSampler, pixel, 0);
        if (front.g > 0.01 && front.r < 0.999999) {
            depth = proxyRayDistance(pixel, extent, front.r);
        }
    }
    fragColor = vec4(coverage, 0.0, coverage, depth);
}
