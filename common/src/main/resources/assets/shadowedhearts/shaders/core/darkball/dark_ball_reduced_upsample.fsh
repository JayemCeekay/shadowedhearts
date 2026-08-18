#version 150

uniform sampler2D Sampler0;
uniform sampler2D SceneDepthSampler;
uniform mat4 InvProjMat;

in vec2 texCoord0;
out vec4 fragColor;

float linearSceneDepth(float storedDepth) {
    float ndcDepth = clamp(storedDepth, 0.0, 1.0) * 2.0 - 1.0;
    float viewZ = InvProjMat[2][2] * ndcDepth
            + InvProjMat[3][2];
    float viewW = InvProjMat[2][3] * ndcDepth
            + InvProjMat[3][3];
    return abs(viewZ / (abs(viewW) > 0.000001
            ? viewW : (viewW < 0.0 ? -0.000001 : 0.000001)));
}

float sceneDepthAt(ivec2 styledCoord, ivec2 styledExtent) {
    ivec2 sceneExtent =
            max(textureSize(SceneDepthSampler, 0), ivec2(1));
    vec2 styledCenterUv =
            (vec2(styledCoord) + vec2(0.5)) / vec2(styledExtent);
    ivec2 sceneCoord = clamp(
            ivec2(styledCenterUv * vec2(sceneExtent)),
            ivec2(0), sceneExtent - ivec2(1));
    return texelFetch(SceneDepthSampler, sceneCoord, 0).r;
}

void main() {
    ivec2 extent = max(textureSize(Sampler0, 0), ivec2(1));
    ivec2 maximumCoord = extent - ivec2(1);
    vec2 sourcePosition = clamp(texCoord0, vec2(0.0), vec2(1.0))
            * vec2(extent) - vec2(0.5);
    ivec2 baseCoord = ivec2(floor(sourcePosition));
    vec2 fraction = fract(sourcePosition);

    ivec2 coordinates[4];
    coordinates[0] = clamp(baseCoord, ivec2(0), maximumCoord);
    coordinates[1] = clamp(
            baseCoord + ivec2(1, 0), ivec2(0), maximumCoord);
    coordinates[2] = clamp(
            baseCoord + ivec2(0, 1), ivec2(0), maximumCoord);
    coordinates[3] = clamp(
            baseCoord + ivec2(1, 1), ivec2(0), maximumCoord);

    float bilinearWeights[4];
    bilinearWeights[0] =
            (1.0 - fraction.x) * (1.0 - fraction.y);
    bilinearWeights[1] =
            fraction.x * (1.0 - fraction.y);
    bilinearWeights[2] =
            (1.0 - fraction.x) * fraction.y;
    bilinearWeights[3] =
            fraction.x * fraction.y;

    int nearestIndex = (fraction.x >= 0.5 ? 1 : 0)
            + (fraction.y >= 0.5 ? 2 : 0);
    vec4 nearestStyled =
            texelFetch(Sampler0, coordinates[nearestIndex], 0);
    float sceneDepths[4];
    for (int sampleIndex = 0; sampleIndex < 4; sampleIndex++) {
        sceneDepths[sampleIndex] = linearSceneDepth(sceneDepthAt(
                coordinates[sampleIndex], extent));
    }
    float referenceDepth = sceneDepths[nearestIndex];
    float compatibleWeight = 0.0;
    for (int sampleIndex = 0; sampleIndex < 4; sampleIndex++) {
        float candidateDepth = sceneDepths[sampleIndex];
        float continuityLimit = max(
                min(referenceDepth, candidateDepth) * 0.012, 0.04);
        float continuity = 1.0 - smoothstep(
                continuityLimit,
                continuityLimit * 2.25,
                abs(candidateDepth - referenceDepth));
        compatibleWeight +=
                bilinearWeights[sampleIndex] * continuity;
    }

    // Let hardware perform the ordinary RGB reconstruction, but do not let its
    // bilinear alpha grow the silhouette into a nearest source texel that is
    // empty. Capping to the nearest alpha also preserves soft source coverage
    // without manufacturing a half-resolution halo.
    vec4 styled = texture(Sampler0, texCoord0);
    float sceneDiscontinuity =
            1.0 - clamp(compatibleWeight, 0.0, 1.0);
    if (styled.a < 0.001 || nearestStyled.a < 0.001) {
        discard;
    }
    float conservativeAlpha = min(styled.a, nearestStyled.a);
    conservativeAlpha *= 1.0 - smoothstep(0.10, 0.35,
            sceneDiscontinuity) * (1.0 - clamp(nearestStyled.a, 0.0, 1.0));
    if (conservativeAlpha < 0.001) {
        discard;
    }
    fragColor = vec4(styled.rgb, conservativeAlpha);
}
