#version 430

uniform sampler2D DiffuseSampler;
uniform sampler2D uDepth;
uniform mat4 uInvView;
uniform mat4 uInvProj;
uniform float uPulseCount;

struct Pulse {
    vec4 origin; // x, y, z, radius
    vec4 color;  // r, g, b, distance
};

layout(std430, binding = 0) buffer PulseData {
    Pulse pulses[];
};

uniform float uThickness;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    float depth = texture(uDepth, texCoord).r;

    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = uInvProj * clipPos;
    viewPos /= viewPos.w;
    vec4 worldPos = uInvView * viewPos;

    vec3 finalPulseColor = vec3(0.0);
    float finalPulseAlpha = 0.0;

    for (int i = 0; i < int(uPulseCount); i++) {
        vec3 origin = pulses[i].origin.xyz;
        float radius = pulses[i].origin.w;
        vec3 color = pulses[i].color.rgb;
        float max_radius = pulses[i].color.a;
        
        float dist = distance(worldPos.xyz, origin);
        float pulse = smoothstep(radius - uThickness, radius, dist) * (1.0 - smoothstep(radius, radius + uThickness, dist));
        
        // Fade out based on distance to prevent infinite pulse
        float fade = 1.0 - smoothstep(0.0, max_radius, dist);
        // Also fade out based on time/radius
        float lifeFade = 1.0 - smoothstep(0.0, max_radius, radius);

        float alpha = pulse * fade * lifeFade;
        finalPulseColor += color * alpha;
        finalPulseAlpha = max(finalPulseAlpha, alpha);
    }

    fragColor = vec4(original.rgb + finalPulseColor, original.a);
}
