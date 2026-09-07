// Post-material shaping for non-Signature Shadow Pokemon aura styles.
// The shared composite remains the authoritative density response. These
// functions only reshape its color and monotonic alpha, so style isolation
// cannot reintroduce derivative rims or nested density contours.

vec4 shadowPokemonAuraColosseumStyle(
        vec4 densityData,
        vec4 signatureColor
) {
    float broadAmount = smoothstep(0.012, 0.58, densityData.r);
    float wispAmount = smoothstep(0.016, 0.46, densityData.b);
    float heatAmount = smoothstep(0.12, 0.72, densityData.g);
    float detailAmount = clamp(wispAmount * 0.72 + heatAmount * 0.28, 0.0, 1.0);

    vec3 shadow = vec3(0.006, 0.001, 0.014);
    vec3 deepHaze = vec3(0.035, 0.008, 0.065);
    vec3 smokeHaze = vec3(0.11, 0.025, 0.18);
    vec3 restrainedDetail = vec3(0.40, 0.12, 0.58);
    vec3 restrainedHot = vec3(0.72, 0.28, 0.84);

    vec3 broadColor = mix(shadow, deepHaze, broadAmount * 0.72);
    broadColor = mix(broadColor, smokeHaze, broadAmount * broadAmount * 0.30);

    // Darken only broad-dominant material. Detail erodes that darkening
    // smoothly instead of tracing a density boundary.
    float broadDarkening = broadAmount * (1.0 - detailAmount * 0.72);
    vec3 color = mix(signatureColor.rgb, broadColor, broadDarkening * 0.76);
    color = mix(color, restrainedDetail, wispAmount * 0.24);
    color = mix(color, restrainedHot, heatAmount * 0.11);
    color *= 0.94;

    // A constant scale preserves the shared composite's monotonic response.
    float alpha = min(signatureColor.a * 0.94, 0.74);
    return vec4(color, alpha);
}

vec4 shadowPokemonAuraXdFaithfulStyle(
        vec4 densityData,
        vec4 signatureColor
) {
    float broadAmount = smoothstep(0.014, 0.56, densityData.r);
    float wispAmount = smoothstep(0.010, 0.38, densityData.b);
    float heatAmount = smoothstep(0.08, 0.64, densityData.g);

    vec3 shadow = vec3(0.010, 0.001, 0.022);
    vec3 deepHaze = vec3(0.055, 0.008, 0.095);
    vec3 detail = vec3(0.56, 0.18, 0.82);
    vec3 hot = vec3(0.98, 0.46, 1.00);
    vec3 filamentCore = vec3(1.00, 0.94, 1.00);

    vec3 sparseBroadColor = mix(shadow, deepHaze, broadAmount * 0.78);
    vec3 color = mix(
            signatureColor.rgb,
            sparseBroadColor,
            broadAmount * (1.0 - wispAmount * 0.55) * 0.24
    );
    color = mix(color, detail, wispAmount * 0.24);
    color = mix(color, hot, heatAmount * 0.31);
    color = mix(color, filamentCore,
            smoothstep(0.58, 1.16, densityData.g) * 0.46);
    color *= 1.03;

    // Lower the continuous envelope, then restore opacity monotonically from
    // detail channels. This keeps isolated plasma readable without outlining
    // overlapping broad puffs.
    float detailLift = smoothstep(
            0.015,
            0.62,
            densityData.b + densityData.g * 0.82
    ) * 0.085;
    float alpha = min(signatureColor.a * 0.78 + detailLift, 0.74);
    return vec4(color, alpha);
}
