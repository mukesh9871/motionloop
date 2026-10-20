#version 300 es
precision mediump float;

in vec2 vLocal;
uniform float uHardness;  // 0 = very soft edge, 0.95 = almost hard
out vec4 fragColor;

void main() {
    float a = 1.0 - smoothstep(uHardness, 1.0, length(vLocal));
    fragColor = vec4(a);
}
