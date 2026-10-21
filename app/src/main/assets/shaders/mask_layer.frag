#version 300 es
precision mediump float;

// Copies a model mask (R8) into a mask target, optionally inverted.
in vec2 vUv;
uniform sampler2D uMask;
uniform float uInvert;
out vec4 fragColor;

void main() {
    float v = texture(uMask, vUv).r;
    fragColor = vec4(mix(v, 1.0 - v, uInvert));
}
