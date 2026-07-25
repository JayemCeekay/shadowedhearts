#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform float MaskExpand;
uniform float MaskScreenExpand;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    vec3 normal = length(Normal) > 0.0001 ? normalize(Normal) : vec3(0.0, 1.0, 0.0);
    vec3 expandedPosition = Position + normal * MaskExpand;

    vec4 viewPos = ModelViewMat * vec4(expandedPosition, 1.0);
    vec3 viewNormal = normalize(mat3(ModelViewMat) * normal);
    vec4 clipPos = ProjMat * viewPos;

    vec2 screenDir = viewNormal.xy;
    float screenLen = length(screenDir);
    if (screenLen > 0.0001) {
        clipPos.xy += (screenDir / screenLen) * MaskScreenExpand * clipPos.w;
    }

    gl_Position = clipPos;
    vertexDistance = fog_distance(expandedPosition, FogShape);
    vertexColor = minecraft_mix_light(
            Light0_Direction, Light1_Direction, normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
