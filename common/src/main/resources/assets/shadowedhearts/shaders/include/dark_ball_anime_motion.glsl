#ifndef SHADOWEDHEARTS_DARK_BALL_ANIME_MOTION
#define SHADOWEDHEARTS_DARK_BALL_ANIME_MOTION

uniform vec3 AnimeCenter;
uniform vec3 AnimeDirection;
uniform vec3 AnimeBend; // fixed ball-local midpoint offset of the intake arch
uniform vec3 AnimeTarget;
uniform vec4 AnimeShape; // radius, axial minimum, axial span, lobe blend
uniform vec2 AnimeClock; // pull progress, local animation seconds
uniform float AnimeSquash; // authored squeeze before the main stretch
float animeSmooth(float value) { return value * value * (3.0 - 2.0 * value); }
float animeOrder(vec3 p) {
    return animeSmooth(clamp((dot(p - AnimeCenter, AnimeDirection) - AnimeShape.y)
            / max(AnimeShape.z, 0.00000001), 0.0, 1.0));
}
float animeLocalProgress(vec3 p) {
    float lag = 1.0 - animeOrder(p);
    // Match the CPU field: a faster leading edge and a longer trailing hold,
    // with the final material still reaching the ball at pull=1.
    return clamp((AnimeClock.x - 0.46 * lag) / (0.52 + 0.02 * lag), 0.0, 1.0);
}
vec3 animeDeform(vec3 p) {
    vec3 relative = p - AnimeCenter;
    float scale = max(AnimeShape.x, 0.0001);
    float axial = dot(relative, AnimeDirection);
    vec3 radial = relative - AnimeDirection * axial;
    vec3 preferredUp = abs(AnimeDirection.z) < 0.95 ? vec3(0, 0, 1) : vec3(0, 1, 0);
    vec3 up = normalize(preferredUp - AnimeDirection * dot(preferredUp, AnimeDirection));
    vec3 side = cross(AnimeDirection, up);
    float u = dot(radial, side) / scale;
    float v = dot(radial, up) / scale;
    float a = axial / scale;
    float energy = AnimeShape.w * (1.0 - animeSmooth(clamp(
            (animeLocalProgress(p) - 0.20) / 0.70, 0.0, 1.0)));
    // Disjoint compact vortices: strong local turning, identity with zero slope
    // at support boundaries. Fixed axial twist breaks up straight rows of curls.
    float latticeAngle = 1.7 * a + 0.4;
    float lc = cos(latticeAngle), ls = sin(latticeAngle);
    vec2 grid = vec2(u * lc + v * ls, -u * ls + v * lc);
    float rowA = floor(grid.y / 0.55425626), rowB = rowA + 1.0;
    float offsetA = 0.5 * mod(rowA, 2.0), offsetB = 0.5 * mod(rowB, 2.0);
    vec2 cellA = vec2(floor(grid.x / 0.64 - offsetA + 0.5), rowA);
    vec2 cellB = vec2(floor(grid.x / 0.64 - offsetB + 0.5), rowB);
    vec2 qa = grid - vec2(0.64 * (cellA.x + offsetA), 0.55425626 * rowA);
    vec2 qb = grid - vec2(0.64 * (cellB.x + offsetB), 0.55425626 * rowB);
    bool useA = dot(qa, qa) <= dot(qb, qb);
    vec2 cell = useA ? cellA : cellB;
    vec2 q = useA ? qa : qb;
    float kernel = max(0.0, 1.0 - dot(q, q) / (0.30 * 0.30));
    float parity = mod(cell.x + cell.y, 2.0);
    float phase = 1.35 * AnimeClock.y + 1.1 * a + 1.7 * cell.x + 2.3 * cell.y;
    float roll = (1.0 - 2.0 * parity) * (1.0 + 0.5 * sin(phase)) * energy * kernel * kernel
            * (any(greaterThan(abs(cell), vec2(2.0))) ? 1.0 : 0.0);
    float rc = cos(roll), rs = sin(roll);
    vec2 delta = vec2(q.x * (rc - 1.0) - q.y * rs, q.x * rs + q.y * (rc - 1.0));
    float driftPhase = 1.35 * AnimeClock.y + 0.4;
    float x = (u + delta.x * lc - delta.y * ls + 0.003 * energy * sin(driftPhase)) * scale;
    float y = (v + delta.x * ls + delta.y * lc + 0.003 * energy * cos(driftPhase)) * scale;
    float angle = 0.18 * AnimeClock.y;
    float strain = energy * (0.003 + 0.009 * AnimeSquash);
    float c = cos(angle), s = sin(angle);
    float compressed = (x * c + y * s) * exp(-strain);
    float expanded = (-x * s + y * c) * exp(strain);
    vec3 transverse = side * (compressed * c - expanded * s)
            + up * (compressed * s + expanded * c);
    vec3 sourceAxis = AnimeCenter + AnimeDirection * axial;
    float travel = animeSmooth(animeLocalProgress(p));
    float width = (1.0 - travel) * (1.0 + 1.2 * travel);
    vec3 path = mix(sourceAxis, AnimeTarget, travel)
            + AnimeBend * (4.0 * travel * (1.0 - travel));
    return path + transverse * width;
}
float animeVisibility(vec3 p) {
    return 1.0 - animeSmooth(clamp((animeLocalProgress(p) - 0.96) / 0.04, 0.0, 1.0));
}
vec3 animeDeformVector(vec3 p, vec3 delta) {
    return animeDeform(p + delta) - animeDeform(p);
}
#endif
