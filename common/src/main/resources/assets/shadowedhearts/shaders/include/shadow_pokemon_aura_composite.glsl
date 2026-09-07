// Shared composite material used by the world and GUI Shadow Pokemon aura programs.

float shadowAuraCompositeHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float shadowAuraCompositeNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = shadowAuraCompositeHash(i);
    float b = shadowAuraCompositeHash(i + vec2(1.0, 0.0));
    float c = shadowAuraCompositeHash(i + vec2(0.0, 1.0));
    float d = shadowAuraCompositeHash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float shadowAuraCompositeFbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * shadowAuraCompositeNoise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return v;
}

bool shadowPokemonAuraComposite(
        vec4 data,
        vec2 textureCoordinate,
        float gameTime,
        vec2 screenSize,
        vec2 renderSize,
        out vec4 compositeColor
) {
    float broad = data.r;
    float heat = data.g;
    float wisp = data.b;
    float coverage = data.a;

    // Screen derivatives must be evaluated before any data-dependent exit.
    // Derivatives inside non-uniform control flow are undefined and can turn
    // otherwise smooth detail rims into driver-dependent bands.
    float combinedDensity = broad + wisp * 0.72;
    vec2 derivativeScale = max(renderSize, vec2(1.0))
            / max(screenSize, vec2(1.0));
    float dx = dFdx(combinedDensity) * derivativeScale.x;
    float dy = dFdy(combinedDensity) * derivativeScale.y;
    float gradient = length(vec2(dx, dy));

    if (broad < 0.001 && heat < 0.001 && wisp < 0.001 && coverage < 0.001) return false;

    float broadFog = smoothstep(0.016, 0.54, broad);
    broadFog = pow(broadFog, 0.86);
    float wispFog = smoothstep(0.012, 0.38, wisp);
    wispFog = pow(wispFog, 0.74);
    float fog = max(broadFog, wispFog * 0.72);
    float hot = smoothstep(0.14, 0.62, heat);
    float whiteFleck = smoothstep(0.68, 1.18, heat) * smoothstep(0.05, 0.42, wisp + coverage * 0.20);

    if (fog < 0.004 && hot < 0.01) return false;

    float rimStrength = smoothstep(0.0, 0.14, gradient);
    // Broad/core haze is intentionally excluded from rim lighting. Those
    // channels overlap in large, long-lived sheets; outlining every summed
    // density transition turns the aura into nested topographic contours.
    float detailShare = clamp(
            (wisp + heat) / max(broad + wisp + heat, 0.001),
            0.0,
            1.0
    );
    float detailRim = rimStrength * smoothstep(0.32, 0.68, detailShare);

    float densityNorm = smoothstep(0.05, 0.58, min(combinedDensity, 0.66));
    vec2 noiseUV = textureCoordinate * screenSize / 62.0 + gameTime * vec2(240.0, -165.0);
    float patch = shadowAuraCompositeFbm(noiseUV);
    float patchMask = smoothstep(0.28, 0.78, patch);

    vec3 blackViolet = vec3(0.015, 0.003, 0.028);
    vec3 deepPurple = vec3(0.065, 0.014, 0.115);
    vec3 smokePurple = vec3(0.18, 0.055, 0.28);
    vec3 colosseumPurple = vec3(0.48, 0.16, 0.72);
    vec3 magentaHot = vec3(0.92, 0.38, 1.00);
    vec3 whiteHot = vec3(1.00, 0.92, 1.00);

    vec3 broadColor = mix(blackViolet, deepPurple, smoothstep(0.0, 0.48, densityNorm));
    broadColor = mix(broadColor, smokePurple, smoothstep(0.18, 0.68, broadFog) * 0.42);
    vec3 wispColor = mix(smokePurple, colosseumPurple, smoothstep(0.02, 0.42, wispFog));

    // Broad haze now progresses monotonically from dark to dense purple.
    // Only detail-dominant wisps and flecks retain the authored edge light.
    vec3 color = broadColor;
    color = mix(color, wispColor, clamp(wispFog * 0.58 + detailRim * 0.16, 0.0, 0.78));
    color = mix(color, colosseumPurple, patchMask * wispFog * (1.0 - densityNorm) * 0.32);
    color = mix(color, magentaHot, hot * 0.26);
    color = mix(color, whiteHot, whiteFleck * 0.44);

    color += colosseumPurple * detailRim * 0.26;

    float voidNoise = shadowAuraCompositeFbm(textureCoordinate * screenSize / 90.0 + gameTime * vec2(90.0, -70.0));
    float voidMask = smoothstep(0.36, 0.86, densityNorm * voidNoise) * (1.0 - hot * 0.55);
    color = mix(color, blackViolet, voidMask * 0.30);

    float darkenFactor = broadFog * 0.22;
    color *= (1.0 - darkenFactor * 0.28);
    color *= 1.04;

    float coverageFog = smoothstep(0.02, 0.58, coverage);
    float alpha = min(broadFog * 0.54 + wispFog * 0.42 + coverageFog * 0.12 + hot * 0.06 + whiteFleck * 0.07 + darkenFactor * 0.34, 0.74);
    if (alpha < 0.005) return false;

    compositeColor = vec4(color, alpha);
    return true;
}
