#version 300 es
layout(location = 0) in vec2 aCorner;  // quad corner in [-1, 1]
layout(location = 1) in vec3 aStamp;   // per instance: center.xy (normalized), radius (fraction of width)

uniform float uAspect;  // width / height

out vec2 vLocal;

void main() {
    vLocal = aCorner;
    // Radius is a fraction of width, so in normalized y it is radius * width / height.
    vec2 r = vec2(aStamp.z, aStamp.z * uAspect);
    vec2 pos = aStamp.xy + aCorner * r;
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
