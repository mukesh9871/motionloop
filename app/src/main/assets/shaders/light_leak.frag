#version 300 es
precision highp float;

// Procedural light leak: three soft colored glows drifting on closed paths.
// Periodic in uPhase (integer frequencies), so it loops seamlessly.

in vec2 vUv;

uniform int uStyle;        // 1 warm, 2 rose, 3 aurora
uniform float uPhase;
uniform float uIntensity;  // 0..1
uniform float uAspect;

out vec4 fragColor;

const float TAU = 6.28318530718;

vec3 glow(vec2 p, vec2 center, float radius, vec3 color) {
    vec2 d = (p - center) * vec2(uAspect, 1.0);
    return color * exp(-dot(d, d) / (radius * radius));
}

void main() {
    float t = uPhase;
    vec3 c1;
    vec3 c2;
    vec3 c3;
    if (uStyle == 1) {
        c1 = vec3(1.0, 0.45, 0.1); c2 = vec3(1.0, 0.75, 0.3); c3 = vec3(0.9, 0.2, 0.1);
    } else if (uStyle == 2) {
        c1 = vec3(1.0, 0.3, 0.55); c2 = vec3(1.0, 0.6, 0.7); c3 = vec3(0.7, 0.25, 0.9);
    } else {
        c1 = vec3(0.2, 0.9, 0.6); c2 = vec3(0.3, 0.5, 1.0); c3 = vec3(0.7, 0.3, 1.0);
    }
    // Glows hug the edges, the way real leaks enter from the side of the frame.
    vec2 a = vec2(-0.05, 0.15) + 0.12 * vec2(cos(TAU * t), sin(TAU * t));
    vec2 b = vec2(1.05, 0.35) + 0.15 * vec2(sin(TAU * t + 1.3), cos(TAU * 2.0 * t));
    vec2 c = vec2(0.3, 1.05) + 0.2 * vec2(sin(TAU * t + 2.1), 0.3 * cos(TAU * t));
    vec3 col = glow(vUv, a, 0.45, c1) + glow(vUv, b, 0.38, c2) + 0.8 * glow(vUv, c, 0.5, c3);
    col *= uIntensity;
    float alpha = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    // Premultiplied: color already scaled by its own coverage.
    fragColor = vec4(min(col, vec3(1.0)), alpha);
}
