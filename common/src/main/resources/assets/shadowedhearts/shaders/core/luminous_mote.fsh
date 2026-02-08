#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    
    // Additive blending support: multiply RGB by Alpha to handle fades correctly
    // Since we use ("srcrgb": "srcalpha", "dstrgb": "1") in the JSON, 
    // the output RGB will be added to the background.
    color.rgb *= color.a;
    
    // Boost the emission/bloom effect
    color.rgb *= 1.5;
    
    fragColor = color;
}