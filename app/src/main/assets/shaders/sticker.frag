#version 300 es
precision highp float;

// Procedural animated stickers. Every motion term uses whole frequencies of uPhase, so the
// sticker loops exactly with the photo. Output is premultiplied.

in vec2 vLocal;            // [-1, 1], +y down

uniform int uKind;          // 1 butterfly, 2 flame, 3 birds, 4 glint
uniform float uPhase;

out vec4 fragColor;

const float TAU = 6.28318530718;

float ellipse(vec2 p, vec2 c, vec2 r) {
    vec2 d = (p - c) / r;
    return 1.0 - smoothstep(0.85, 1.0, length(d));
}

vec4 over(vec4 top, vec4 base) { return top + base * (1.0 - top.a); }

vec4 butterfly(vec2 p) {
    float flap = 0.2 + 0.8 * abs(cos(TAU * 3.0 * uPhase));
    vec2 q = vec2(abs(p.x) / max(flap, 0.05), p.y);
    float upper = ellipse(q, vec2(0.45, -0.22), vec2(0.45, 0.42));
    float lower = ellipse(q, vec2(0.32, 0.32), vec2(0.3, 0.32));
    float wing = max(upper, lower);
    float spot = ellipse(q, vec2(0.55, -0.3), vec2(0.12, 0.1));
    float edge = wing;
    vec3 col = mix(vec3(1.0, 0.55, 0.1), vec3(0.95, 0.3, 0.05), smoothstep(-0.6, 0.6, p.y));
    col = mix(col, vec3(0.05), smoothstep(0.75, 0.95, length((q - vec2(0.42, 0.0)) / vec2(0.6, 0.7))));
    col = mix(col, vec3(1.0), spot);
    vec4 wings = vec4(col * edge, edge);
    float body = ellipse(p, vec2(0.0, 0.05), vec2(0.06, 0.45));
    return over(vec4(vec3(0.08) * body, body), wings);
}

vec4 flame(vec2 p) {
    // Teardrop that flickers in height and sways at the tip.
    float t = TAU * uPhase;
    float h = 0.85 + 0.08 * sin(3.0 * t) + 0.05 * sin(7.0 * t + 1.3);
    float sway = 0.08 * sin(5.0 * t) * smoothstep(0.6, -0.9, p.y);
    vec2 q = vec2(p.x - sway, p.y);
    float y = (q.y - 0.6) / (1.4 * h);            // 0 at the base, about -1 at the tip
    float width = 0.42 * sqrt(max(0.0, -y)) * (1.0 + y * 0.6);
    float d = abs(q.x) / max(width, 1e-3);
    float inside = (1.0 - smoothstep(0.8, 1.0, d)) * step(y, 0.0) * step(-1.0, y);
    float core = (1.0 - smoothstep(0.2, 0.6, d)) * smoothstep(-0.95, -0.3, y);
    vec3 col = mix(vec3(1.0, 0.35, 0.05), vec3(1.0, 0.8, 0.25), core);
    col = mix(col, vec3(1.0, 0.97, 0.85), core * smoothstep(-0.5, -0.1, y));
    float glow = 0.25 * exp(-dot(q - vec2(0.0, 0.1), q - vec2(0.0, 0.1)) * 3.0);
    float a = clamp(inside + glow, 0.0, 1.0);
    return vec4(col * a, a);
}

vec4 birds(vec2 p) {
    vec4 acc = vec4(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        // Each bird crosses the sticker once per cycle, a third of a cycle apart.
        float x = fract(uPhase + fi / 3.0) * 2.4 - 1.2;
        vec2 c = vec2(x, -0.3 + 0.3 * fi + 0.06 * sin(TAU * (2.0 * uPhase + fi)));
        vec2 q = (p - c) / 0.28;
        float flap = 0.45 * sin(TAU * (6.0 * uPhase + fi * 0.3));
        // Two wing strokes meeting at the body: a soft V.
        float wing = abs(q.y + abs(q.x) * flap - abs(q.x) * 0.2);
        float a = (1.0 - smoothstep(0.05, 0.12, wing)) * (1.0 - smoothstep(0.7, 0.9, abs(q.x)));
        acc = over(vec4(vec3(0.07) * a, a), acc);
    }
    return acc;
}

vec4 glint(vec2 p) {
    float t = TAU * uPhase;
    float pulse = 0.6 + 0.4 * sin(2.0 * t);
    float ang = t;  // one turn per cycle
    vec2 q = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * p;
    float r = length(p);
    float rays = exp(-abs(q.x) * 30.0) + exp(-abs(q.y) * 30.0);
    vec2 q2 = mat2(0.7071, -0.7071, 0.7071, 0.7071) * q;
    rays += 0.5 * (exp(-abs(q2.x) * 40.0) + exp(-abs(q2.y) * 40.0));
    float a = clamp((exp(-r * r * 30.0) + rays * (1.0 - smoothstep(0.2, 1.0, r))) * pulse, 0.0, 1.0);
    vec3 col = mix(vec3(1.0, 0.95, 0.8), vec3(0.8, 0.9, 1.0), 0.5 + 0.5 * sin(t));
    return vec4(col * a, a);
}

void main() {
    vec4 c;
    if (uKind == 1) c = butterfly(vLocal);
    else if (uKind == 2) c = flame(vLocal);
    else if (uKind == 3) c = birds(vLocal);
    else c = glint(vLocal);
    fragColor = c;
}
