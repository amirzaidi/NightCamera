#version 300 es

precision mediump float;

uniform usampler2D centerFrame;
uniform int alignCount;
uniform ivec2 frameSize;

uniform usampler2D alignFrame1;
uniform usampler2D alignFrame2;
uniform usampler2D alignFrame3;
uniform usampler2D alignFrame4;
uniform usampler2D alignFrame5;
uniform usampler2D alignFrame6;

uniform ivec2 alignVec1;
uniform ivec2 alignVec2;
uniform ivec2 alignVec3;
uniform ivec2 alignVec4;
uniform ivec2 alignVec5;
uniform ivec2 alignVec6;

// Out
out int result;

bool isInLimits(ivec2 xy) {
    return xy.x >= 0 && xy.y >= 0 && xy.x < frameSize.x && xy.y < frameSize.y;
}

int getResult(ivec2 xy) {
    int px[7], tmp;
    ivec2 xyAligned;

    px[0] = int(texelFetch(centerFrame, xy, 0).x);
    int p = 1;

    if (alignCount >= 1) {
        xyAligned = xy + alignVec1;
        if (isInLimits(xyAligned)) {
            px[p++] = int(texelFetch(alignFrame1, xyAligned, 0).x);
        }
    }

    if (alignCount >= 2) {
        xyAligned = xy + alignVec2;
        if (isInLimits(xyAligned)) {
            px[p++] = int(texelFetch(alignFrame2, xyAligned, 0).x);
        }
    }

    if (alignCount >= 3) {
        xyAligned = xy + alignVec3;
        if (isInLimits(xyAligned)) {
            px[p++] = int(texelFetch(alignFrame3, xyAligned, 0).x);
        }
    }

    if (alignCount >= 4) {
        xyAligned = xy + alignVec4;
        if (isInLimits(xyAligned)) {
            px[p++] = int(texelFetch(alignFrame4, xyAligned, 0).x);
        }
    }

    if (alignCount >= 5) {
        xyAligned = xy + alignVec5;
        if (isInLimits(xyAligned)) {
            px[p++] = int(texelFetch(alignFrame5, xyAligned, 0).x);
        }
    }

    if (alignCount >= 6) {
        xyAligned = xy + alignVec6;
        if (isInLimits(xyAligned)) {
            px[p++] = int(texelFetch(alignFrame6, xyAligned, 0).x);
        }
    }

    for (int i = 1; i < p; i++) {
        for (int j = i - 1; j >= 0; j--) {
            if (px[j] > px[i]) {
                tmp = px[i];
                px[i] = px[j];
                px[j] = tmp;
            }
        }
    }

    // Multiple of 2, so average the middle two, count 4 -> index 1, 2.
    if (p % 2 == 0) {
        return (px[(p / 2) - 1] + px[p / 2]) / 2;
    }

    // Rounding down of integer division, count 3 -> index 1.
    return px[p / 2];
}

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);
    result = getResult(xy);
}
