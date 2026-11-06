#version 150

#moj_import <shadowedhearts:shadow_pokemon_aura_performance.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float AuraTime;
uniform float FilamentSeed;
uniform float FilamentPhase;

// Packed seed, age, material role and coverage belong to one whole puff.
// Smooth interpolation can perturb even identical vertex seeds by an ULP.
flat out vec4 vertexColor;
out vec2 texCoord0;
flat out vec4 xdPuffTransform;
flat out vec4 xdPuffCurl;
flat out vec2 xdPuffFlow;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    xdPuffTransform = vec4(0.0);
    xdPuffCurl = vec4(0.0);
    xdPuffFlow = vec2(0.0);
    if (AuraPerformance > 0.5) {
        // These values are constant across one puff. Flat outputs avoid both
        // repeated fragment trigonometry and interpolation of the packed seed.
        vec4 particleColor = Color * ColorModulator;
        float puffSeed = floor(clamp(particleColor.r, 0.0, 1.0) * 255.0 + 0.5) / 255.0;
        float seed = FilamentSeed + puffSeed * 97.0;
        float life = clamp(particleColor.g, 0.0, 1.0);
        float phase = FilamentPhase + life * 2.20;
        float angle = puffSeed * 6.283185 + sin(phase + seed) * 0.05;
        float strain = 1.0 + sin(phase * 0.70 + seed * 0.23) * 0.22;
        xdPuffTransform = vec4(cos(angle), sin(angle), mix(1.55, 2.15, fract(seed * 0.37)), strain);
        xdPuffCurl = vec4(vec2(sin(seed * 0.73), cos(seed * 0.51)) * 0.45,
                (life - 0.5) * 0.65 + sin(phase * 0.65 + seed) * 0.22,
                sin(AuraTime * 1200.0 * 0.010 + seed) * 0.10);
        xdPuffFlow = vec2(sin(seed + phase * 0.5) * 0.20, -phase * 0.12);
    }
}
