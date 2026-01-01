// shadow_aura_xd_cylinder.vsh (identical to sphere variant)
#version 150

// === Attributes (icosphere/cylinder proxy only needs Position) ===
in vec3 Position;

// === Uniforms you set from Java ===
uniform mat4 uModel;        // object -> world
uniform mat4 uView;         // world -> view
uniform mat4 uProj;         // view  -> clip
uniform vec3 uCameraPosWS;  // camera/world eye position
uniform vec3 uEntityPosWS;  // entity world position

// Optional: expand the proxy 1–2% to close 1px gaps at the silhouette
uniform float uExpand;      // default 1.0, try 1.01–1.02 if needed

// === Varyings to fragment ===
out vec3 vPosWS;            // world-space position at this fragment on the proxy
out vec3 vRayDirWS;         // camera->fragment direction in world space

void main() {
    // Slight uniform expansion in OBJECT space (keeps proxy SDF consistent)
    vec3 posOS = Position * uExpand;

    // Object -> World
    vec4 posWS4 = uModel * vec4(posOS, 1.0);
    vPosWS      = posWS4.xyz;

    // Per-fragment ray direction (will be perspective-correct after interpolation)
    vRayDirWS   = vPosWS - uCameraPosWS;

    // World -> View -> Clip
    gl_Position = uProj * (uView * posWS4);
}
