#version 300 es
precision mediump float;

in vec2 vUv;
uniform sampler2D uSrc;
out vec4 fragColor;

void main() {
    fragColor = vec4(texture(uSrc, vUv).rgb, 1.0);
}
