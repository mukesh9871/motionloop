#version 300 es
precision mediump float;

// Premultiplied text with optional glow under it and a typewriter reveal.
in vec2 vUv;

uniform sampler2D uText;
uniform sampler2D uGlow;    // unused when uGlowAlpha is 0
uniform sampler2D uOrder;   // red = (char index + 1) / count, used when uUseOrder is 1
uniform float uAlpha;
uniform float uGlowAlpha;
uniform float uReveal;      // 0..1 share of characters shown
uniform int uUseOrder;

out vec4 fragColor;

void main() {
    vec4 t = texture(uText, vUv) * uAlpha;
    if (uUseOrder == 1) {
        float order = texture(uOrder, vUv).r;
        // Pixels of characters not reached yet are hidden. +0.5/255 absorbs 8-bit rounding.
        t *= step(order, uReveal + 0.5 / 255.0);
    }
    vec4 g = texture(uGlow, vUv) * uGlowAlpha * uAlpha;
    fragColor = t + g * (1.0 - t.a);
}
