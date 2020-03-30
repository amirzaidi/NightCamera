#version 300 es

precision mediump float;

uniform usampler2D centerFrame;
uniform int alignCount;

uniform usampler2D alignFrame1;
uniform usampler2D alignFrame2;
uniform usampler2D alignFrame3;
uniform usampler2D alignFrame4;

uniform ivec2 alignVec1;
uniform ivec2 alignVec2;
uniform ivec2 alignVec3;
uniform ivec2 alignVec4;

// Out
out int result;

int getResult(ivec2 xy) {
    int px[5], tmp;
    px[0] = int(texelFetch(centerFrame, xy, 0).x);
    px[1] = int(texelFetch(alignFrame1, xy - alignVec1, 0).x);

    // Shortcuts
    if (alignCount == 1) {
        return px[0];
    }

    if (alignCount == 2) {
        return (px[0] + px[1]) / 2;
    }

    px[2] = int(texelFetch(alignFrame2, xy - alignVec2, 0).x);
    px[3] = int(texelFetch(alignFrame3, xy - alignVec3, 0).x);
    px[4] = int(texelFetch(alignFrame4, xy - alignVec4, 0).x);

    for (int i = 1; i < alignCount; i++) {
        for (int j = i - 1; j >= 0; j--) {
            if (px[j] > px[i]) {
                tmp = px[i];
                px[i] = px[j];
                px[j] = tmp;
            }
        }
    }

    if (alignCount == 3) {
        return px[1];
    }

    if (alignCount == 4) {
        return (px[1] + px[2]) / 2;
    }

    return px[2];
}

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);
    result = getResult(xy);
}
