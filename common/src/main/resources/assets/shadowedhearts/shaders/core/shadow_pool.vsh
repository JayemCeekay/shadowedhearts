#version 150
in vec3 Position; in vec4 Color; in vec2 UV0;
uniform mat4 uView, uProj;
uniform float uTime;
// Ripple controls
uniform float uRippleAmp;     // ~0.012
uniform float uRippleFreq;    // ~3.0
uniform float uRippleSpeed;   // ~0.35
uniform float uRippleEdge;    // ~0.82
uniform float uRippleSteep;   // ~0.35
uniform float uRippleUvAmp;   // ~0.015

out vec2 vUV, vUvRipple;
out float vRipple;
out vec4 vColor;
out vec3 vWorldPos;   // for world-space noise

const float TAU = 6.28318530718;

void main(){
    vUV = UV0; vColor = Color;

    vec2 p   = (UV0 - 0.5) * 2.0;
    float r  = length(p);
    vec2 dir = normalize(p + 1e-6);

    float k   = TAU * uRippleFreq;
    float w   = TAU * uRippleSpeed;
    float phi = k * r - w * uTime;

    float inner = 1.0 - smoothstep(uRippleEdge, 1.0, r);
    float A = uRippleAmp * inner;
    float c = cos(phi), s = sin(phi);

    vec3 pos = Position;
    pos.y  += A * s;                       // vertical ripple
    pos.xz += dir * (uRippleSteep * A * c);// gentle Gerstner

    vUvRipple = UV0 + dir * (uRippleUvAmp * c * inner);
    vRipple   = s * inner;

    vec4 world = uView * vec4(pos,1.0);
    vWorldPos  = world.xyz;
    gl_Position= uProj * world;
}
