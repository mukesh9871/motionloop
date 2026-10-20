#version 300 es
// Instanced particles. Every motion term is periodic in uPhase with an integer
// frequency, so phase and phase + 1 give the same frame (seamless loop).

layout(location = 0) in vec2 aCorner;  // quad corner in [-1, 1]
layout(location = 1) in vec4 aSeed;    // per instance: x0, y0, phase offset, random

uniform int uType;        // 1 sparkle, 2 snow, 3 rain, 4 bokeh, 5 embers
uniform float uPhase;
uniform float uSpeed;     // whole wraps per cycle for the slowest particles
uniform float uSize;      // 0..1
uniform float uAspect;    // width / height
uniform float uFlipY;

out vec2 vLocal;
out float vAlpha;
out vec3 vColor;
flat out int vType;

const float TAU = 6.28318530718;

void main() {
    float x0 = aSeed.x;
    float y0 = aSeed.y;
    float ph = aSeed.z;
    float r = aSeed.w;
    float t = uPhase;
    // Integer speed tiers 1x, 2x, 3x give depth without breaking periodicity.
    float k = uSpeed * (1.0 + floor(r * 3.0));
    float m = 1.0 + floor(fract(r * 7.0) * 2.0);

    vec2 pos;
    float size;
    float alpha = 1.0;
    vec3 color = vec3(1.0);
    vec2 along = vec2(0.0, 1.0);  // quad long axis (rain streaks)
    float stretch = 1.0;

    if (uType == 1) {
        // Sparkle: fixed spots that twinkle.
        pos = vec2(x0, y0);
        size = mix(0.008, 0.03, r);
        float tw = max(0.0, sin(TAU * (m * t + ph)));
        alpha = tw * tw * tw;
        color = mix(vec3(1.0, 0.95, 0.8), vec3(0.8, 0.9, 1.0), fract(r * 13.0));
    } else if (uType == 2) {
        // Snow: falls k screens per cycle and sways.
        float y = fract(y0 + k * t);
        pos = vec2(x0 + 0.025 * sin(TAU * (m * t + ph)), y * 1.2 - 0.1);
        size = mix(0.004, 0.014, r) * (0.6 + 0.4 * k / (uSpeed * 3.0));
        alpha = 0.55 + 0.45 * r;
    } else if (uType == 3) {
        // Rain: fast, slanted streaks.
        float y = fract(y0 + 2.0 * k * t);
        pos = vec2(x0 + 0.12 * y, y * 1.3 - 0.15);
        size = mix(0.0015, 0.003, r);
        along = normalize(vec2(0.12, 1.0));
        stretch = 14.0;
        alpha = 0.35 + 0.35 * r;
        color = vec3(0.85, 0.9, 1.0);
    } else if (uType == 4) {
        // Bokeh: large soft discs drifting in small circles.
        pos = vec2(x0, y0) + 0.04 * vec2(cos(TAU * (t + ph)), sin(TAU * (t + ph)));
        size = mix(0.03, 0.08, r);
        alpha = 0.18 + 0.2 * fract(r * 5.0);
        color = mix(vec3(1.0, 0.8, 0.55), vec3(0.6, 0.75, 1.0), fract(r * 11.0));
    } else {
        // Embers: rise, wiggle and flicker.
        float y = fract(y0 + k * t);
        pos = vec2(x0 + 0.03 * sin(TAU * (2.0 * m * t + ph)), 1.1 - y * 1.2);
        size = mix(0.003, 0.009, r);
        alpha = 0.6 + 0.4 * sin(TAU * (3.0 * m * t + ph));
        color = mix(vec3(1.0, 0.45, 0.1), vec3(1.0, 0.8, 0.3), fract(r * 17.0));
    }

    size *= mix(0.5, 1.8, uSize);
    vec2 across = vec2(along.y, -along.x);
    vec2 local = aCorner.x * across + aCorner.y * along * stretch;
    // Sizes are fractions of width: scale y by aspect so particles stay round.
    vec2 p = pos + local * size * vec2(1.0, uAspect);

    vLocal = aCorner;
    vAlpha = clamp(alpha, 0.0, 1.0);
    vColor = color;
    vType = uType;
    vec2 clip = p * 2.0 - 1.0;
    gl_Position = vec4(clip.x, mix(clip.y, -clip.y, uFlipY), 0.0, 1.0);
}
