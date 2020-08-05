#version 300 es

#define TILE_SIZE 16

precision mediump float;

uniform usampler2D altFrame1;
uniform usampler2D altFrame2;
uniform usampler2D altFrame3;
uniform usampler2D altFrame4;
uniform usampler2D refFrame;
uniform usampler2D alignment;
uniform int alignCount;

uniform ivec2 frameSize;

// Out
out int result;

bool isInLimits(ivec2 xy) {
    return xy.x >= 0 && xy.y >= 0 && xy.x < frameSize.x && xy.y < frameSize.y;
}

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);
    uint px[MAX_FRAME_COUNT], tmp;
    ivec2 xyAligned;

    // Divide by TILE_SIZE, so we select the alignments for the current tile.
    uvec4 xyAlign = texelFetch(alignment, xy / TILE_SIZE, 0);

    // Multiply by two, as we used a 2x boxdowned grayscale image.
    ivec4 xAlign = (ivec4(xyAlign % 256u) - 128) * 2;
    ivec4 yAlign = (ivec4(xyAlign / 256u) - 128) * 2;

    px[0] = texelFetch(refFrame, xy, 0).x;
    int p = 1;

    //return int(px[0]);

    if (alignCount >= 1) {
        xyAligned = xy + ivec2(xAlign.x, yAlign.x);
        if (isInLimits(xyAligned)) {
            px[p++] = texelFetch(altFrame1, xyAligned, 0).x;
        }
    }

    if (alignCount >= 2) {
        xyAligned = xy + ivec2(xAlign.y, yAlign.y);
        if (isInLimits(xyAligned)) {
            px[p++] = texelFetch(altFrame2, xyAligned, 0).x;
        }
    }

    if (alignCount >= 3) {
        xyAligned = xy + ivec2(xAlign.z, yAlign.z);
        if (isInLimits(xyAligned)) {
            px[p++] = texelFetch(altFrame3, xyAligned, 0).x;
        }
    }

    if (alignCount >= 4) {
        xyAligned = xy + ivec2(xAlign.w, yAlign.w);
        if (isInLimits(xyAligned)) {
            px[p++] = texelFetch(altFrame4, xyAligned, 0).x;
        }
    }

    /*
    uint pxSum = 0u;
    for (int i = 0; i < p; i++) {
        pxSum += px[i];
    }
    return int(pxSum) / p;
    //*/

    //*

    // Smart median.
    for (int i = 1; i < p; i++) {
        for (int j = i - 1; j >= 0; j--) {
            if (px[j] > px[i]) {
                tmp = px[i];
                px[i] = px[j];
                px[j] = tmp;
            }
        }
    }

    if (p % 2 == 0) {
        // Multiple of 2, so average the middle two, count 4 -> index 1, 2.
        result = int(px[(p / 2) - 1] + px[p / 2]) / 2;
    } else {
        // Rounding down of integer division, count 3 -> index 1.
        result = int(px[p / 2]);
    }
}
