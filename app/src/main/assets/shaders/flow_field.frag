#version 300 es
precision highp float;

// Turns sparse arrows and anchors into a dense, smooth velocity field.
// Velocity is in normalized uv units per loop cycle.

#define MAX_ITEMS 64

in vec2 vUv;

uniform int uArrowCount;
uniform vec4 uArrows[MAX_ITEMS];   // start.xy, end.xy (normalized)
uniform int uAnchorCount;
uniform vec4 uAnchors[MAX_ITEMS];  // center.xy, radius (fraction of width), unused
uniform float uAspect;             // width / height
uniform float uEncode;             // 1.0: pack into an RGBA8 target
uniform float uMaxFlow;

out vec4 fragColor;

// Distances are measured with x scaled by the aspect ratio so influence is circular.
vec2 toAspect(vec2 p) { return vec2(p.x * uAspect, p.y); }

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-8), 0.0, 1.0);
    return length(p - (a + ab * t));
}

void main() {
    vec2 p = toAspect(vUv);
    vec2 sum = vec2(0.0);
    float wsum = 0.0;

    // Inverse distance weighting (Shepard, power 2). Smooth everywhere,
    // and exactly the arrow velocity on top of an arrow.
    for (int i = 0; i < MAX_ITEMS; i++) {
        if (i >= uArrowCount) break;
        vec4 a = uArrows[i];
        float d = segmentDistance(p, toAspect(a.xy), toAspect(a.zw));
        float w = 1.0 / (d * d + 1e-4);
        sum += (a.zw - a.xy) * w;
        wsum += w;
    }

    // Anchors vote for zero velocity and also hard-stop motion inside their radius.
    float still = 1.0;
    for (int i = 0; i < MAX_ITEMS; i++) {
        if (i >= uAnchorCount) break;
        vec4 an = uAnchors[i];
        float r = an.z * uAspect;
        float d = length(p - toAspect(an.xy));
        float e = max(d - r, 0.0);
        wsum += 1.0 / (e * e + 1e-4);
        still *= smoothstep(r * 0.6, r * 1.3, d);
    }

    vec2 v = wsum > 0.0 ? (sum / wsum) * still : vec2(0.0);

    if (uEncode > 0.5) {
        fragColor = vec4(clamp(v / uMaxFlow, -1.0, 1.0) * 0.5 + 0.5, 0.0, 1.0);
    } else {
        fragColor = vec4(v, 0.0, 1.0);
    }
}
