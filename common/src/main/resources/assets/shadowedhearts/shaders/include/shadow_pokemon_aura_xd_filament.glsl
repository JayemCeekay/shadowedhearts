// One broad CPU carrier holds an independently evolving cellular puff.
// Color R seed, G normalized life, B material role (.25 dark/.75 pink/0 flake),
// A lifecycle coverage. R/A density absorbs light; G/B density emits pink/violet.
// Neither the carrier's sides nor a circular sprite define luminous outlines.
vec2 shadowXdCellHash(vec2 cell, float seed) {
    return fract(sin(vec2(dot(cell, vec2(127.1, 311.7)),
                         dot(cell, vec2(269.5, 183.3))) + seed) * 43758.5453);
}

// A single bounded 3x3 search returns the three nearest animated sites.
// Their gaps form curved walls and pinched junctions, not a repeated glyph.
vec3 shadowXdCellDistances(vec2 p, float seed, float phase) {
    vec2 cell = floor(p);
    vec2 local = fract(p);
    vec3 nearest = vec3(100.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 random = shadowXdCellHash(cell + offset, seed);
            vec2 site = 0.5 + (random - 0.5) * 0.64
                    + sin(random * 6.283185 + phase * 0.50) * 0.10;
            vec2 delta = offset + site - local;
            float distanceSquared = dot(delta, delta);
            if (distanceSquared < nearest.x) {
                nearest = vec3(distanceSquared, nearest.x, nearest.y);
            } else if (distanceSquared < nearest.y) {
                nearest.yz = vec2(distanceSquared, nearest.y);
            } else {
                nearest.z = min(nearest.z, distanceSquared);
            }
        }
    }
    return sqrt(nearest);
}

vec4 shadowXdFeatherDensity(vec2 coordinate, float seed, float life, float coverage) {
    // A bowed spindle with a pale central spine, not a round point sprite.
    // Rotation and flutter come from the CPU billboard; local curvature remains
    // coherent instead of flickering into an unrelated shape on each frame.
    vec2 p = coordinate * 2.0 - 1.0;
    float taper = max(0.0, 1.0 - p.y * p.y);
    float center = sin(p.y * 2.4 + seed) * 0.14 * taper;
    float across = abs(p.x - center);
    float aa = max(fwidth(across), 0.012);
    float widthVariation = fract(seed * 0.137);
    float brightnessVariation = mix(0.82, 1.22, fract(seed * 0.473));
    float width = mix(0.16, 0.23, widthVariation) * pow(taper, 0.70);
    float leaf = (1.0 - smoothstep(width * 0.30, width + aa, across))
            * (1.0 - smoothstep(0.78, 0.98, abs(p.y)));
    float spine = (1.0 - smoothstep(0.012, mix(0.036, 0.052, widthVariation) + aa, across)) * taper * leaf;
    float glow = (1.0 - smoothstep(width * 0.70, width + 0.24 + aa, across))
            * (1.0 - smoothstep(0.65, 0.99, abs(p.y)));
    float shimmer = 0.84 + 0.16 * sin(life * 5.0 + seed * 6.283185);
    // Keep small antialiased feathers luminous enough to reach the pale-core
    // response. This extra G belongs only to the accent, never the pink puffs.
    return vec4(0.0, (leaf * 1.10 + spine * 2.20) * shimmer * brightnessVariation,
            glow * 0.28 * brightnessVariation, leaf * 0.08) * coverage;
}

vec4 shadowPokemonAuraXdFilamentDensity(
        vec4 texColor, vec4 particleColor, vec2 textureCoordinate,
        float auraTime, float baseSeed, float basePhase
) {
    vec2 puffCoord = vec2(textureCoordinate.x, textureCoordinate.y) * 2.0 - 1.0;
    // PARTICLE stores this seed in one normalized byte. Recover that exact
    // grid before the high-gain cell hash: even one interpolated float ULP can
    // otherwise move sites far enough to make abrupt bands across a puff.
    float puffSeed = floor(clamp(particleColor.r, 0.0, 1.0) * 255.0 + 0.5) / 255.0;
    float seed = baseSeed + puffSeed * 97.0;
    float life = clamp(particleColor.g, 0.0, 1.0);
    if (particleColor.b < 0.10) {
        return shadowXdFeatherDensity(textureCoordinate, seed, life, max(particleColor.a, 0.0));
    }
    float isHighlight = step(0.5, particleColor.b);
    // The same sites persist through life while differential rotation and
    // strain curl their links. Motion remains continuous and independent of
    // the particle's smoothly rising center, rather than rehashing each frame.
    float phase = basePhase + life * 2.20;
    // Retain only a tiny transverse shimmer from the shared clock. The larger
    // twisting/stretching motion depends on life, so clock changes cannot
    // suddenly rotate or stretch the whole cellular field.
    float microPhase = sin(auraTime * 1200.0 * 0.010 + seed) * 0.10;
    float angle = puffSeed * 6.283185 + sin(phase + seed) * 0.05;
    mat2 orientation = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    vec2 p = orientation * puffCoord * mix(1.55, 2.15, fract(seed * 0.37));
    vec2 curlCenter = vec2(sin(seed * 0.73), cos(seed * 0.51)) * 0.45;
    vec2 relative = p - curlCenter;
    float curlAngle = ((life - 0.5) * 0.65 + sin(phase * 0.65 + seed) * 0.22)
            * exp(-dot(relative, relative) * 0.35);
    mat2 curl = mat2(cos(curlAngle), -sin(curlAngle), sin(curlAngle), cos(curlAngle));
    p = curlCenter + curl * relative;
    float strain = 1.0 + sin(phase * 0.70 + seed * 0.23) * 0.22;
    p *= vec2(strain, 1.0 / strain);
    // Broad rolls pull neighboring links in different directions. The smaller
    // transverse bend roughens their contours without adding another cell search.
    p += vec2(sin(p.y * 1.35 + phase * 0.70 + seed),
              sin(p.x * 1.25 - phase * 0.55 + seed * 0.31)) * 0.34;
    p += sin(p.yx * 3.1 + vec2(phase * 0.8 + microPhase, -phase * 0.7 - microPhase) + seed) * 0.09;
    p += vec2(sin(seed + phase * 0.5) * 0.20, -phase * 0.12);
    vec3 nearest = shadowXdCellDistances(p, seed, phase);
    float wallGap = nearest.y - nearest.x;
    float junctionGap = nearest.z - nearest.x;
    // Evaluate all derivatives before the wrapper can discard empty pixels.
    float wallAA = max(fwidth(wallGap), 0.012);
    float junctionAA = max(fwidth(junctionGap), 0.012);
    float broadWidth = 0.5 + 0.5 * sin(p.x * 1.1 - p.y * 1.4 + phase * 0.45 + seed * 0.13);
    float linkVariation = 0.5 + 0.5 * sin(p.x * 2.3 + p.y * 0.6 - phase * 0.85 + seed * 0.35);
    float widthField = broadWidth * 0.65 + linkVariation * 0.35;
    float wallWidth = mix(0.035, 0.185, widthField) * mix(1.35, 1.0, isHighlight);
    float wall = 1.0 - smoothstep(wallWidth * 0.25, wallWidth + wallAA, wallGap);
    float softWall = 1.0 - smoothstep(wallWidth * 0.45, wallWidth * 1.8 + 0.10 + wallAA, wallGap);
    float junctionWidth = mix(0.22, 0.33, broadWidth);
    float junction = 1.0 - smoothstep(0.045, junctionWidth + junctionAA, junctionGap);
    float knotCore = 1.0 - smoothstep(0.025, 0.12 + junctionAA, junctionGap);
    // This wider junction field is deposited in B, then spatially spread by
    // the existing depth-aware blur. G remains the sharper luminous structure.
    float junctionGlow = 1.0 - smoothstep(0.12, 0.64 + junctionAA, junctionGap);
    float opening = smoothstep(-0.35, 0.65,
            sin(p.x * 1.3 + p.y * 1.7 - phase * 0.55 + seed)
            + sin(p.y * 2.2 + phase * 0.65) * 0.35);
    // Some links fade almost completely while their filled junction remains;
    // a uniformly bright, equally thin cell perimeter reads as a wire grid.
    float linkPresence = opening * opening * (0.40 + linkVariation * 0.60);
    wall *= 0.015 + linkPresence * 0.985;
    // The dimmer violet population links the pink knots. Keep a soft remnant
    // of its walls through the openings instead of reducing both layers to
    // the same isolated bright islands.
    softWall *= mix(0.30 + opening * 0.70, 0.12 + opening * 0.88, isHighlight);
    // Knots brighten independently of their links, so not every cell retains
    // the same three equally bright arms or a permanently luminous center.
    float knotPresence = 0.48 + 0.52 * smoothstep(-0.25, 0.75,
            sin(p.x * 0.85 - p.y * 1.35 + phase * 0.80 + seed * 0.41));
    junction *= knotPresence;
    knotCore *= knotPresence;
    junctionGlow *= 0.55 + opening * 0.45;

    vec2 boundaryWarp = vec2(sin(puffCoord.y * 4.0 + seed + phase * 0.4),
                             sin(puffCoord.x * 5.0 - phase * 0.35 + seed)) * 0.065;
    float boundary = length(puffCoord + boundaryWarp);
    float radius = length(puffCoord);
    // Fade throughout the radial footprint instead of holding a solid center
    // until a narrow outer band. The wider halo reaches beyond bright cells.
    // Both envelopes end at radius one, leaving every billboard corner empty.
    float puff = exp(-boundary * boundary * 1.60)
            * (1.0 - smoothstep(0.65, 1.0, radius));
    float haloPuff = exp(-boundary * boundary * 0.85)
            * (1.0 - smoothstep(0.72, 1.0, radius));
    float cellLobe = 1.0 - smoothstep(0.10, 0.65, nearest.x);
    float sheet = puff * (0.12 + softWall * 0.60 + cellLobe * 0.20);
    float highlight = puff * (wall * 0.26 + junction * 1.05 + knotCore * 0.90);
    float edgeGlow = haloPuff * (softWall * 0.30 + junctionGlow * 0.68);
    float envelope = max(particleColor.a, 0.0);
    // Distinct freely moving populations interleave in the same density field.
    // Dark cellular strands deposit no luminous core/halo at all. Pink puffs
    // carry selected knots and soft shoulders without repainting every opening.
    vec4 darkLayer = vec4(puff * (wall * 0.85 + junction * 0.45) + sheet * 0.20,
            0.0, 0.0, sheet * 0.62);
    vec4 highlightLayer = vec4(0.0, highlight * 0.95, edgeGlow * 0.75, 0.0);
    return mix(darkLayer, highlightLayer, isHighlight) * envelope;
}
