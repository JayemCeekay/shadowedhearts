#version 150

uniform sampler2D Sampler0;    // blurred bloom texture
uniform float BloomIntensity;  // overall bloom strength (default 1.0)

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 bloom = texture(Sampler0, texCoord0);

    // Discard near-black fragments to avoid full-screen overdraw
    float brightness = dot(bloom.rgb, vec3(0.299, 0.587, 0.114));
    if (brightness < 0.005) discard;

    // Apply intensity multiplier
    bloom.rgb *= BloomIntensity;

    // Output with premultiplied alpha for additive blending (ONE, ONE)
    fragColor = vec4(bloom.rgb, 1.0);
}
