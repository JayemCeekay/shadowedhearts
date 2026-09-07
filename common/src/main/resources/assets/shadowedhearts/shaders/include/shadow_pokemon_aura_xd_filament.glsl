// Coherent local-UV plasma material for the XD-faithful aura.
//
// PARTICLE Color is a compact per-quad parameter channel so many independent
// clusters can remain in one draw:
//   R = seed offset, G = phase offset, B = intensity, A = opacity.
// FilamentSeed and FilamentPhase provide a per-pass base value. Keeping the
// topology entirely in local UV prevents branches from swimming across their
// parent burst as either the camera or Pokemon moves.

const float SHADOW_AURA_FILAMENT_PI = 3.14159265359;
const float SHADOW_AURA_FILAMENT_TAU = 6.28318530718;

float shadowAuraFilamentHash(float value) {
    return fract(sin(value * 91.3458 + 17.153) * 47453.5453);
}

mat2 shadowAuraFilamentRotation(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c);
}

float shadowAuraFilamentMainX(float y, float seed, float phase) {
    float lowFrequency = mix(1.35, 2.10, shadowAuraFilamentHash(seed + 3.7));
    float highFrequency = mix(3.40, 5.20, shadowAuraFilamentHash(seed + 8.1));
    return 0.17 * sin(y * lowFrequency * SHADOW_AURA_FILAMENT_PI + phase)
            + 0.055 * sin(y * highFrequency * SHADOW_AURA_FILAMENT_PI
                    - phase * 0.63 + seed * 0.17);
}

float shadowAuraFilamentBranchDistance(
        vec2 point,
        float seed,
        float phase,
        float side,
        float verticalOffset
) {
    float startY = verticalOffset - 0.24;
    float endY = verticalOffset + 0.34;
    float progress = clamp((point.y - startY) / max(endY - startY, 0.001), 0.0, 1.0);
    float startX = shadowAuraFilamentMainX(startY, seed, phase);
    float spread = mix(0.30, 0.52, shadowAuraFilamentHash(seed + verticalOffset * 11.0));
    float branchX = startX + side * spread * pow(progress, 0.82)
            + 0.040 * sin(progress * SHADOW_AURA_FILAMENT_TAU
                    + phase * 1.31 + seed);
    float verticalGate = smoothstep(startY, startY + 0.08, point.y)
            * (1.0 - smoothstep(endY - 0.08, endY, point.y));
    return mix(1.0, abs(point.x - branchX), verticalGate);
}

vec4 shadowPokemonAuraXdFilamentDensity(
        vec4 texColor,
        vec4 particleColor,
        vec2 textureCoordinate,
        float auraTime,
        float baseSeed,
        float basePhase
) {
    float particleSeed = baseSeed + particleColor.r * 97.0;
    float phase = basePhase + particleColor.g * SHADOW_AURA_FILAMENT_TAU
            + auraTime * 2.35;
    float intensity = mix(0.72, 1.28, clamp(particleColor.b, 0.0, 1.0));
    float opacity = max(particleColor.a, 0.0);

    vec2 point = textureCoordinate * 2.0 - 1.0;
    float rotation = (shadowAuraFilamentHash(particleSeed + 1.3) - 0.5) * 0.72;
    point = shadowAuraFilamentRotation(rotation) * point;

    float mainDistance = abs(point.x
            - shadowAuraFilamentMainX(point.y, particleSeed, phase));
    float branchA = shadowAuraFilamentBranchDistance(
            point,
            particleSeed + 2.7,
            phase,
            -1.0,
            -0.24
    );
    float branchB = shadowAuraFilamentBranchDistance(
            point,
            particleSeed + 7.2,
            phase * 0.83,
            1.0,
            0.12
    );

    vec2 loopCenter = vec2(
            mix(-0.16, 0.18, shadowAuraFilamentHash(particleSeed + 5.4)),
            mix(-0.20, 0.22, shadowAuraFilamentHash(particleSeed + 9.8))
    );
    vec2 loopRadii = vec2(
            mix(0.25, 0.38, shadowAuraFilamentHash(particleSeed + 4.2)),
            mix(0.17, 0.30, shadowAuraFilamentHash(particleSeed + 6.6))
    );
    vec2 loopPoint = shadowAuraFilamentRotation(phase * 0.09 + particleSeed)
            * (point - loopCenter);
    float loopDistance = abs(length(loopPoint / loopRadii) - 1.0)
            * min(loopRadii.x, loopRadii.y);
    float loopAngle = atan(loopPoint.y, loopPoint.x);
    float loopGap = smoothstep(-0.30, 0.14,
            sin(loopAngle * 1.5 + particleSeed * 0.71 + phase * 0.22));
    loopDistance = mix(1.0, loopDistance, loopGap);

    float distanceToFilament = min(min(mainDistance, branchA), min(branchB, loopDistance));
    float alongFlicker = 0.82 + 0.18 * sin(
            point.y * 13.0 + point.x * 7.0 + phase * 1.70 + particleSeed);

    float textureEnvelope = smoothstep(0.025, 0.30, texColor.a);
    float radialEnvelope = 1.0 - smoothstep(0.74, 1.13, length(point));
    float envelope = textureEnvelope * radialEnvelope * opacity;

    float core = (1.0 - smoothstep(0.010, 0.028, distanceToFilament))
            * alongFlicker * envelope * intensity;
    float halo = (1.0 - smoothstep(0.020, 0.105, distanceToFilament))
            * envelope * intensity;

    // Match the shared density target contract: R broad, G heat, B wisp,
    // A coverage. The narrow core remains in heat while the broader violet
    // plasma halo participates in the wisp response.
    return vec4(0.0, core * 1.18, halo * 0.78, halo * 0.34);
}
