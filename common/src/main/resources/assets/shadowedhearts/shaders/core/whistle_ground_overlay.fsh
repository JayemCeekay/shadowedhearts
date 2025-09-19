#version 150

in vec3 vPosWS;

uniform vec2 uCenterXZ;  // world XZ center of the brush
uniform float uRadius;   // brush radius in blocks
uniform vec3 uColor;     // overlay color (light green)
uniform float uOpacity;  // base opacity (0..1)
uniform float uSoftness; // edge feather in blocks
uniform float uBaseY;    // base Y (ring altitude)
uniform float uFadeHeight; // vertical fade height above base

out vec4 fragColor;

void main() {
    float dist = length(vPosWS.xz - uCenterXZ);

    // Base fill with soft edge: inside is 1, fades to 0 from (radius - softness) to radius
    float inner = max(uRadius - uSoftness, 0.0);
    float fill = 1.0 - smoothstep(inner, uRadius, dist);

    // Slightly lower the opacity of the fill to make it a bit more transparent
    float baseAlpha = fill * (uOpacity * 0.85);

    // Highlight ring right at the edge: a narrow band centered on the radius
    float ringWidth = max(uSoftness * 0.35, 0.03);
    float ringBand = smoothstep(uRadius - ringWidth, uRadius, dist) * (1.0 - smoothstep(uRadius, uRadius + ringWidth, dist));
    // Make the ring a bit stronger than the fill
    float ringAlpha = ringBand * min(uOpacity + 0.20, 1.0);

    float a = max(baseAlpha, ringAlpha);

    // Apply vertical fade: 1.0 at baseY, 0.0 at baseY + uFadeHeight (if > 0)
    if (uFadeHeight > 0.0001) {
        float t = clamp((vPosWS.y - uBaseY) / uFadeHeight, 0.0, 1.0);
        a *= (1.0 - t);
    }

    fragColor = vec4(uColor, a);
}
