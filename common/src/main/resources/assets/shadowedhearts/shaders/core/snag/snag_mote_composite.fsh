#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 densityData = texture(Sampler0, texCoord0).rg;
    float density = densityData.r;
    float core = densityData.g;

    if (density < 0.001) discard;

    float mote = smoothstep(0.015, 0.18, density);
    mote = pow(mote, 0.9);
    if (mote < 0.004) discard;

    float coreGlow = smoothstep(0.10, 0.38, core);
    float whiteCore = smoothstep(0.28, 0.72, core);
    float dx = dFdx(density);
    float dy = dFdy(density);
    float rim = smoothstep(0.0, 0.08, length(vec2(dx, dy)));

    vec3 outer = vec3(0.28, 0.08, 0.48);
    vec3 mid = vec3(0.62, 0.24, 0.95);
    vec3 hot = vec3(1.00, 0.88, 1.00);
    vec3 white = vec3(1.00, 0.98, 1.00);

    vec3 color = mix(outer, mid, mote);
    color = mix(color, hot, coreGlow * 0.55);
    color = mix(color, white, whiteCore * 0.85);
    color += mid * rim * 0.35;

    float alpha = min(mote * 0.52 + coreGlow * 0.14 + whiteCore * 0.22, 0.82);
    if (alpha < 0.005) discard;

    fragColor = vec4(color * 1.15, alpha);
}
