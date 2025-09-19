#version 150

in vec3 Position;

uniform mat4 uView;
uniform mat4 uProj;

out vec3 vPosWS;

void main() {
    // Position is provided in world space
    vPosWS = Position;
    gl_Position = uProj * (uView * vec4(Position, 1.0));
}
