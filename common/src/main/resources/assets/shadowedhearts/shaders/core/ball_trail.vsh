#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2; // lightmap (not used, but kept for vertex format compatibility)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;        // vanilla-provided (ticks-ish)
uniform vec4 ColorModulator;   // vanilla-provided

out vec4 vColor;
out vec2 vUv;

void main() {
    vColor = Color * ColorModulator;
    vUv = UV0;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
