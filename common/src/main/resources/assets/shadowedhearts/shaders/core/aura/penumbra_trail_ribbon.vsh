#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2; // lightmap (not used, but kept for vertex format compatibility)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform vec4 ColorModulator;

out vec4 vColor;
out vec2 vUv;
out float vTime;

void main() {
    vColor = Color * ColorModulator;
    vUv = UV0;
    vTime = GameTime * 1200.0; // convert to seconds-ish

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
