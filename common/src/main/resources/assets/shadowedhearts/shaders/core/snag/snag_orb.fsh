#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float u_time;
uniform vec3  u_orbTint;         // tint color for the shell energy
uniform float u_orbIntensity;    // overall brightness multiplier
uniform float u_shellInner;      // inner edge of shell band (0..1, default 0.70)
uniform float u_shellOuter;      // outer edge fade start (0..1, default 0.90)
uniform float u_noiseScrollX;    // noise UV scroll speed X
uniform float u_noiseScrollY;    // noise UV scroll speed Y
uniform float u_rimPower;        // rim brightening exponent (default 2.0)
uniform float u_crackleScale;    // scales the noise contrast for crackling look

const float TAU = 6.28318530718;
const float PI  = 3.14159265359;

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 d = texCoord0 - vec2(0.5);
    float r = length(d) * 2.0;

    // Discard outside the unit circle
    if (r > 1.0) discard;

    // ── Shell band mask ──────────────────────────────────────────────────
    float shellInner = clamp(u_shellInner, 0.0, 0.99);
    float shellOuter = clamp(u_shellOuter, shellInner + 0.01, 1.0);

    // Fade in at inner edge, fade out at outer edge
    float shell = smoothstep(shellInner, shellInner + 0.06, r)
                * (1.0 - smoothstep(shellOuter, 1.0, r));

    // ── Sphere-projected UV for noise sampling ───────────────────────────
    // Fake sphere normal from disc coordinates
    float z = sqrt(max(0.0, 1.0 - r * r));
    vec2 sphereUv = vec2(
        atan(d.x, z) / TAU + 0.5,
        atan(d.y, z) / PI + 0.5
    );

    // Scroll noise over the sphere surface
    vec2 noiseUv = sphereUv + vec2(u_time * u_noiseScrollX, u_time * u_noiseScrollY);
    float n = texture(Sampler0, noiseUv).r;

    // Apply crackle contrast: push noise toward 0/1 for an electric look
    float crackle = clamp(u_crackleScale, 0.5, 4.0);
    n = pow(n, crackle);

    // ── Rim brightening at shell edges ───────────────────────────────────
    float rimPow = max(0.5, u_rimPower);
    // Brighter near the outer edge of the shell band
    float edgeDist = abs(r - (shellInner + shellOuter) * 0.5) / ((shellOuter - shellInner) * 0.5);
    float rim = pow(clamp(edgeDist, 0.0, 1.0), rimPow);
    float rimBoost = 1.0 + rim * 0.6;

    // ── Combine ──────────────────────────────────────────────────────────
    float energy = n * shell * max(0.0, u_orbIntensity) * rimBoost;

    // Mild pulsation
    float pulse = 0.9 + 0.1 * sin(u_time * 6.0);
    energy *= pulse;

    vec3 color = u_orbTint * energy;
    float alpha = clamp(energy, 0.0, 1.0);

    vec4 outCol = vec4(color, alpha);

    // Apply vertex/overlay color modulation (same pattern as ball_glow)
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
