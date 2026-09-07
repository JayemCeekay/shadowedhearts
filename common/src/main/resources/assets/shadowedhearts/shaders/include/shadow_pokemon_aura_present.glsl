// Resolves an already-composited aura target without filtering between its
// texels. Window coordinates are made viewport-local before snapping so the
// grid remains stable under offset world and GUI viewports.
vec4 shadowPokemonAuraPresent(
        sampler2D source,
        vec2 framebufferCoordinate,
        vec2 gridOrigin,
        vec2 pixelSize
) {
    ivec2 sourceExtent = max(textureSize(source, 0), ivec2(1));
    ivec2 sourceCoord = clamp(
            ivec2(floor((framebufferCoordinate - gridOrigin)
                    / max(pixelSize, vec2(1.0)))),
            ivec2(0),
            sourceExtent - ivec2(1));
    return texelFetch(source, sourceCoord, 0);
}

// Minecraft-style fractional mip presentation for the world aura: each
// adjacent level stays nearest-filtered and therefore crisp, while only the
// contribution of the two levels is interpolated. Mix in premultiplied-alpha
// space so differently covered texels cannot produce dark or bright fringes;
// return straight alpha for the presentation program's SRC_ALPHA blend.
vec4 shadowPokemonAuraFractionalMipPresent(
        sampler2D lowerSource,
        sampler2D upperSource,
        vec2 framebufferCoordinate,
        vec2 gridOrigin,
        vec2 lowerPixelSize,
        vec2 upperPixelSize,
        float lodBlend
) {
    vec4 lowerColor = shadowPokemonAuraPresent(
            lowerSource,
            framebufferCoordinate,
            gridOrigin,
            lowerPixelSize);
    float safeLodBlend = clamp(lodBlend, 0.0, 1.0);
    if (safeLodBlend <= 0.0) return lowerColor;

    vec4 upperColor = shadowPokemonAuraPresent(
            upperSource,
            framebufferCoordinate,
            gridOrigin,
            upperPixelSize);

    vec4 lowerPremultiplied = vec4(
            lowerColor.rgb * lowerColor.a,
            lowerColor.a);
    vec4 upperPremultiplied = vec4(
            upperColor.rgb * upperColor.a,
            upperColor.a);
    vec4 blended = mix(
            lowerPremultiplied,
            upperPremultiplied,
            safeLodBlend);
    if (blended.a <= 0.00001) return vec4(0.0);
    return vec4(blended.rgb / blended.a, blended.a);
}
