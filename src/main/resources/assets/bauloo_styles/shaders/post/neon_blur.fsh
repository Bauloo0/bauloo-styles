#version 330

// Player Outline "Neon Glow": a soft gaussian blur of the player silhouette (colour and coverage),
// one direction per pass. Radius comes from the post effect (the thickness setting).

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 step = BlurDir / InSize;
    float radius = max(1.0, Radius);
    float sigma = radius / 2.0;
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (float i = -radius; i <= radius; i += 1.0) {
        float w = exp(-(i * i) / (2.0 * sigma * sigma));
        vec4 s = texture(InSampler, texCoord + step * i);
        sum += vec4(s.rgb * s.a, s.a) * w;
        total += w;
    }
    sum /= total;
    // keep the colour un-premultiplied, so the glow keeps its colour where it gets thin
    fragColor = vec4(sum.a > 0.0001 ? sum.rgb / sum.a : vec3(0.0), sum.a);
}
