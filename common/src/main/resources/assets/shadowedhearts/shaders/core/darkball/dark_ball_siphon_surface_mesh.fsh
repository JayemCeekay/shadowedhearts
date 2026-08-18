#version 150

in float siphonWindow;
in float siphonCurveT;
in float siphonLocalRadius;
in vec3 cameraRelativePosition;

out vec4 fragColor;

const float SIPHON_COVERAGE_DEPTH_CUTOFF = 0.08;

void main() {
    if (siphonWindow < SIPHON_COVERAGE_DEPTH_CUTOFF
            || siphonCurveT < -0.001
            || siphonCurveT > 1.001) {
        discard;
    }

    // Preserve the established material-buffer contract:
    // R = body, G = siphon, B > 2 = siphon radius payload,
    // A = positive camera-to-surface distance.
    float surfaceDistance =
            max(length(cameraRelativePosition), 0.0001);
    fragColor = vec4(
            0.0,
            siphonWindow,
            2.0 + siphonLocalRadius,
            surfaceDistance);
}
