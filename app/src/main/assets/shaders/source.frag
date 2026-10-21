#version 300 es
precision highp float;

// Builds the warp's source image: the photo with its sky and/or background replaced.
// Runs before the warp, so motion arrows and overlays act on the new sky and background too.
// Every animated term is seamless in uPhase: drift uses the same two-copy crossfade as the
// warp, twinkle and aurora waves use integer frequencies.

in vec2 vUv;

uniform sampler2D uPhoto;
uniform sampler2D uSkyMask;    // 1 = sky (model mask + brush strokes)
uniform sampler2D uSkyPhoto;   // SkyStyle.PHOTO, mirrored-repeat wrap
uniform int uStyle;            // SkyStyle ordinal: clear, sunset, night, storm, aurora, photo
uniform int uSkyOn;            // 1 = sky stage active
uniform int uReplace;          // 1 = replace the sky, 0 = only tint the mask (editing)
uniform float uPhase;
uniform float uDrift;          // 0..1
uniform float uFeather;        // 0..1
uniform float uHarmonize;      // 0..1
uniform float uAspect;         // width / height
uniform float uSkyPhotoAspect; // width / height of the sky photo

// Background replacement (11d). uBgMode: -1 off, 0 tint the subject (editing view),
// otherwise BackgroundMode ordinal: 1 transparent, 2 solid, 3 gradient, 4 blur, 5 photo.
uniform int uBgMode;
uniform sampler2D uSubjectMask;  // 1 = subject (model mask + brush strokes)
uniform sampler2D uBlurred;      // blurred photo for mode 4
uniform sampler2D uBgPhoto;      // replacement photo for mode 5
uniform float uBgPhotoAspect;
uniform vec3 uBgColor1;
uniform vec3 uBgColor2;
uniform float uBgBlur;           // 0..1
uniform float uSubjectFeather;   // 0..1
uniform int uChecker;            // 1: draw transparent areas as a checkerboard (editor), 0: black (video)

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float cloudField(vec2 uv, float offset, float coverage, float scale) {
    vec2 p = vec2(uv.x * uAspect + offset, uv.y) * scale;
    return smoothstep(coverage, coverage + 0.3, fbm(p));
}

// Two copies of the same field half a cycle apart, crossfaded so the wrap never shows.
float drifting(vec2 uv, float coverage, float scale) {
    float p1 = fract(uPhase);
    float p2 = fract(uPhase + 0.5);
    float d = uDrift * 0.6;
    float c1 = cloudField(uv, d * (p1 - 0.5), coverage, scale);
    float c2 = cloudField(uv, d * (p2 - 0.5), coverage, scale);
    return mix(c1, c2, abs(2.0 * p1 - 1.0));
}

float stars(vec2 uv) {
    vec2 grid = vec2(uv.x * uAspect, uv.y) * 90.0;
    vec2 cell = floor(grid);
    float h = hash(cell);
    if (h < 0.985) return 0.0;
    vec2 center = cell + vec2(hash(cell + 3.1), hash(cell + 7.7));
    float d = length(grid - center);
    float m = 1.0 + floor(hash(cell + 1.3) * 3.0);
    float twinkle = 0.55 + 0.45 * sin(TAU * (m * uPhase + h * 10.0));
    return (1.0 - smoothstep(0.0, 0.35, d)) * twinkle;
}

vec3 skyColor(vec2 uv) {
    float h = uv.y; // 0 at the top of the photo
    if (uStyle == 0) {
        vec3 grad = mix(vec3(0.16, 0.42, 0.86), vec3(0.62, 0.80, 0.97), smoothstep(0.0, 1.0, h));
        float c = drifting(uv, 0.48, 2.5);
        return mix(grad, vec3(0.97, 0.98, 1.0), c * 0.9);
    } else if (uStyle == 1) {
        vec3 grad = mix(vec3(0.22, 0.16, 0.42), vec3(1.0, 0.52, 0.24), smoothstep(0.05, 0.95, h));
        float sd = length(vec2((uv.x - 0.5) * uAspect, (h - 0.85) * 1.6));
        float sun = exp(-sd * sd * 6.0);
        grad += vec3(1.0, 0.75, 0.4) * sun * 0.6;
        float c = drifting(uv, 0.5, 2.0);
        vec3 cloud = mix(vec3(0.95, 0.45, 0.45), vec3(1.0, 0.78, 0.55), h);
        return mix(grad, cloud, c * 0.75);
    } else if (uStyle == 2) {
        vec3 grad = mix(vec3(0.01, 0.02, 0.07), vec3(0.06, 0.10, 0.24), h);
        float c = drifting(uv, 0.6, 1.8);
        return grad + vec3(stars(uv)) * (1.0 - c) + vec3(0.12, 0.14, 0.22) * c * 0.5;
    } else if (uStyle == 3) {
        vec3 grad = mix(vec3(0.22, 0.24, 0.28), vec3(0.42, 0.44, 0.48), h);
        float c = drifting(uv, 0.3, 3.0);
        float flash = pow(max(0.0, sin(TAU * uPhase)), 60.0);
        return mix(grad, vec3(0.16, 0.17, 0.2), c * 0.8) + vec3(0.6, 0.65, 0.8) * flash * 0.5;
    } else if (uStyle == 4) {
        vec3 grad = mix(vec3(0.01, 0.03, 0.08), vec3(0.04, 0.10, 0.18), h);
        float wave = 0.32 + 0.07 * sin(TAU * (uv.x * 1.5 + uPhase)) + 0.03 * sin(TAU * (uv.x * 4.0 - 2.0 * uPhase));
        // x * x, not pow(x, 2.0): pow is undefined for negative x in GLSL.
        float dw = (h - wave) * 7.0;
        float band = exp(-dw * dw);
        float rays = 0.6 + 0.4 * noise(vec2(uv.x * uAspect * 30.0, 0.0));
        vec3 aurora = mix(vec3(0.1, 0.95, 0.55), vec3(0.6, 0.3, 0.95), smoothstep(wave - 0.1, wave + 0.15, h));
        return grad + vec3(stars(uv)) * 0.8 + aurora * band * rays * 0.9;
    }
    // Photo: cover-fit (no stretching), drift sideways with the crossfade trick,
    // mirrored repeat hides the edges. textureLod: this runs inside a per-pixel branch,
    // where implicit mip selection is undefined.
    vec2 fit = vec2(1.0);
    if (uSkyPhotoAspect > uAspect) fit.x = uAspect / uSkyPhotoAspect; else fit.y = uSkyPhotoAspect / uAspect;
    vec2 st = (vUv - 0.5) * fit + 0.5;
    float p1 = fract(uPhase);
    float p2 = fract(uPhase + 0.5);
    float d = uDrift * 0.3;
    vec3 a = textureLod(uSkyPhoto, vec2(st.x + d * (p1 - 0.5), st.y), 0.0).rgb;
    vec3 b = textureLod(uSkyPhoto, vec2(st.x + d * (p2 - 0.5), st.y), 0.0).rgb;
    return mix(a, b, abs(2.0 * p1 - 1.0));
}

// The light each style casts on the foreground when harmonizing.
vec3 styleTint() {
    if (uStyle == 0) return vec3(0.95, 1.0, 1.06);
    if (uStyle == 1) return vec3(1.12, 0.96, 0.84);
    if (uStyle == 2) return vec3(0.72, 0.8, 1.05);
    if (uStyle == 3) return vec3(0.86, 0.9, 0.96);
    if (uStyle == 4) return vec3(0.78, 0.95, 0.95);
    return vec3(1.0);
}

vec2 coverFit(vec2 uv, float imageAspect) {
    vec2 fit = vec2(1.0);
    if (imageAspect > uAspect) fit.x = uAspect / imageAspect; else fit.y = imageAspect / uAspect;
    return (uv - 0.5) * fit + 0.5;
}

vec3 background(vec3 photo) {
    if (uBgMode == 1) {
        if (uChecker == 0) return vec3(0.0);
        vec2 cell = floor(vec2(vUv.x * uAspect, vUv.y) * 24.0);
        return vec3(mix(0.62, 0.78, mod(cell.x + cell.y, 2.0)));
    }
    if (uBgMode == 2) return uBgColor1;
    if (uBgMode == 3) return mix(uBgColor1, uBgColor2, vUv.y);
    if (uBgMode == 4) return mix(photo, texture(uBlurred, vUv).rgb, uBgBlur);
    return texture(uBgPhoto, coverFit(vUv, uBgPhotoAspect)).rgb;
}

void main() {
    vec3 photo = texture(uPhoto, vUv).rgb;
    vec3 c = photo;

    if (uSkyOn == 1) {
        float raw = texture(uSkyMask, vUv).r;
        float f = 0.04 + 0.4 * uFeather;
        float m = smoothstep(0.5 - f, 0.5 + f, raw);
        if (uReplace == 0) {
            // Editing view: show the detected sky as a blue tint over the photo.
            c = mix(photo, vec3(0.2, 0.55, 1.0), m * 0.45);
        } else {
            vec3 fg = mix(photo, photo * styleTint(), uHarmonize * (1.0 - m));
            // Skip the sky math where there is no sky, it is the expensive part.
            vec3 sky = m > 0.002 ? skyColor(vUv) : vec3(0.0);
            c = mix(fg, clamp(sky, 0.0, 1.0), m);
        }
    }

    if (uBgMode >= 0) {
        float fs = 0.04 + 0.4 * uSubjectFeather;
        float s = smoothstep(0.5 - fs, 0.5 + fs, texture(uSubjectMask, vUv).r);
        if (uBgMode == 0) {
            // Editing view: show the subject as a violet tint.
            c = mix(c, vec3(0.49, 0.36, 1.0), s * 0.45);
        } else {
            // Uniform branches only (uBgMode), so the texture lookups in background() are well defined.
            c = mix(background(photo), c, s);
        }
    }
    fragColor = vec4(c, 1.0);
}
