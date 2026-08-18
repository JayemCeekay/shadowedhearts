#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 VolumeRootCameraRelative;
uniform vec3 VolumeAxis;
uniform vec3 VolumeSide;
uniform vec3 VolumeUp;
uniform float EffectFade;

out float siphonWindow;
out float siphonCurveT;
out float siphonLocalRadius;
out vec3 cameraRelativePosition;

void main() {
    vec3 cameraRelative = VolumeRootCameraRelative
            + VolumeAxis * Position.x
            + VolumeSide * Position.y
            + VolumeUp * Position.z;
    vec4 viewPosition = ModelViewMat * vec4(cameraRelative, 1.0);

    gl_Position = ProjMat * viewPosition;
    siphonWindow = clamp(Color.r, 0.0, 1.0)
            * clamp(EffectFade, 0.0, 1.0);
    siphonCurveT = UV0.x;
    siphonLocalRadius = max(UV0.y, 0.0);
    cameraRelativePosition = cameraRelative;
}
