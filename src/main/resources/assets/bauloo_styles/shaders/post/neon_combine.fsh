#version 330

// Player Outline "Neon Glow": the glow only outside the player (so the skin stays clean),
// fading out, plus a thin bright edge right at the silhouette.

uniform sampler2D EdgeSampler;
uniform sampler2D GlowSampler;
uniform sampler2D SilSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 edge = texture(EdgeSampler, texCoord);
    vec4 glow = texture(GlowSampler, texCoord);
    float inside = texture(SilSampler, texCoord).a;
    float halo = clamp(glow.a * 2.6, 0.0, 1.0) * (1.0 - inside);
    halo = sqrt(halo) * 0.95;
    vec3 base = glow.a > 0.0001 ? glow.rgb : edge.rgb * 5.0;
    vec3 bright = mix(base, vec3(1.0), 0.35);
    float a = max(edge.a, halo);
    fragColor = vec4(mix(base, bright, edge.a), a);
}
