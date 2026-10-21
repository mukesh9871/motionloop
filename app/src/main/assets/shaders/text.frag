#version 300 es
// highp: these sample full frames at frame resolution, and mediump uv is coarser than a texel there.
precision highp float;

// Premultiplied text with optional glow under it and a typewriter reveal.
in vec2 vUv;

uniform sampler2D uText;
uniform sampler2D uGlow;    // unused when uGlowAlpha is 0
uniform sampler2D uOrder;   // red = (char index + 1) / count, 0 where no character covers
uniform float uAlpha;
uniform float uGlowAlpha;
uniform float uReveal;      // 0..1 share of characters shown
uniform int uUseOrder;
uniform int uAlphaOnly;     // 1: write coverage only

out vec4 fragColor;

void main() {
    vec4 t = texture(uText, vUv) * uAlpha;
    vec4 g = texture(uGlow, vUv) * uGlowAlpha * uAlpha;
    if (uUseOrder == 1) {
        float order = texture(uOrder, vUv).r;
        // Pixels no character claims (far shadow or outline edges) appear only at full reveal.
        if (order < 0.5 / 255.0) order = 1.0;
        // +0.5/255 absorbs 8-bit rounding of the levels.
        float shown = step(order, uReveal + 0.5 / 255.0);
        t *= shown;
        g *= shown;
    }
    vec4 c = t + g * (1.0 - t.a);
    fragColor = uAlphaOnly == 1 ? vec4(c.a) : c;
}
