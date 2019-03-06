#version 300 es

precision mediump float;

uniform sampler2D centerFrame;
uniform sampler2D alignFrame;
uniform int LOD;
uniform int MaxLOD;
uniform ivec2 alignment;

// Out
out ivec4 localAlign;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy = (xy << (MaxLOD - LOD)) + 1;

    float centerPx = texelFetch(centerFrame, xy, LOD).x;
    float alignPx, diff, minDiff = -1.f, totalDiff;
    int minDiffCount = 0;
    ivec2 dxy, minDxy;

    for (int i = 0; i < 9; i++) {
        dxy = ivec2((i % 3) - 1, (i / 3) - 1);
        alignPx = texelFetch(alignFrame, xy + alignment + dxy, LOD).x;
        diff = distance(alignPx, centerPx);
        totalDiff += pow(diff, 3.f);

        if (minDiff == -1.f || diff < minDiff) {
            minDiffCount = 1;
            minDxy = dxy;
            minDiff = diff;
        } else if (diff == minDiff) {
            minDiffCount++;
        }
    }

    if (minDiffCount == 1) {
        localAlign.xy = minDxy;
        localAlign.z = int(pow(totalDiff / 9.f, 0.33f));
        localAlign.w = 1;
    } else {
        // In doubt, disable this patch
        localAlign.w = 0;
    }
}
