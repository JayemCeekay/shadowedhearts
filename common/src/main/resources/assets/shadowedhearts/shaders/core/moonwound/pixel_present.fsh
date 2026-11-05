#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform float PixelSize;
uniform vec2 GridOrigin;
uniform vec2 GridSize;
uniform vec2 ViewportOrigin;
uniform float PixelBlend;
uniform int EnergyCoverage;
uniform int SceneOcclusion;
uniform int ClipEnabled;
uniform vec4 ClipBounds;
out vec4 fragColor;

bool contributes(vec4 value) {
    return value.a > 0.000001 || (EnergyCoverage != 0
            && max(max(abs(value.r), abs(value.g)), abs(value.b)) > 0.000001);
}

void main() {
    if (ClipEnabled != 0 && (any(lessThan(gl_FragCoord.xy, ClipBounds.xy))
            || any(greaterThanEqual(gl_FragCoord.xy, ClipBounds.zw)))) discard;
    vec2 lower = gl_FragCoord.xy - 0.5;
    vec2 upper = lower + 1.0;
    ivec2 first = ivec2(floor((lower - GridOrigin) / PixelSize));
    vec4 pixelColor = vec4(0.0);
    float pixelDepth = 1.0;
    // A display pixel overlaps at most two cells on each axis (cell size >= 1).
    // Only cell boundaries are filtered: magnified texel interiors remain crisp,
    // while their edges move smoothly across subpixel positions like a texture.
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 cell = first + ivec2(x, y);
            if (any(lessThan(cell, ivec2(0))) || any(greaterThanEqual(cell, ivec2(GridSize)))) continue;
            vec2 cellLower = GridOrigin + vec2(cell) * PixelSize;
            vec2 overlap = max(vec2(0.0), min(upper, cellLower + PixelSize) - max(lower, cellLower));
            float weight = overlap.x * overlap.y;
            if (weight <= 0.0) continue;
            vec4 value = texelFetch(Sampler0, cell, 0);
            pixelColor += value * weight;
            if (contributes(value))
                pixelDepth = min(pixelDepth, texelFetch(Sampler1, cell, 0).r);
        }
    }
    ivec2 sourcePixel = ivec2(gl_FragCoord.xy - ViewportOrigin);
    vec4 sourceColor = PixelBlend < 1.0 ? texelFetch(Sampler2, sourcePixel, 0) : vec4(0.0);
    vec4 color = mix(sourceColor, pixelColor, PixelBlend);
    if (!contributes(color)) discard;
    // As texels approach display-pixel size, smoothly recover the full-resolution
    // effect instead of switching grids. The world itself is never downsampled.
    fragColor = color;
    float nearestDepth = PixelBlend > 0.0 ? pixelDepth : 1.0;
    if (PixelBlend < 1.0 && contributes(sourceColor))
        nearestDepth = min(nearestDepth, texelFetch(Sampler3, sourcePixel, 0).r);
    // Aura capture has no physical effect depth. Its source depth is the pristine
    // scene-depth boundary, so only permit expansion onto equally distant or
    // farther scene pixels. This remains valid after Fabulous replaces main depth.
    if (SceneOcclusion != 0 && nearestDepth > texelFetch(Sampler3, sourcePixel, 0).r + 0.000001) discard;
    // Preserve full-resolution foreground occlusion and leave world depth intact.
    gl_FragDepth = nearestDepth;
}
