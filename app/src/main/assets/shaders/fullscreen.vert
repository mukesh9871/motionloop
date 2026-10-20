#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;

// 1.0 when drawing to a window or encoder surface, where clip y = +1 is the top row.
// 0.0 when drawing into an FBO, so v = 0 (image top) stays the first row in memory.
uniform float uFlipY;

out vec2 vUv;

void main() {
    vUv = aUv;
    gl_Position = vec4(aPos.x, mix(aPos.y, -aPos.y, uFlipY), 0.0, 1.0);
}
