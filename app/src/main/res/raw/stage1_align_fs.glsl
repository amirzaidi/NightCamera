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

vec2 grad(ivec2 xy) {
    float left = texelFetch(centerFrame, xy + ivec2(-1, 0), LOD).x;
    float right = texelFetch(centerFrame, xy + ivec2(1, 0), LOD).x;

    float top = texelFetch(centerFrame, xy + ivec2(0, -1), LOD).x;
    float bottom = texelFetch(centerFrame, xy + ivec2(0, 1), LOD).x;

    return vec2(bottom - top, right - left);
}

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy = (xy << (MaxLOD - LOD)) + 1;

    // Calculate difference for dx of -1, 0, 1
    float centerPx = texelFetch(centerFrame, xy, LOD).x;
    float[3] alignPx;
    for (int dx = -1; dx <= 1; dx++) {
        alignPx[dx + 1] = texelFetch(alignFrame, xy + alignment + ivec2(dx, dy), LOD).x;
    }

    float p = 2.f;
    diff.x = pow(distance(centerPx, alignPx[0]), p);
    diff.y = pow(distance(centerPx, alignPx[1]), p);
    diff.z = pow(distance(centerPx, alignPx[2]), p);
    diff.w = length(grad(xy));
    //diff.w = 1.f;
}
