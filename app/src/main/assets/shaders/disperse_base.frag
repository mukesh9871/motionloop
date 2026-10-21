#version 300 es
precision highp float;

// Dispersion, base layer: cells of the painted area go missing while their flake is away.
// Missing cells show a soft blur of the photo. Must match disperse.vert (same grid, hash, timing).

in vec2 vUv;

uniform sampler2D uFrame;
uniform sampler2D uMask;
uniform sampler2D uHole;     // blurred photo
uniform vec2 uGrid;          // cells across, down
uniform float uPhase;
uniform float uAmount;

out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec3 c = texture(uFrame, vUv).rgb;
    vec2 cell = floor(vUv * uGrid);
    float gone = 0.0;
    if (hash(cell) < uAmount && texture(uMask, (cell + 0.5) / uGrid).r >= 0.5) {
        float life = fract(uPhase + hash(cell + 17.3));
        gone = smoothstep(0.0, 0.06, life) * (1.0 - smoothstep(0.85, 1.0, life));
    }
    fragColor = vec4(mix(c, texture(uHole, vUv).rgb, gone), 1.0);
}
