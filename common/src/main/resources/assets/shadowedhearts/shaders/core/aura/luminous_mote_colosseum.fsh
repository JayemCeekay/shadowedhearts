#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Draw after smoke, but retain the scene occlusion captured with its camera.
    // The final Iris framebuffer need not retain the original world depth.
    vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    float sceneDepth = texture(Sampler1, screenUv).r;
    if (gl_FragCoord.z > sceneDepth + 0.00001) discard;

    vec4 sprite = texture(Sampler0, texCoord0);
    vec4 color = sprite * vertexColor * ColorModulator;
    if (color.a < 0.0001) discard;
    // Brighten the small center more than the halo, retaining the sprite's
    // falloff as the interpolated alpha and quad size smoothly approach zero.
    float glow = mix(1.9, 2.8, smoothstep(0.08, 0.65, sprite.a));
    // SRC_ALPHA / ONE applies the fade once, after the smoke's extinction.
    fragColor = vec4(color.rgb * glow, color.a);
}
