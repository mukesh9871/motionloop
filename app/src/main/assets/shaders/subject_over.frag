#version 300 es
precision mediump float;

// Copies the frame (mode 0), or only the subject from it as premultiplied color (mode 1), which is
// drawn over text so the text appears behind the subject.
in vec2 vUv;

uniform sampler2D uFrame;
uniform sampler2D uSubject;   // refined subject mask, R8
uniform float uFeather;       // 0..1
uniform int uMode;

out vec4 fragColor;

void main() {
    vec3 c = texture(uFrame, vUv).rgb;
    if (uMode == 0) {
        fragColor = vec4(c, 1.0);
        return;
    }
    float f = 0.04 + 0.4 * uFeather;
    float s = smoothstep(0.5 - f, 0.5 + f, texture(uSubject, vUv).r);
    fragColor = vec4(c * s, s);
}
