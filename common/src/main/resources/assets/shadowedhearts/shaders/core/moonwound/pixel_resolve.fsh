#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float PixelSize;
uniform vec2 SourceSize;
uniform vec2 GridOrigin;
uniform int ResolvePass;
uniform int EnergyCoverage;
out vec4 fragColor;

bool contributes(vec4 value) {
    return value.a > 0.000001 || (EnergyCoverage != 0
            && max(max(abs(value.r), abs(value.g)), abs(value.b)) > 0.000001);
}

void main() {
    ivec2 outputPixel = ivec2(floor(gl_FragCoord.xy));
    vec4 sum = vec4(0.0);
    float area = 0.0;
    float nearestDepth = 1.0;

    if (ResolvePass == 1) {
        // Large cells first reduce X independently for each original source row.
        // A fragment visits at most SourceSize.x pixels instead of an entire image.
        float lower = clamp(GridOrigin.x + float(outputPixel.x) * PixelSize, 0.0, SourceSize.x);
        float upper = clamp(GridOrigin.x + float(outputPixel.x + 1) * PixelSize, 0.0, SourceSize.x);
        int first = int(floor(lower)), last = int(ceil(upper));
        for (int x = first; x < last; x++) {
            float weight = max(0.0, min(upper, float(x + 1)) - max(lower, float(x)));
            if (weight <= 0.0) continue;
            ivec2 samplePixel = ivec2(x, outputPixel.y);
            vec4 value = texelFetch(Sampler0, samplePixel, 0);
            sum += value * weight;
            area += weight;
            if (contributes(value))
                nearestDepth = min(nearestDepth, texelFetch(Sampler1, samplePixel, 0).r);
        }
    } else if (ResolvePass == 2) {
        // Row averages all have the same clipped X area within this column, so
        // weighted Y reduction gives the same rectangular box average as pass 0.
        float lower = clamp(GridOrigin.y + float(outputPixel.y) * PixelSize, 0.0, SourceSize.y);
        float upper = clamp(GridOrigin.y + float(outputPixel.y + 1) * PixelSize, 0.0, SourceSize.y);
        int first = int(floor(lower)), last = int(ceil(upper));
        for (int y = first; y < last; y++) {
            float weight = max(0.0, min(upper, float(y + 1)) - max(lower, float(y)));
            if (weight <= 0.0) continue;
            ivec2 samplePixel = ivec2(outputPixel.x, y);
            vec4 value = texelFetch(Sampler0, samplePixel, 0);
            sum += value * weight;
            area += weight;
            // Empty rows carry depth 1. Do not threshold averaged row alpha:
            // a thin contributing source texel must retain its original depth.
            nearestDepth = min(nearestDepth, texelFetch(Sampler1, samplePixel, 0).r);
        }
    } else {
        // Spans <= 16 overlap at most 17 source pixels on either axis. Keeping
        // this path's loop bound fixed prevents accidental whole-image fragments.
        vec2 lower = clamp(GridOrigin + vec2(outputPixel) * PixelSize, vec2(0.0), SourceSize);
        vec2 upper = clamp(GridOrigin + vec2(outputPixel + 1) * PixelSize, vec2(0.0), SourceSize);
        ivec2 first = ivec2(floor(lower));
        ivec2 last = ivec2(ceil(upper));
        for (int row = 0; row < 17; row++) {
            int y = first.y + row;
            if (y >= last.y) break;
            float overlapY = max(0.0, min(upper.y, float(y + 1)) - max(lower.y, float(y)));
            for (int column = 0; column < 17; column++) {
                int x = first.x + column;
                if (x >= last.x) break;
                float overlapX = max(0.0, min(upper.x, float(x + 1)) - max(lower.x, float(x)));
                float weight = overlapX * overlapY;
                if (weight <= 0.0) continue;
                ivec2 samplePixel = ivec2(x, y);
                vec4 value = texelFetch(Sampler0, samplePixel, 0);
                sum += value * weight;
                area += weight;
                if (contributes(value))
                    nearestDepth = min(nearestDepth, texelFetch(Sampler1, samplePixel, 0).r);
            }
        }
    }
    fragColor = sum / max(area, 0.000001);
    gl_FragDepth = nearestDepth;
}
