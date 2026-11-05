#version 150

uniform float Time;
uniform float OpeningAge;
uniform vec3 PortalCamera;
// Padded local half-width, top and bottom; the opening still occupies its original coordinates.
uniform vec3 PortalBounds;
uniform float FogFloor;
uniform float FogImpact;
uniform float SurfaceCoverage;
uniform int SurfacePass;
uniform vec4 ColorModulator;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float portalNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(cell), hash3(cell + vec3(1, 0, 0)), f.x),
                   mix(hash3(cell + vec3(0, 1, 0)), hash3(cell + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(cell + vec3(0, 0, 1)), hash3(cell + vec3(1, 0, 1)), f.x),
                   mix(hash3(cell + vec3(0, 1, 1)), hash3(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

vec2 rotate(vec2 p, float a) {
    float s = sin(a), c = cos(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float gaussian(float distance) {
    return exp(-distance * distance);
}

void main() {
    vec2 p = vec2(texCoord.x * 2.0 - 1.0, 1.0 - texCoord.y * 2.0);
    if (SurfacePass != 0) {
        // Geometry carries these wisps along the actual rock. UV + stable phase keeps their
        // internal curl attached to the moving wisp rather than to the viewer's position.
        float r2 = dot(p, p);
        if (r2 >= 1.0) discard;
        float phase = vertexColor.g * 37.0;
        vec2 curl = rotate(p, Time * 0.20 + phase + (1.0 - r2) * 1.3);
        float density = 0.0;
        float light = 0.0;
        for (int layer = 0; layer < 4; layer++) {
            vec3 flow = vec3(curl * 2.7 + vec2(phase, -Time * 0.14), float(layer) * 0.43 + phase);
            float n = portalNoise(flow);
            density += smoothstep(0.20, 0.78, n) * 0.25;
            light += smoothstep(0.52, 0.90, n) * 0.25;
        }
        float envelope = pow(max(0.0, 1.0 - r2), 1.6);
        float opacity = (1.0 - exp(-density * envelope * 1.6)) * vertexColor.a;
        if (opacity < 0.002) discard;
        vec3 tint = mix(vec3(0.46, 0.025, 0.95), vec3(0.82, 0.09, 0.42),
                        smoothstep(0.8, 1.0, vertexColor.r));
        vec3 color = vec3(0.007, 0.003, 0.016) + tint * (0.16 + light * 0.65);
        fragColor = vec4(color, opacity) * ColorModulator;
        return;
    }
    // Keep the quad and pixel grid fixed while a crooked tear grows tall, then peels open.
    // At 2.1 seconds this mapping becomes exactly the established portal coordinates.
    if (OpeningAge <= 0.025) discard;
    vec2 physicalP = p;
    float unfold = smoothstep(0.95, 2.10, OpeningAge);
    float height = mix(0.016, 1.0, smoothstep(0.16, 1.20, OpeningAge));
    float width = mix(0.012, 1.0, unfold);
    float crooked = (sin(p.y * 10.0 + 1.1) * 0.018 + sin(p.y * 24.0) * 0.006)
                  * smoothstep(0.25, 0.8, OpeningAge) * (1.0 - unfold);
    p = vec2((p.x - crooked) / width, p.y / height);
    float presence = smoothstep(0.025, 0.28, OpeningAge);
    // Compute the pixel cone before divergent exits. It widens with distance into the tunnel.
    float footprint = max(length(dFdx(p)), length(dFdy(p)));
    float frameFade = 1.0 - smoothstep(PortalBounds.x - 0.18, PortalBounds.x - 0.005, abs(physicalP.x));
    frameFade *= 1.0 - smoothstep(PortalBounds.y - 0.18, PortalBounds.y - 0.005, physicalP.y);
    frameFade *= smoothstep(PortalBounds.z + 0.005, PortalBounds.z + 0.18, physicalP.y);
    float radius = length(p);
    float vortexFade = 1.0 - smoothstep(1.08, 1.35, radius);
    if (frameFade <= 0.0) discard;
    // Before the stream starts, the empty area outside the growing tear needs no ray march.
    if (OpeningAge <= 2.1 && vortexFade <= 0.0) discard;

    float angle = atan(p.y, p.x + 0.00001);
    float time = Time;
    float drift = portalNoise(vec3(p * 4.0, time * 0.15));
    float mouth = 0.645 + sin(angle * 5.0 - time * 0.55) * 0.024
                        + sin(angle * 9.0 + time * 0.37) * 0.012 + (drift - 0.5) * 0.045;
    float aa = max(footprint * 1.25, 0.003);
    // One shared transition carries the violet inner rim into the dark outer miasma.
    float rimBlend = smoothstep(mouth - 0.07 - aa, mouth + 0.12 + aa, radius);
    float aperture = 1.0 - rimBlend;

    // Trace from the visible plane into a hollow, tapering volume behind it. Local XY use
    // half-width/half-height; Z uses half-width. Clamp grazing rays without rotating the quad.
    float eyeDepth = max(PortalCamera.z, 0.35);
    vec2 slope = (p - PortalCamera.xy) / eyeDepth;
    slope /= max(1.0, length(slope) / 2.0);
    int steps = int(mix(48.0, 32.0, smoothstep(5.0, 18.0, PortalCamera.z)));
    vec3 violet = mix(vec3(0.46, 0.025, 0.95), vec3(0.82, 0.09, 0.42),
                      smoothstep(0.8, 1.0, vertexColor.r));
    vec3 tunnelLight = vec3(0.0);
    float transmission = 1.0;
    if (aperture > 0.0) {
        float cursor = 0.0;
        for (int i = 0; i < 48; i++) {
            if (i >= steps || cursor >= 3.4 || transmission < 0.025) break;
            // Distance-guided cone marching spends samples at the thin wall and skips the void.
            // The safety band covers its animated corrugation; the finite cap bounds GPU work.
            float guideRadius = mouth * exp(-cursor * 0.29);
            float guideDistance = abs(length(p + slope * cursor) - guideRadius);
            float stride = clamp((0.018 + max(0.0, guideDistance - 0.06) * 0.55)
                                 / (0.65 + length(slope)), 0.009, 0.24);
            float start = cursor;
            float end = min(3.4, cursor + stride);
            float depth = (start + end) * 0.5;
            cursor = end;
            vec2 q = p + slope * depth;
            vec2 curl = rotate(q, depth * 0.85 - time * 0.16);
            float n = portalNoise(vec3(curl * 5.3, depth * 2.1 - time * 0.23));
            float theta = atan(q.y, q.x + 0.00001);
            float wallRadius = mouth * exp(-depth * 0.29)
                             + sin(theta * 3.0 + depth * 3.4 - time * 0.65) * 0.025;
            float cone = footprint * (1.0 + depth / eyeDepth);
            // Integrate the ray segment's footprint as well as the pixel cone so a thin wall
            // does not turn into regularly spaced bands between distant samples.
            float segmentFootprint = (end - start) * (length(slope) + wallRadius * 0.29 + 0.14) * 0.5;
            float thickness = sqrt(0.032 * 0.032 + cone * cone * 2.25 + segmentFootprint * segmentFootprint);
            float wall = gaussian((length(q) - wallRadius - (n - 0.5) * 0.055) / thickness);
            float phase = theta * 4.0 + depth * 6.3 - time * 0.85 + n * 2.2;
            float ribbon = pow(0.5 + 0.5 * sin(phase), 2.0);
            float strandAngle = theta + depth * 1.575 - time * 0.2125;
            float strands = portalNoise(vec3(vec2(cos(strandAngle), sin(strandAngle)) * 12.0,
                                             depth * 0.65 - time * 0.12));
            strands = mix(strands, 0.5, smoothstep(0.018, 0.07, cone));
            ribbon *= 0.25 + smoothstep(0.30, 0.77, strands) * 1.4;
            float density = wall * (2.0 + n * 4.0) * (0.032 / thickness);
            float opacity = 1.0 - exp(-density * (end - start) * 3.0);
            // Absorption darkens the recess; broken spiral ribbons reveal the curved inner wall.
            vec3 light = violet * (0.07 + ribbon * 1.15 + n * 0.09) * exp(-depth * 0.65);
            tunnelLight += transmission * opacity * light;
            transmission *= 1.0 - opacity;
        }
    }
    vec3 premultiplied = (tunnelLight + vec3(0.0007, 0.0002, 0.0015)) * aperture;
    float alpha = aperture;

    // A broad, torn fire lip instead of a uniformly bright neon outline.
    float lipDistance = radius - mouth;
    vec2 fireFlow = rotate(p, -time * 0.18) * vec2(15.0, 8.0)
                  + vec2(sin(p.y * 8.0 - time) * 0.7, -time * 0.65);
    float flame = portalNoise(vec3(fireFlow, time * 0.38));
    float hot = smoothstep(0.43, 0.78, flame);
    float lipWidth = 0.012 + hot * 0.020 + aa;
    float lip = gaussian((lipDistance - (flame - 0.5) * 0.035) / lipWidth);
    float tongues = smoothstep(-0.025, 0.015, lipDistance)
                  * (1.0 - smoothstep(0.025, 0.075 + hot * 0.13, lipDistance)) * hot;
    float lipAlpha = max(lip * (0.28 + hot * 0.6), tongues * 0.55);
    lipAlpha *= mix(1.0, 0.35, rimBlend);
    vec3 lipColor = mix(violet, vec3(0.76, 0.24, 1.0) * violet.b, hot * 0.55) * (0.65 + hot);
    premultiplied = lipColor * lipAlpha + premultiplied * (1.0 - lipAlpha);
    alpha = lipAlpha + alpha * (1.0 - lipAlpha);
    premultiplied *= vortexFade;
    alpha *= vortexFade;

    // Front-to-back integration of cool black smoke and sparse violet wisps spilling out of
    // the mouth, then pouring down to the current landing face. Separate world-space wisps
    // carry the flow around the rock; the saved-summit bank is a distant/uncached fallback.
    vec3 smokeLight = vec3(0.0);
    float smokeTransmission = 1.0;
    int smokeSteps = PortalCamera.z < 9.0 ? 10 : 6;
    for (int i = 0; i < 10; i++) {
        if (i >= smokeSteps) break;
        float z = 0.40 - (float(i) + 0.5) * 0.65 / float(smokeSteps);
        vec2 q = p - slope * z;
        vec2 swirl = rotate(q, z * 1.2 - time * 0.10 + length(q) * 1.8);
        vec3 flow = vec3(swirl * 5.8 + vec2(0.0, -time * 0.38), z * 4.0 + time * 0.16);
        float n = portalNoise(flow);
        float detail = portalNoise(flow * 2.03 + 17.1);
        float turbulence = mix(n, n * 0.7 + detail * 0.3, 1.0 - smoothstep(0.025, 0.08, footprint));
        float plume = smoothstep(0.25, 0.85, 0.5 + 0.5 * sin(angle * 3.0 + z * 5.0 + n * 4.0 - time * 0.35));
        float smokeRadius = mouth + 0.055 + plume * 0.10 + (n - 0.5) * 0.10 + z * 0.12;
        float envelope = gaussian((length(q) - smokeRadius) / (0.09 + plume * 0.08));
        float wisps = smoothstep(0.30, 0.78, turbulence);
        float ringDensity = envelope * wisps * (0.45 + plume) * 4.0 * mix(0.55, 1.0, rimBlend) * vortexFade;
        float veins = pow(smoothstep(0.50, 0.83, detail), 2.0) * smoothstep(0.45, 0.7, n);
        vec3 ringColor = mix(violet * 0.16, vec3(0.006, 0.002, 0.012), rimBlend)
                       + violet * veins * 0.85;

        vec2 fallQ = physicalP - slope * z;
        float fallProgress = clamp((-fallQ.y - 0.50) / max(0.25, -FogFloor - 0.50), 0.0, 1.0);
        float fallCenter = FogImpact * fallProgress
                         + sin(time * 0.65 - fallProgress * 2.0 + z * 3.0) * 0.045
                         + sin(time * 0.97 + fallProgress * 4.0) * 0.035;
        float fallWidth = mix(0.20, 0.34, fallProgress);
        float release = smoothstep(0.42, 0.68, -fallQ.y);
        float landingFade = smoothstep(FogFloor - 0.30, FogFloor + 0.16, fallQ.y);
        // Y-up plus positive time advection moves the density down, toward the meteorite.
        vec3 fallingFlow = vec3((fallQ.x - fallCenter) * 6.5, fallQ.y * 5.0 + time * 1.8, z * 5.0);
        float fallingNoise = portalNoise(fallingFlow);
        float fallingDetail = portalNoise(fallingFlow * 2.03 + 23.7);
        float fallingWisps = smoothstep(0.22, 0.75, fallingNoise * 0.7 + fallingDetail * 0.3);
        float stream = gaussian((fallQ.x - fallCenter) / fallWidth) * release * landingFade;
        // Include the entire soft front past the landing so the established flow is unchanged.
        float front = mix(-0.06, 1.06, smoothstep(2.1, 2.65, OpeningAge));
        float reached = 1.0 - smoothstep(front - 0.06, front + 0.06, fallProgress);
        float streamDensity = stream * fallingWisps * 5.0 * reached * smoothstep(2.1, 2.32, OpeningAge);
        float bank = gaussian((fallQ.y - FogFloor - 0.035) / 0.13)
                   * gaussian(fallQ.x / (0.48 + n * 0.12));
        float bankDensity = smoothstep(2.65, 3.15, OpeningAge)
                          * bank * (0.3 + fallingWisps * 0.7) * 2.6 * (1.0 - SurfaceCoverage);
        vec3 fallingColor = vec3(0.008, 0.003, 0.018)
                          + violet * (0.10 + fallingDetail * fallingDetail * fallingDetail * 0.55);
        vec3 bankColor = vec3(0.008, 0.003, 0.018) + violet * (0.07 + veins * 0.18);
        float density = ringDensity + streamDensity + bankDensity;
        vec3 smokeColor = (ringColor * ringDensity + fallingColor * streamDensity + bankColor * bankDensity)
                        / max(density, 0.0001);
        float opacity = 1.0 - exp(-density * 0.65 / float(smokeSteps));
        smokeLight += smokeTransmission * opacity * smokeColor;
        smokeTransmission *= 1.0 - opacity;
    }
    premultiplied = smokeLight + smokeTransmission * premultiplied;
    alpha = (1.0 - smokeTransmission) + smokeTransmission * alpha;

    // The Minecraft pass uses straight-alpha blending. Unpremultiply once, after integration.
    alpha *= frameFade * presence;
    premultiplied *= frameFade * presence;
    float openingPulse = gaussian((OpeningAge - 1.92) / 0.17) * 0.15;
    premultiplied *= 1.0 + openingPulse;
    if (alpha < 0.003) discard;
    fragColor = vec4(premultiplied / max(alpha, 0.0001), alpha * vertexColor.a) * ColorModulator;
}
