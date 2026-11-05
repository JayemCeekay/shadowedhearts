#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform int OpaqueMaterial;
uniform float AlphaCutoff;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewPosition;
in vec4 overlayColor;
in vec3 lightColor;

out vec4 fragColor;

vec3 safe_normalize(vec3 value, vec3 fallback) {
    float magnitude2 = dot(value, value);
    return magnitude2 > 0.00000001 ? value * inversesqrt(magnitude2) : fallback;
}

// Shader packs commonly use temporal jitter and smaller intermediate targets.
// Suppress subpixel ripples instead of letting thin moving veins flicker.
float filtered_sine(float phase) {
    float footprint = fwidth(phase);
    return sin(phase) * (1.0 - smoothstep(0.0, 6.28318530718, footprint));
}

void main() {
    // Native skin contributes only its alpha silhouette, including layer holes.
    float skinAlpha = OpaqueMaterial != 0 ? 1.0 : texture(Sampler0, texCoord0).a;
    float alpha = skinAlpha * vertexColor.a * ColorModulator.a;

    float t = GameTime * 6.28318530718;
    vec2 flowingUv = texCoord0 * 6.28318530718;
    float broad = filtered_sine(flowingUv.x * 5.0 + flowingUv.y * 3.0 + t * 48.0);
    float fine = filtered_sine(flowingUv.y * 13.0 - flowingUv.x * 7.0 - t * 96.0);
    vec3 baseNormal = safe_normalize(viewNormal, vec3(0.0, 1.0, 0.0));
    vec3 n = safe_normalize(baseNormal + vec3(broad * 0.085, fine * 0.065, broad * fine * 0.04), baseNormal);
    vec3 v = safe_normalize(-viewPosition, vec3(0.0, 0.0, 1.0));
    vec3 light = normalize(vec3(-0.4, 0.75, 0.55));
    vec3 halfway = safe_normalize(light + v, light);
    float reflected = clamp(dot(n, halfway), 0.0, 1.0);
    float wetGloss = pow(reflected, 96.0) / (1.0 + fwidth(reflected) * 96.0);
    float wideSheen = pow(reflected, 10.0) * 0.07;
    float fresnel = pow(clamp(1.0 - abs(dot(n, v)), 0.0, 1.0), 4.0);
    float vein = pow(max(0.0, broad * 0.5 + 0.5), 50.0)
            * (0.5 + 0.5 * fine);
    // Derivatives above run before alpha discard so cutout edges retain the
    // neighboring samples required for a defined pixel footprint.
    if (alpha < AlphaCutoff) discard;
    vec3 black = vec3(0.004, 0.005, 0.009);
    vec3 color = black + vec3(0.025, 0.031, 0.046) * vein
            + vec3(0.48, 0.54, 0.64) * wetGloss
            + vec3(0.13, 0.15, 0.2) * (fresnel * 3.0 + wideSheen);
    color *= mix(vec3(0.3), lightColor, 0.75);
    color = mix(overlayColor.rgb * 0.25, color, overlayColor.a);
    fragColor = linear_fog(vec4(color, alpha), vertexDistance, FogStart, FogEnd, FogColor);
}
