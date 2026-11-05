#version 150

#moj_import <shadowedhearts:dark_ball_anime_motion.glsl>

in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 VolumeRootCameraRelative;
uniform vec3 VolumeAxis;
uniform vec3 VolumeSide;
uniform vec3 VolumeUp;
out vec3 cameraRelativePosition;
out vec3 sourcePosition;

void main() {
    sourcePosition = Position;
    vec3 localPosition = animeDeform(Position);
    cameraRelativePosition = VolumeRootCameraRelative + VolumeAxis * localPosition.x
            + VolumeSide * localPosition.y + VolumeUp * localPosition.z;
    gl_Position = ProjMat * ModelViewMat * vec4(cameraRelativePosition, 1.0);
}
