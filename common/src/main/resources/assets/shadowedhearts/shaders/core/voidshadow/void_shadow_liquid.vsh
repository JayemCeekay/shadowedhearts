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
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewNormal;
out vec3 viewPosition;
out vec4 overlayColor;
out vec3 lightColor;

vec3 safe_normalize(vec3 value, vec3 fallback) {
    float magnitude2 = dot(value, value);
    return magnitude2 > 0.00000001 ? value * inversesqrt(magnitude2) : fallback;
}

void main() {
    vec3 n = safe_normalize(Normal, vec3(0.0, 1.0, 0.0));
    // Cube faces duplicate shared corners with different normals and UVs.
    // Preserve the posed geometry so liquid flow cannot open their seams;
    // the fragment shader animates the surface normals and wet highlights.
    vec4 eyePosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePosition;
    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    viewNormal = safe_normalize(mat3(ModelViewMat) * n, vec3(0.0, 1.0, 0.0));
    viewPosition = eyePosition.xyz;
    // Retain vanilla packed coordinates while avoiding undefined texel reads
    // from unusual addon layers or fullbright values at a texture boundary.
    overlayColor = texelFetch(Sampler1, clamp(UV1, ivec2(0), textureSize(Sampler1, 0) - 1), 0);
    lightColor = texelFetch(Sampler2, clamp(UV2 / 16, ivec2(0), textureSize(Sampler2, 0) - 1), 0).rgb;
}
