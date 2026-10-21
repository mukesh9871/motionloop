#version 300 es
// A rotated text quad. Sizes are in image widths, so rotation stays undistorted on any aspect.
layout(location = 0) in vec2 aPos;  // fullscreen quad corners in [-1, 1]
layout(location = 1) in vec2 aUv;   // v = 0 at the top row of the text bitmap

uniform vec2 uCenter;     // normalized image position (0,0 top-left)
uniform vec2 uHalfSize;   // half width and half height, in image widths
uniform float uRotation;  // radians, clockwise on screen
uniform float uAspect;    // image width / height
uniform float uFlipY;

out vec2 vUv;

void main() {
    vUv = aUv;
    // Quad y = -1 is the bitmap's top row, and normalized image y grows downward: same direction.
    vec2 local = aPos * uHalfSize;
    float c = cos(uRotation);
    float s = sin(uRotation);
    vec2 r = vec2(c * local.x - s * local.y, s * local.x + c * local.y);
    // Width units to normalized: x as is, y times aspect (height = width / aspect).
    vec2 p = uCenter + vec2(r.x, r.y * uAspect);
    vec2 clip = p * 2.0 - 1.0;
    gl_Position = vec4(clip.x, mix(clip.y, -clip.y, uFlipY), 0.0, 1.0);
}
