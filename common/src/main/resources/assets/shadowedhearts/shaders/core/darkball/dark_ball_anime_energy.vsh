#version 150

in vec3 Position;
in vec2 UV0;
out vec2 energyUv;

void main() {
    energyUv = UV0;
    gl_Position = vec4(Position, 1.0);
}
