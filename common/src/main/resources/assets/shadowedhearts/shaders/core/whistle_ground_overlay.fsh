#version 150

in vec3 vPosWS;

uniform vec2 uCenterXZ;  // world XZ center of the brush
uniform float uRadius;   // brush radius in blocks
uniform vec3 uColor;     // overlay color (light green)
uniform float uOpacity;  // base opacity (0..1)
uniform float uSoftness; // edge feather in blocks
uniform float uBaseY;    // base Y (ring altitude)
uniform float uFadeHeight; // vertical fade height above base
uniform float uTime;     // seconds since start (for animation)

out vec4 fragColor;

void main() {
    vec2 d = vPosWS.xz - uCenterXZ;
    float dist = length(d);

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

    // Occasional pulse ring that expands from center, then rests
    float period = 3.5;              // seconds between pulses
    float activeWindow = 0.55;       // fraction of period when pulse is visible
    float phase = fract(uTime / period);
    float gate = 1.0 - step(activeWindow, phase); // 1 when phase < activeWindow
    float pulseT = clamp(phase / activeWindow, 0.0, 1.0);
    float pulseRadius = pulseT * (uRadius * 1.25);
    float pulseWidth = max(uSoftness * 0.25, 0.05);
    float pulseBand = smoothstep(pulseRadius - pulseWidth, pulseRadius, dist) * (1.0 - smoothstep(pulseRadius, pulseRadius + pulseWidth, dist));
    float pulseAlpha = gate * pulseBand * (uOpacity * 0.9);

    float a = max(max(baseAlpha, ringAlpha), pulseAlpha);

    // Apply vertical fade: 1.0 at baseY, 0.0 at baseY + uFadeHeight (if > 0)
    if (uFadeHeight > 0.0001) {
        float t = clamp((vPosWS.y - uBaseY) / uFadeHeight, 0.0, 1.0);
        // Add a subtle rotating sine modulation so the fade seems to swirl around
        float theta = atan(d.y, d.x);         // angle around the center
        float swirl = 0.5 + 0.5 * sin(6.0 * theta + uTime * 1.5);
        float mod = 0.9 + 0.2 * swirl;        // stays within ~[0.9, 1.1]
        t = clamp(t * mod, 0.0, 1.0);
        a *= (1.0 - t);
    }

    fragColor = vec4(uColor, a);
}
