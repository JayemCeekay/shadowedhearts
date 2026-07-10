#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float AuraExpand;
uniform float AuraScreenExpand;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 normal = length(Normal) > 0.0001 ? normalize(Normal) : vec3(0.0, 1.0, 0.0);
    vec3 expandedPosition = Position + normal * AuraExpand;

    vec4 viewPos = ModelViewMat * vec4(expandedPosition, 1.0);
    vec3 viewNormal = normalize(mat3(ModelViewMat) * normal);
    vec4 clipPos = ProjMat * viewPos;

    vec2 screenDir = viewNormal.xy;
    float screenLen = length(screenDir);
    if (screenLen > 0.0001) {
        clipPos.xy += (screenDir / screenLen) * AuraScreenExpand * clipPos.w;
    }

    gl_Position = clipPos;
    vertexDistance = length(viewPos.xyz);
    vertexColor = Color;
    texCoord0 = UV0;
}
