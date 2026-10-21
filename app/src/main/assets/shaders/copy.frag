#version 300 es
// highp: these sample full frames at frame resolution, and mediump uv is coarser than a texel there.
precision highp float;

in vec2 vUv;
uniform sampler2D uSrc;
out vec4 fragColor;

void main() {
    fragColor = vec4(texture(uSrc, vUv).rgb, 1.0);
}
