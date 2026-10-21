#version 300 es
// One sticker quad: centered at uCenter (normalized image space), uSize wide (fraction of width),
// rotated by uRotation (radians, clockwise on screen). Gentle bob that is periodic in uPhase.
layout(location = 0) in vec2 aPos;

uniform vec2 uCenter;
uniform float uSize;
uniform float uRotation;
uniform float uAspect;   // width / height
uniform float uPhase;
uniform int uKind;
uniform float uFlipY;

out vec2 vLocal;

const float TAU = 6.28318530718;

void main() {
    // Offscreen, clip y = -1 is the image top row, so aPos already has +y pointing down the image.
    vLocal = aPos;
    float c = cos(uRotation);
    float s = sin(uRotation);
    vec2 r = vec2(c * vLocal.x - s * vLocal.y, s * vLocal.x + c * vLocal.y);
    vec2 center = uCenter;
    // Butterflies bob up and down twice per cycle.
    if (uKind == 1) center.y += 0.06 * uSize * uAspect * sin(TAU * 2.0 * uPhase);
    // Half sizes: the quad spans [-1, 1]. y scaled by aspect so the sticker stays square on screen.
    vec2 p = center + r * 0.5 * uSize * vec2(1.0, uAspect);
    vec2 clip = p * 2.0 - 1.0;
    gl_Position = vec4(clip.x, mix(clip.y, -clip.y, uFlipY), 0.0, 1.0);
}
