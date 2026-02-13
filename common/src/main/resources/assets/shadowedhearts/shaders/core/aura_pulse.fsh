#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D uDepth;
uniform mat4 uInvView;
uniform mat4 uInvProj;
uniform sampler2D uPulseTexture;
uniform float uPulseCount;

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
        vec4 data1 = texelFetch(uPulseTexture, ivec2(i * 2, 0), 0);
        vec4 data2 = texelFetch(uPulseTexture, ivec2(i * 2 + 1, 0), 0);

        vec3 origin = data1.xyz;
        float radius = data1.w;
        vec3 color = data2.rgb;
        float max_radius = data2.a;
        
        float dist = distance(worldPos.xyz, origin);
        float pulse = smoothstep(radius - uThickness, radius, dist) * (1.0 - smoothstep(radius, radius + uThickness, dist));
        
        // Fade out based on distance to prevent infinite pulse
        float fade = 1.0 - smoothstep(0.0, max_radius, dist);
        // Also fade out based on time/radius
        float lifeFade = 1.0 - smoothstep(0.0, max_radius*0.90, radius);

        float alpha = pulse * fade * lifeFade;
        finalPulseColor += color * alpha;
        finalPulseAlpha = max(finalPulseAlpha, alpha);
    }

    fragColor = vec4(original.rgb + finalPulseColor, original.a);
}
