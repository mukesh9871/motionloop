#version 300 es
precision highp float;
precision highp sampler3D;

// Adjustments, an optional 3D LUT grade, then an optional overlay layer blended on top.

in vec2 vUv;

uniform sampler2D uSrc;
uniform sampler3D uLut;
uniform sampler2D uOverlay;   // premultiplied RGBA layer (particles, light leaks)
uniform float uLutSize;
uniform float uLutMix;        // 0 = no grade
uniform float uBrightness;    // -1..1
uniform float uContrast;      // -1..1
uniform float uSaturation;    // -1..1
uniform float uWarmth;        // -1..1
uniform int uBlendMode;       // -1 none, then BlendMode.ordinal: normal, add, screen, overlay, multiply, soft light
uniform float uOverlayOpacity;

out vec4 fragColor;

vec3 blendLayer(vec3 base, vec4 layer, int mode) {
    // Un-premultiply for the modes that need the layer's own color.
    vec3 lc = layer.a > 0.0001 ? layer.rgb / layer.a : vec3(0.0);
    vec3 b;
    if (mode == 0) {
        return layer.rgb + base * (1.0 - layer.a);                       // normal (premultiplied over)
    } else if (mode == 1) {
        return min(base + layer.rgb, vec3(1.0));                          // add
    } else if (mode == 2) {
        return 1.0 - (1.0 - base) * (1.0 - layer.rgb);                    // screen
    } else if (mode == 3) {
        b = mix(2.0 * base * lc, 1.0 - 2.0 * (1.0 - base) * (1.0 - lc), step(0.5, base)); // overlay
    } else if (mode == 4) {
        b = base * lc;                                                     // multiply
    } else {
        b = (1.0 - 2.0 * lc) * base * base + 2.0 * lc * base;              // soft light (pegtop)
    }
    return mix(base, b, layer.a);
}

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

    if (uBlendMode >= 0) {
        vec4 layer = texture(uOverlay, vUv);
        c = mix(c, clamp(blendLayer(c, layer, uBlendMode), 0.0, 1.0), uOverlayOpacity);
    }
    fragColor = vec4(c, 1.0);
}
