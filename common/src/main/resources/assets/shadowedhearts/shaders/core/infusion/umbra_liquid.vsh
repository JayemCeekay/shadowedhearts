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
out vec3 localPosition;
out vec3 localRay;
out vec3 viewNormal;
out vec3 viewPosition;
out vec3 lightColor;
flat out vec3 boxSize;
flat out vec3 localEye;
flat out float tankSeed;

vec3 safe_normalize(vec3 value, vec3 fallback) {
    float magnitude2 = dot(value, value);
    return magnitude2 > 0.00000001 ? value * inversesqrt(magnitude2) : fallback;
}

vec3 undo_tank_rotation(vec3 value, int quarterTurns) {
    // Use exact quarter turns: the Java pose uses X'=cX+sZ, Z'=-sX+cZ.
    if (quarterTurns == 1) return vec3(-value.z, value.y, value.x);
    if (quarterTurns == 2) return vec3(-value.x, value.y, -value.z);
    if (quarterTurns == 3) return vec3(value.z, value.y, -value.x);
    return value;
}

void main() {
    // Color is box coordinates, never a tint. UV0/UV1 carry immutable per-tank
    // parameters so several tanks can share the same deferred buffer safely.
    float width = max(float(UV1.y) / 4096.0, 0.0001);
    boxSize = vec3(width, max(UV0.x, 0.0001), width);
    localPosition = clamp(Color.rgb, 0.0, 1.0) * boxSize;
    tankSeed = clamp(UV0.y, 0.0, 1.0);

    vec4 eyePosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePosition;
    vertexDistance = fog_distance(Position, FogShape);
    viewPosition = eyePosition.xyz;
    viewNormal = safe_normalize(mat3(ModelViewMat) * Normal, vec3(0.0, 1.0, 0.0));

    // Preserve the captured world model-view, including Minecraft's view bob.
    // Position and eye use the same camera-relative space, avoiding large
    // absolute block coordinates in the fragment ray calculation.
    vec3 eye = (inverse(ModelViewMat) * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    localRay = undo_tank_rotation(Position - eye, UV1.x & 3);
    localEye = localPosition - localRay;
    lightColor = texelFetch(Sampler2, clamp(UV2 / 16, ivec2(0), textureSize(Sampler2, 0) - 1), 0).rgb;
}
