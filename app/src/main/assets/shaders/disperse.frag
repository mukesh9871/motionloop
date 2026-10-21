#version 300 es
precision mediump float;

in vec2 vLocal;
in vec3 vColor;
in float vAlpha;

out vec4 fragColor;

void main() {
    // Slightly rounded square flake, premultiplied.
    vec2 d = abs(vLocal);
    float a = (1.0 - smoothstep(0.75, 1.0, max(d.x, d.y))) * vAlpha;
    fragColor = vec4(vColor * a, a);
}
