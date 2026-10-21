#version 300 es
precision mediump float;

// One direction of a separable 9-tap Gaussian. uStep is one texel along x or y.
in vec2 vUv;
uniform sampler2D uSrc;
uniform vec2 uStep;
out vec4 fragColor;

void main() {
    // Weights for sigma about 2 texels, spread at 1.5 texel spacing for a wide, cheap blur.
    vec3 c = texture(uSrc, vUv).rgb * 0.2270;
    c += texture(uSrc, vUv + uStep * 1.5).rgb * 0.1946;
    c += texture(uSrc, vUv - uStep * 1.5).rgb * 0.1946;
    c += texture(uSrc, vUv + uStep * 3.0).rgb * 0.1216;
    c += texture(uSrc, vUv - uStep * 3.0).rgb * 0.1216;
    c += texture(uSrc, vUv + uStep * 4.5).rgb * 0.0540;
    c += texture(uSrc, vUv - uStep * 4.5).rgb * 0.0540;
    c += texture(uSrc, vUv + uStep * 6.0).rgb * 0.0162;
    c += texture(uSrc, vUv - uStep * 6.0).rgb * 0.0162;
    fragColor = vec4(c, 1.0);
}
