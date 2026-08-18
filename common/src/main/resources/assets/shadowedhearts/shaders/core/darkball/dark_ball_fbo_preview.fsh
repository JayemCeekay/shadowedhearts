#version 150

uniform sampler2D Sampler0;
uniform int PreviewMode;
uniform vec2 PreviewUvMin;
uniform vec2 PreviewUvMax;

in vec2 texCoord0;

out vec4 fragColor;

float coverageTone(float value) {
    return pow(clamp(value, 0.0, 1.0), 0.45);
}

float payloadTone(float value, float scale) {
    return 1.0 - exp(-abs(value) * scale);
}

void main() {
    vec3 color;

    if (PreviewMode == 8) {
        vec2 checkerCell = floor(texCoord0 * vec2(16.0, 12.0));
        float checker = mod(checkerCell.x + checkerCell.y, 2.0);
        color = mix(
                vec3(0.035, 0.008, 0.045),
                vec3(0.18, 0.025, 0.09),
                checker);
        float crossDistance = min(
                abs(texCoord0.x - texCoord0.y),
                abs((1.0 - texCoord0.x) - texCoord0.y));
        float cross = 1.0 - smoothstep(0.025, 0.055, crossDistance);
        color = mix(color, vec3(1.0, 0.08, 0.45), cross);
    } else {
        vec2 sampleUv = mix(PreviewUvMin, PreviewUvMax, texCoord0);
        vec4 source = texture(Sampler0, sampleUv);
        if (PreviewMode == 7) {
            color = clamp(source.rgb, 0.0, 1.0);
        } else if (PreviewMode == 1) {
            float value = coverageTone(source.r);
            color = vec3(value);
        } else if (PreviewMode == 2) {
            float value = coverageTone(source.g);
            color = vec3(value);
        } else if (PreviewMode == 3) {
            float value = payloadTone(source.b, 0.85);
            color = source.b >= 0.0
                    ? vec3(0.12, 0.72, 1.0) * value
                    : vec3(1.0, 0.16, 0.62) * value;
        } else if (PreviewMode == 4) {
            float value = payloadTone(source.a, 0.18);
            color = source.a >= 0.0
                    ? vec3(value)
                    : vec3(0.72, 0.24, 1.0) * value;
        } else if (PreviewMode == 5) {
            float value = pow(
                    clamp((1.0 - source.r) * 384.0, 0.0, 1.0),
                    0.35);
            color = vec3(value * clamp(source.a, 0.0, 1.0));
        } else if (PreviewMode == 6) {
            float value = pow(
                    clamp((1.0 - source.r) * 384.0, 0.0, 1.0),
                    0.35);
            color = vec3(value);
        } else if (PreviewMode == 9) {
            // The diagnostic target stores premultiplied stable identity
            // colors. Unpremultiplication makes even a single faint footprint
            // readable; overlaps mix the participating splat identities.
            color = source.a > 0.0001
                    ? clamp(source.rgb / source.a, 0.0, 1.0)
                    : vec3(0.0);
        } else if (PreviewMode == 10) {
            // One isolated splat peaks near 0.125. Cyan/yellow/red therefore
            // identify roughly two/four/eight-way overlap without bands in
            // the underlying data.
            float density = clamp(source.a, 0.0, 1.0);
            vec3 low = mix(
                    vec3(0.02, 0.08, 0.30),
                    vec3(0.00, 0.86, 1.00),
                    smoothstep(0.0, 0.25, density));
            vec3 high = mix(
                    vec3(1.00, 0.92, 0.08),
                    vec3(1.00, 0.04, 0.10),
                    smoothstep(0.50, 1.0, density));
            color = mix(
                    low,
                    high,
                    smoothstep(0.25, 0.60, density));
        } else {
            color = pow(
                    clamp(abs(source.rgb), 0.0, 1.0),
                    vec3(0.45));
        }
    }

    float edgeDistance = min(
            min(texCoord0.x, 1.0 - texCoord0.x),
            min(texCoord0.y, 1.0 - texCoord0.y));
    float border = 1.0 - smoothstep(0.002, 0.008, edgeDistance);
    color = mix(color, vec3(0.60, 0.48, 0.78), border);
    fragColor = vec4(color, 1.0);
}
