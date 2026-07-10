#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float u_time;
uniform float u_streakStrength;    // brightness of horizontal arm
uniform float u_streakSharpness;   // higher = thinner streaks
uniform float u_spikeCount;        // number of spike arm pairs (abs(cos(N*a)) yields 2N)
uniform float u_spikeStrength;     // additive strength of diagonal spikes
uniform float u_aspect;            // width/height of quad to correct stretch
uniform vec3  u_flareTint;         // additive color (e.g., cool white or magenta)

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 d = texCoord0 - vec2(0.5);

    // Correct for non-square quads so the flare stays circular in world space
    float aspect = max(u_aspect, 0.01);
    d.x *= aspect;

    float r = length(d) * 2.0;

    // ── Horizontal streak arm ────────────────────────────────────────────
    // cos(atan(y,x)) peaks at angle=0 and angle=PI (horizontal axis)
    float sharpness = max(1.0, u_streakSharpness);
    float ang = atan(d.y, d.x);

    float hArm = pow(abs(cos(ang)), sharpness)
               * pow(max(0.0, 1.0 - r), 1.5);

    // ── Diagonal spike arms (camera aperture blade pattern) ──────────────
    float N = max(1.0, u_spikeCount);
    float spikes = pow(abs(cos(N * ang)), sharpness);
    float radFall = pow(max(0.0, 1.0 - r), 0.8);

    // ── Central core glow ────────────────────────────────────────────────
    float core = exp(-r * r * 6.0);

    // ── Combine ──────────────────────────────────────────────────────────
    float intensity = core * 1.2
                    + hArm * max(0.0, u_streakStrength)
                    + spikes * max(0.0, u_spikeStrength) * radFall;

    // Optional texture overlay (if bound, multiply; otherwise pure procedural)
    vec4 texSample = texture(Sampler0, texCoord0);
    // If texture alpha is near zero everywhere, treat as no-texture (pure procedural)
    float texMask = (texSample.a > 0.01) ? texSample.r : 1.0;
    intensity *= texMask;

    // Mild pulsation for life
    float pulse = 0.85 + 0.15 * sin(u_time * 8.0);
    intensity *= pulse;

    vec3 color = u_flareTint * intensity;

    // Ensure alpha matches intensity for proper additive blending
    float alpha = clamp(intensity, 0.0, 1.0);
    vec4 outCol = vec4(color, alpha);

    // Apply vertex/overlay color modulation
    float alphaMod = vertexColor.a * ColorModulator.a;
    outCol.rgb *= (vertexColor.rgb * ColorModulator.rgb * overlayColor.rgb);
    outCol.a *= alphaMod * overlayColor.a;
    outCol.rgb *= alphaMod;

    // Fog fade
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    outCol.rgb = mix(FogColor.rgb, outCol.rgb, fogFade);
    outCol.a *= fogFade;

    fragColor = outCol;
}
