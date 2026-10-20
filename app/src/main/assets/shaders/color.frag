#version 300 es
precision highp float;
precision highp sampler3D;

// Adjustments then an optional 3D LUT grade, on the warped frame.

in vec2 vUv;

uniform sampler2D uSrc;
uniform sampler3D uLut;
uniform float uLutSize;
uniform float uLutMix;       // 0 = no grade
uniform float uBrightness;   // -1..1
uniform float uContrast;     // -1..1
uniform float uSaturation;   // -1..1
uniform float uWarmth;       // -1..1

out vec4 fragColor;

void main() {
    vec3 c = texture(uSrc, vUv).rgb;

    c *= vec3(1.0 + 0.10 * uWarmth, 1.0, 1.0 - 0.10 * uWarmth);
    c += 0.25 * uBrightness;
    c = (c - 0.5) * (1.0 + 0.8 * uContrast) + 0.5;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = clamp(mix(vec3(l), c, 1.0 + uSaturation), 0.0, 1.0);

    if (uLutMix > 0.0) {
        // Sample texel centers so 0 and 1 hit the first and last LUT entries exactly.
        vec3 coord = c * ((uLutSize - 1.0) / uLutSize) + 0.5 / uLutSize;
        c = mix(c, texture(uLut, coord).rgb, uLutMix);
    }
    fragColor = vec4(c, 1.0);
}
