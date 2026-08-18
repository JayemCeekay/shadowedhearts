#version 150

uniform sampler2D Sampler0;
uniform float JumpStep;

in vec2 texCoord0;
out vec4 fragColor;

bool validSeed(vec2 seedUv) {
    return seedUv.x >= 0.0 && seedUv.y >= 0.0;
}

void main() {
    vec2 fieldSize = vec2(textureSize(Sampler0, 0));
    vec2 texel = 1.0 / max(fieldSize, vec2(1.0));
    vec2 bestSeed = texture(Sampler0, texCoord0).rg;
    float bestDistanceSquared = validSeed(bestSeed)
            ? dot((bestSeed - texCoord0) * fieldSize,
                    (bestSeed - texCoord0) * fieldSize)
            : 1.0e30;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) {
                continue;
            }
            vec2 sampleUv = clamp(texCoord0
                    + vec2(float(x), float(y)) * texel * JumpStep,
                    vec2(0.0), vec2(1.0));
            vec2 candidateSeed = texture(Sampler0, sampleUv).rg;
            if (!validSeed(candidateSeed)) {
                continue;
            }
            vec2 candidateDelta = (candidateSeed - texCoord0) * fieldSize;
            float candidateDistanceSquared = dot(
                    candidateDelta, candidateDelta);
            if (candidateDistanceSquared < bestDistanceSquared) {
                bestDistanceSquared = candidateDistanceSquared;
                bestSeed = candidateSeed;
            }
        }
    }

    fragColor = vec4(bestSeed, 0.0, 1.0);
}
