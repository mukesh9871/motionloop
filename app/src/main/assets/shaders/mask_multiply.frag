#version 300 es
precision mediump float;

// Multiplies the bound target by a mask: drawn with glBlendFunc(GL_ZERO, GL_SRC_ALPHA).
in vec2 vUv;
uniform sampler2D uMask;
out vec4 fragColor;

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, texture(uMask, vUv).r);
}
