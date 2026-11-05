#version 150

uniform sampler2D BodyMaterialSampler;
uniform int BodyDepthAvailable;
#moj_import <shadowedhearts:dark_ball_anime_motion.glsl>

uniform float CoreCoverage;
uniform int EnergySourceHandoff;
in vec3 cameraRelativePosition;
in vec3 sourcePosition;
out vec4 fragColor;

void main() {
    // Retire captured source material as it reaches the ball.
    float coverage = clamp(CoreCoverage, 0.0, 1.0) * animeVisibility(sourcePosition);
    if (EnergySourceHandoff != 0 ? coverage <= 0.0 : coverage < 0.08) discard;
    float distanceToCamera = max(length(cameraRelativePosition), 0.0001);
    // Scene and source self-occlusion use the writable hardware depth attachment.
    vec4 existing = BodyDepthAvailable != 0
            ? texelFetch(BodyMaterialSampler, ivec2(gl_FragCoord.xy), 0) : vec4(0.0);
    // Fading this nearer core must not erase body coverage already supplied
    // by the energy body, then suddenly reveal it when the core reaches its cutoff.
    float materialDistance = distanceToCamera;
    if (existing.r > 0.002) {
        // Fill coverage from the real moving triangles while retaining a
        // nearer energy surface depth. This is a union of supported geometry, not a
        // screen-space hole fill; alpha-cutout gaps have no core fragments.
        float existingDistance = existing.a < -2.0 ? -existing.a - 2.0 : existing.a;
        if (existingDistance > 0.0001) materialDistance = min(materialDistance, existingDistance);
    }
    float depthPayload = -(2.0 + materialDistance);
    // R is geometric occupancy throughout the energy handoff. Putting the
    // complementary weights in R would seed a second 0.5 contour around the
    // source inside the visible energy body. B alone carries presentation alpha;
    // its sum keeps shared pixels opaque while either exterior fades normally.
    float bodyCoverage = EnergySourceHandoff != 0
            ? 1.0 : max(coverage, existing.r);
    float bodyAuthority = EnergySourceHandoff != 0
            ? min(1.0, coverage + clamp(existing.b, 0.0, 1.0))
            : max(coverage, clamp(existing.b, 0.0, 1.0));
    fragColor = vec4(bodyCoverage, 0.0, bodyAuthority, depthPayload);
}
