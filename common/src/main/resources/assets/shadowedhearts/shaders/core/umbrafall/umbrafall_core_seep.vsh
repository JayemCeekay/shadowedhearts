#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform sampler2D Sampler2;

out float vertexDistance;
out vec2 materialUv;
out vec4 vertexColor;
out vec3 lightColor;
flat out int materialKind;
flat out float materialSeed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(Position, FogShape);
    materialUv = UV0;
    vertexColor = Color;
    // A private entity-compatible layout carries immutable per-quad data.
    // UV1 is metadata, not the entity hurt/flash overlay texture coordinate.
    materialKind = UV1.x;
    materialSeed = float(UV1.y) / 256.0;
    lightColor = texelFetch(Sampler2,
            clamp(UV2 / 16, ivec2(0), textureSize(Sampler2, 0) - 1), 0).rgb;
}
