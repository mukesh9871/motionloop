#version 300 es
precision highp float;

in vec2 vUv;

uniform sampler2D uImage;
uniform sampler2D uFlow;
uniform sampler2D uMask;
uniform float uPhase;        // loop phase in cycles, not wrapped
uniform int uMode;           // 0 = continuous, 1 = boomerang
uniform float uAmount;       // 0 shows the still photo (editing), 1 animates
uniform float uEncoded;      // flow texture is RGBA8 packed
uniform float uMaxFlow;
uniform float uMaskOverlay;  // tint frozen areas while brushing

out vec4 fragColor;

vec2 flowAt(vec2 uv) {
    vec2 f = texture(uFlow, uv).rg;
    if (uEncoded > 0.5) f = (f * 255.0 - 127.0) / 127.0 * uMaxFlow;
    return f * (1.0 - texture(uMask, uv).r) * uAmount;
}

void main() {
    vec2 f = flowAt(vUv);
    vec3 color;

    if (uMode == 0) {
        // Two copies half a cycle apart. Each one is fully faded out at the moment
        // its phase wraps from 1 back to 0, so the jump is never visible.
        float p1 = fract(uPhase);
        float p2 = fract(uPhase + 0.5);
        vec3 c1 = texture(uImage, vUv - f * (p1 - 0.5)).rgb;
        vec3 c2 = texture(uImage, vUv - f * (p2 - 0.5)).rgb;
        // Same formula as LoopTiming.secondCopyWeight (unit tested there).
        color = mix(c1, c2, abs(2.0 * p1 - 1.0));
    } else {
        // Forward then back, eased at both ends.
        float p = 0.5 - 0.5 * cos(6.28318530718 * uPhase);
        color = texture(uImage, vUv - f * (p - 0.5)).rgb;
    }

    float m = texture(uMask, vUv).r;
    color = mix(color, vec3(1.0, 0.42, 0.48), m * 0.45 * uMaskOverlay);
    fragColor = vec4(color, 1.0);
}
