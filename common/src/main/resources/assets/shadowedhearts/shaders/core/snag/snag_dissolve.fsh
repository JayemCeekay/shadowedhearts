#version 150

#moj_import <fog.glsl>

// ─── Samplers ────────────────────────────────────────────────────────────────
uniform sampler2D Sampler0;   // entity texture (albedo)
uniform sampler2D Sampler1;   // overlay texture (hurt flash, etc.)
uniform sampler2D Sampler2;   // lightmap
uniform sampler2D Sampler3;   // dissolve noise (our custom sampler)

// ─── Standard uniforms ──────────────────────────────────────────────────────
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

// ─── Dissolve uniforms ──────────────────────────────────────────────────────
uniform float u_dissolveProgress;  // 0.0 = fully solid, 1.0 = fully dissolved
uniform vec3  u_dissolveColor;     // edge glow color (e.g., purple/magenta)
uniform float u_edgeWidth;         // width of the glowing edge band (default 0.08)
uniform float u_noiseScale;        // UV scale for noise sampling (default 3.0)
uniform float u_time;              // animation time for scrolling noise
uniform vec3  u_conversionColor;   // optional material tint for conversion effects
uniform float u_conversionStrength;

// ─── Varyings from vertex shader ─────────────────────────────────────────────
in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec2 texCoord1;

out vec4 fragColor;

void main() {
    // Sample the entity's base texture
    vec4 baseColor = texture(Sampler0, texCoord0);

    // Alpha test — discard fully transparent pixels from the model texture
    if (baseColor.a < 0.1) {
        discard;
    }

    // Apply overlay (hurt flash)
    baseColor.rgb = mix(baseColor.rgb, overlayColor.rgb, overlayColor.a);

    // Apply vertex color + color modulator
    baseColor *= vertexColor * ColorModulator;

    // ── Dissolve logic ──────────────────────────────────────────────────
    // Sample noise texture using the entity UV scaled up for detail
    vec2 noiseUV = texCoord0 * u_noiseScale + vec2(u_time * 0.05, u_time * 0.03);
    float noise = texture(Sampler3, noiseUV).r;

    // Compare noise against dissolve progress to determine visibility
    float dissolve = smoothstep(u_dissolveProgress - u_edgeWidth,
                                u_dissolveProgress + u_edgeWidth,
                                noise);

    // If fully dissolved at this pixel, discard
    if (dissolve < 0.01) {
        discard;
    }

    // ── Edge glow ───────────────────────────────────────────────────────
    // Bright glowing edge at the dissolve boundary
    float edgeFactor = 1.0 - smoothstep(0.0, u_edgeWidth * 2.0, abs(noise - u_dissolveProgress));
    // Make edge glow stronger as dissolve progresses
    float edgeIntensity = edgeFactor * 2.5 * min(u_dissolveProgress * 4.0, 1.0);
    // Fade edge glow near the end to prevent lingering bright spots
    edgeIntensity *= (1.0 - smoothstep(0.85, 1.0, u_dissolveProgress));

    vec3 convertedBase = mix(baseColor.rgb, u_conversionColor,
                             clamp(u_conversionStrength * u_dissolveProgress, 0.0, 1.0));
    vec3 finalColor = convertedBase + u_dissolveColor * edgeIntensity;

    // Apply lightmap (sample from Sampler2 using texCoord1)
    vec4 lightColor = texture(Sampler2, texCoord1);
    finalColor *= lightColor.rgb;

    // Apply fog
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    finalColor = mix(FogColor.rgb, finalColor, fogFade);

    float finalAlpha = baseColor.a * dissolve * fogFade;

    fragColor = vec4(finalColor, finalAlpha);
}
