#version 300 es
// Dispersion flakes: one instance per grid cell. Cells inside the painted area (and picked by
// uAmount) fly off along uDir and come back once per cycle, so the effect loops seamlessly.
layout(location = 0) in vec2 aPos;   // quad corner in [-1, 1]

uniform sampler2D uFrame;
uniform sampler2D uMask;
uniform vec2 uGrid;
uniform float uPhase;
uniform float uAmount;
uniform float uDistance;   // travel in image widths
uniform vec2 uDir;         // unit direction, +y down
uniform float uAspect;     // width / height
uniform float uFlipY;

out vec2 vLocal;
out vec3 vColor;
out float vAlpha;

const float TAU = 6.28318530718;

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    // Integer math: float division can land just under a whole number and pick the wrong row.
    int cols = int(uGrid.x + 0.5);
    vec2 cell = vec2(ivec2(gl_InstanceID % cols, gl_InstanceID / cols));
    vec2 origin = (cell + 0.5) / uGrid;
    vLocal = aPos;
    vColor = vec3(0.0);
    vAlpha = 0.0;
    if (hash(cell) >= uAmount || textureLod(uMask, origin, 0.0).r < 0.5) {
        // Not a flake: a degenerate triangle outside the clip volume.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    float life = fract(uPhase + hash(cell + 17.3));
    float r = hash(cell + 41.7);
    float travel = pow(life, 1.5) * uDistance * (0.6 + 0.8 * r);
    vec2 perp = vec2(-uDir.y, uDir.x);
    vec2 drift = uDir * travel + perp * 0.02 * sin(TAU * (2.0 * life + r));
    // Distances are in widths: y scaled by aspect.
    vec2 pos = origin + drift * vec2(1.0, uAspect);
    float size = (1.0 / uGrid.x) * (1.0 - 0.6 * life);
    float a = TAU * (life + r);
    vec2 rc = vec2(cos(a) * aPos.x - sin(a) * aPos.y, sin(a) * aPos.x + cos(a) * aPos.y);
    vec2 p = pos + rc * 0.5 * size * vec2(1.0, uAspect);
    vColor = textureLod(uFrame, origin, 0.0).rgb;
    vAlpha = smoothstep(0.0, 0.06, life) * (1.0 - smoothstep(0.7, 1.0, life));
    vec2 clip = p * 2.0 - 1.0;
    gl_Position = vec4(clip.x, mix(clip.y, -clip.y, uFlipY), 0.0, 1.0);
}
