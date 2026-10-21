#version 300 es
precision highp float;

// Depth parallax: each pixel shifts by (depth - focus) times the camera offset, so near and far
// layers slide past each other. The camera path is periodic in uPhase (one turn per cycle).

in vec2 vUv;

uniform sampler2D uFrame;
uniform sampler2D uDepth;   // R8, 1 = near
uniform int uPath;          // CameraPath ordinal: circle, sway, zoom
uniform float uPhase;
uniform float uOffset;      // camera offset in image widths (amount * MAX_OFFSET)
uniform float uFocus;       // depth that does not move
uniform float uAspect;      // width / height

out vec4 fragColor;

const float TAU = 6.28318530718;

vec2 shiftFor(vec2 uv, float depth) {
    float k = depth - uFocus;
    float a = TAU * uPhase;
    if (uPath == 0) {
        // Offsets in widths, so scale y by aspect to keep the circle round on screen.
        return uOffset * k * vec2(cos(a), sin(a) * uAspect);
    } else if (uPath == 1) {
        return uOffset * k * vec2(sin(a), 0.0);
    }
    // Zoom: push near content outward from the center and back, once per cycle.
    float z = 0.5 - 0.5 * cos(a);
    // At the frame edge |uv - 0.5| = 0.5, so the largest shift is uOffset, like the other paths.
    return (uv - 0.5) * (uOffset * 2.0) * k * z;
}

void main() {
    // Zoom in a little so shifted pixels never sample past the frame edge. |k| <= 1, so the
    // largest shift is uOffset in x and uOffset * aspect in y (circle path). Zooming by
    // (1 + 2 * margin) keeps margin / (1 + 2 * margin) of the frame in reserve at each edge:
    // solve that for a reserve of 1.1x the largest shift.
    float reserve = 1.1 * uOffset * max(1.0, uAspect);
    float margin = reserve / (1.0 - 2.0 * reserve);
    vec2 uv = (vUv - 0.5) / (1.0 + 2.0 * margin) + 0.5;

    // Backward mapping needs the depth at the source point, which depends on the shift.
    // A few fixed-point steps converge well for smooth depth maps.
    vec2 src = uv;
    for (int i = 0; i < 3; i++) {
        float d = texture(uDepth, src).r;
        src = uv - shiftFor(uv, d);
    }
    fragColor = vec4(texture(uFrame, clamp(src, 0.0, 1.0)).rgb, 1.0);
}
