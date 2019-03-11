#version 300 es

precision mediump float;

// Fixed
uniform sampler2D centerFrame;
uniform sampler2D alignFrame;
uniform int MaxLOD;

// Changes every iteration
uniform int LOD;
uniform ivec2 alignment;
uniform int dy;

// Out
out vec4 diff;

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy = (xy << (MaxLOD - LOD)) + 1;

    // Calculate difference for dx of -1, 0, 1
    float centerPx = texelFetch(centerFrame, xy, LOD).x;
    float[3] alignPx;
    for (int i = 0; i < 3; i++) {
        alignPx[i] = texelFetch(alignFrame, xy + alignment + ivec2(i - 1, dy), LOD).x;
    }

    float p = 4.f;
    diff.x = pow(centerPx - alignPx[0], p);
    diff.y = pow(centerPx - alignPx[1], p);
    diff.z = pow(centerPx - alignPx[2], p);
    diff.w = 1.f + centerPx;
}
