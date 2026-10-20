#version 300 es
precision mediump float;

in vec2 vLocal;
in float vAlpha;
in vec3 vColor;
flat in int vType;

out vec4 fragColor;

void main() {
    float d = length(vLocal);
    float a;
    if (vType == 1) {
        // Four-point star: bright core plus thin rays along the axes.
        float core = exp(-d * d * 18.0);
        float rays = exp(-abs(vLocal.x) * 22.0) * (1.0 - abs(vLocal.y))
                   + exp(-abs(vLocal.y) * 22.0) * (1.0 - abs(vLocal.x));
        a = clamp(core + 0.7 * rays, 0.0, 1.0);
    } else if (vType == 4) {
        // Bokeh disc with a slightly brighter rim.
        a = (1.0 - smoothstep(0.85, 1.0, d)) * (0.75 + 0.25 * smoothstep(0.5, 0.95, d));
    } else if (vType == 3) {
        a = (1.0 - smoothstep(0.3, 1.0, abs(vLocal.x))) * (1.0 - smoothstep(0.6, 1.0, abs(vLocal.y)));
    } else {
        a = 1.0 - smoothstep(0.2, 1.0, d);
    }
    a *= vAlpha;
    // Premultiplied output: the layer is composited with premultiplied math.
    fragColor = vec4(vColor * a, a);
}
