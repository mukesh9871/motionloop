#version 300 es
precision highp float;

// Full-frame atmosphere. Fog: value noise sampled on a circle in noise space, so it drifts and
// comes back exactly once per cycle. Grain: a fresh noise pattern 24 times per cycle, repeating.

in vec2 vUv;

uniform int uStyle;       // 1 fog, 2 grain
uniform float uPhase;
uniform float uAmount;    // 0..1
uniform float uAspect;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

void main() {
    float t = fract(uPhase);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    if (uStyle == 1) {
        vec2 drift = 0.8 * vec2(cos(TAU * t), sin(TAU * t));
        float n = fbm(p * 2.5 + drift);
        // Denser near the bottom, like ground fog.
        float a = smoothstep(0.35, 0.8, n) * mix(0.45, 1.0, vUv.y) * uAmount;
        fragColor = vec4(vec3(0.92) * a, a);
    } else {
        float frame = floor(t * 24.0);
        float g = hash(floor(vUv * vec2(640.0 * uAspect, 640.0)) + frame * 17.0);
        float a = 0.35 * uAmount;
        fragColor = vec4(vec3(g) * a, a);
    }
}
