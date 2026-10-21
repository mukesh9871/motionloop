#version 300 es
// Instanced particles. Every motion term is periodic in uPhase with an integer
// frequency, so phase and phase + 1 give the same frame (seamless loop).

layout(location = 0) in vec2 aCorner;  // quad corner in [-1, 1]
layout(location = 1) in vec4 aSeed;    // per instance: x0, y0, phase offset, random

uniform int uType;        // 1 sparkle, 2 snow, 3 rain, 4 bokeh, 5 embers, 6 bubbles, 7 hearts, 8 fireflies, 9 smoke
uniform float uPhase;
uniform float uSpeed;     // whole wraps per cycle for the slowest particles
uniform float uSize;      // 0..1
uniform float uAspect;    // width / height
uniform float uFlipY;
uniform float uPixel;     // one output pixel as a fraction of width (1 / width)

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
    // Wrap margins in normalized y, large enough that a particle is fully off-screen when
    // fract() wraps it around. Rain streaks are long and scaled by aspect, so they get more.
    float margin = 0.02 + 0.03 * max(uAspect, 1.0);
    float rainMargin = 0.02 + 0.08 * max(uAspect, 1.0);
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
        pos = vec2(x0 + 0.025 * sin(TAU * (m * t + ph)), y * (1.0 + 2.0 * margin) - margin);
        size = mix(0.004, 0.014, r) * (0.6 + 0.4 * k / (uSpeed * 3.0));
        alpha = 0.55 + 0.45 * r;
    } else if (uType == 3) {
        // Rain: fast, slanted streaks.
        float y = fract(y0 + 2.0 * k * t);
        // Start x spread over [-0.12, 1) so the slant still covers the lower left corner.
        pos = vec2(x0 * 1.12 - 0.12 + 0.12 * y, y * (1.0 + 2.0 * rainMargin) - rainMargin);
        size = mix(0.0015, 0.003, r);
        // Streak drawn along the actual motion. Screen y spans (1 + 2 * rainMargin) per wrap
        // while x moves 0.12, so the slope is 0.12 / (1 + 2 * rainMargin) in normalized
        // coordinates, times aspect in the width-scaled space the quad is built in.
        along = normalize(vec2(0.12 / (1.0 + 2.0 * rainMargin) * uAspect, 1.0));
        stretch = 14.0;
        alpha = 0.35 + 0.35 * r;
        color = vec3(0.85, 0.9, 1.0);
    } else if (uType == 4) {
        // Bokeh: large soft discs drifting in small circles.
        pos = vec2(x0, y0) + 0.04 * vec2(cos(TAU * (t + ph)), sin(TAU * (t + ph)));
        size = mix(0.03, 0.08, r);
        alpha = 0.18 + 0.2 * fract(r * 5.0);
        color = mix(vec3(1.0, 0.8, 0.55), vec3(0.6, 0.75, 1.0), fract(r * 11.0));
    } else if (uType == 5) {
        // Embers: rise, wiggle and flicker.
        float y = fract(y0 + k * t);
        pos = vec2(x0 + 0.03 * sin(TAU * (2.0 * m * t + ph)), 1.0 + margin - y * (1.0 + 2.0 * margin));
        size = mix(0.003, 0.009, r);
        alpha = 0.6 + 0.4 * sin(TAU * (3.0 * m * t + ph));
        color = mix(vec3(1.0, 0.45, 0.1), vec3(1.0, 0.8, 0.3), fract(r * 17.0));
    } else if (uType == 6) {
        // Bubbles: rise slowly with a wobble. Larger wrap margin: bubbles are big.
        float bm = margin + 0.04;
        float y = fract(y0 + uSpeed * t);
        pos = vec2(x0 + 0.02 * sin(TAU * (2.0 * m * t + ph)), 1.0 + bm - y * (1.0 + 2.0 * bm));
        size = mix(0.01, 0.035, r);
        alpha = 0.7;
        color = mix(vec3(0.85, 0.95, 1.0), vec3(1.0, 0.9, 1.0), fract(r * 9.0));
    } else if (uType == 7) {
        // Hearts: float up and sway, gently pulsing.
        float hm = margin + 0.03;
        float y = fract(y0 + uSpeed * t);
        pos = vec2(x0 + 0.03 * sin(TAU * (m * t + ph)), 1.0 + hm - y * (1.0 + 2.0 * hm));
        size = mix(0.012, 0.03, r) * (0.9 + 0.1 * sin(TAU * (2.0 * m * t + ph)));
        alpha = 0.85;
        color = mix(vec3(1.0, 0.3, 0.45), vec3(1.0, 0.55, 0.7), fract(r * 5.0));
    } else if (uType == 8) {
        // Fireflies: wander on small closed loops and pulse.
        pos = vec2(x0, y0) + 0.05 * vec2(sin(TAU * (m * t + ph)), sin(TAU * (2.0 * t + ph * 1.7)));
        size = mix(0.006, 0.014, r);
        float pulse = 0.5 + 0.5 * sin(TAU * (2.0 * m * t + ph));
        alpha = pulse * pulse;
        color = vec3(0.85, 1.0, 0.45);
    } else {
        // Smoke: large soft puffs that rise, grow and fade.
        float y = fract(y0 + uSpeed * t);
        float sm = margin + 0.12;
        pos = vec2(x0 + 0.06 * sin(TAU * (t + ph)), 1.0 + sm - y * (1.0 + 2.0 * sm));
        size = mix(0.05, 0.12, r) * (0.6 + 0.8 * y);
        alpha = 0.22 * sin(3.14159265 * y);
        color = vec3(0.85, 0.85, 0.88);
    }

    // At least about 1.5 px wide, so thin particles do not flicker out in small (GIF) exports.
    size = max(size * mix(0.5, 1.8, uSize), 0.75 * uPixel);
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
