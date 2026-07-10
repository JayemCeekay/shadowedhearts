#version 150

#moj_import <fog.glsl>
#moj_import <shadowedhearts:palette_fire.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float u_time;

uniform vec3 u_glowTint;
uniform float u_rimStrength;
uniform float u_pulseSpeed;
uniform float u_useMask; // 1.0 = use red channel as mask, 0.0 = luminance

// Palette controls
uniform float u_glowMix;        // 0..1, mix palette vs u_glowTint
uniform float u_paletteSaturation; // 0..1
uniform float u_voxelsPerRad;

// Palette stops
uniform vec3 u_c0; // center color
uniform vec3 u_c1; // inner mid
uniform vec3 u_c2; // outer mid
uniform vec3 u_c3; // edge color
uniform float u_t1;
uniform float u_t2;
uniform float u_t3;
uniform vec3 u_lumaCoeff;

in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 texCoord = texCoord0;
    vec3 nrm = vec3(0.0, 0.0, 1.0);

    if (u_voxelsPerRad > 0.0) {
        texCoord = sh_quantize3D(vec3(texCoord0, 0.0), u_voxelsPerRad).xy;
    }

    vec4 base = texture(Sampler0, texCoord);
    if (base.a < 0.1) discard;

    // Choose glow source: mask red or luminance of base
    float maskGlow = base.r;
    float lumGlow = sh_luma(base.rgb);
    float glow = mix(lumGlow, maskGlow, clamp(u_useMask, 0.0, 1.0));

    // Pulsing intensity using global game time
    float pulse = 0.6 + 0.4 * sin(u_time * u_pulseSpeed);

    // Rim via view-space normal — stronger at grazing angles
    float rim = pow(max(0.0, 1.0 - abs(nrm.z)), 3.0);

    float intensity = max(0.0, glow * pulse + rim * u_rimStrength);

    // Outward gradient: center -> edges drives the palette
    vec2 d = texCoord - vec2(0.5);
    float radiusFromCenter = clamp(length(d) * 2.0, 0.0, 1.0);

    // Detect if palette uniforms are unset and use defaults
    const vec3 DEF_c0 = vec3(1.00, 1.00, 1.00);
    const vec3 DEF_c1 = vec3(1.00, 0.92, 0.12);
    const vec3 DEF_c2 = vec3(1.00, 0.55, 0.10);
    const vec3 DEF_c3 = vec3(0.45, 0.00, 0.95);
    const float DEF_t1 = 0.20;
    const float DEF_t2 = 0.65;
    const float DEF_t3 = 1.00;

    float cSum = u_c0.x + u_c0.y + u_c0.z + u_c1.x + u_c1.y + u_c1.z
               + u_c2.x + u_c2.y + u_c2.z + u_c3.x + u_c3.y + u_c3.z;
    vec3 c0 = (cSum <= 0.0001) ? DEF_c0 : u_c0;
    vec3 c1 = (cSum <= 0.0001) ? DEF_c1 : u_c1;
    vec3 c2 = (cSum <= 0.0001) ? DEF_c2 : u_c2;
    vec3 c3 = (cSum <= 0.0001) ? DEF_c3 : u_c3;
    float t1v = (u_t3 <= 0.0001) ? DEF_t1 : u_t1;
    float t2v = (u_t3 <= 0.0001) ? DEF_t2 : u_t2;
    float t3v = (u_t3 <= 0.0001) ? DEF_t3 : u_t3;

    vec3 pal = palette_vertical_fire(radiusFromCenter, c0, c1, c2, c3, t1v, t2v, t3v, u_lumaCoeff, u_paletteSaturation);

    vec3 tintMixed = mix(u_glowTint, pal, clamp(u_glowMix, 0.0, 1.0));
    vec3 color = tintMixed * intensity;

    vec4 outCol = vec4(color, intensity);

    // Apply vertex/overlay tinting and ensure additive path respects alpha fades.
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
