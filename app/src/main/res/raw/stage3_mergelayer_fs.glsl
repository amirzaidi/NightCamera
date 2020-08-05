#version 300 es

#define MAX_FRAME_COUNT 5
#define TILE_SIZE 16

#define COS_INT_RES 128
#define M_PI 3.1415926535897932384626433832795f

precision mediump float;

uniform usampler2D altFrame1;
uniform usampler2D altFrame2;
uniform usampler2D altFrame3;
uniform usampler2D altFrame4;
uniform usampler2D refFrame;
uniform usampler2D alignment;
uniform sampler2D alignmentWeight;
uniform int alignCount;

uniform ivec2 frameSize;

// Out
out int result;

/*
ivec4[2] getOffsets(ivec2 xy) {
    uvec4 xyAlign;
    ivec4 xAlign, yAlign;
    ivec4 offsets[2];

    // Divide by TILE_SIZE, so we select the alignments for the current tile.
    ivec2 xyTileDiv = xy / TILE_SIZE;
    ivec2 xyTileMod = xy % TILE_SIZE;
    vec2 xyTileInterp = vec2(float(xyTileMod.x), float(xyTileMod.y));
    vec2 xyTileInterpFactor = xyTileInterp / float(TILE_SIZE) - 0.5f; // [-0.5, 0.5]

    ivec2 xyTileInterpFactorCos; // -0.5 -> 0, 0 -> 128, 0.5 -> 0
    xyTileInterpFactorCos.x = int(cos(M_PI * xyTileInterpFactor.x) * float(COS_INT_RES));
    xyTileInterpFactorCos.y = int(cos(M_PI * xyTileInterpFactor.y) * float(COS_INT_RES));

    if (xyTileInterpFactorCos.x < 0 || xyTileInterpFactorCos.x > 128) {
        offsets[0] = ivec4(99999);
        offsets[1] = ivec4(99999);
        return offsets;
    }
    if (xyTileInterpFactorCos.y < 0 || xyTileInterpFactorCos.y > 128) {
        offsets[0] = ivec4(-99999);
        offsets[1] = ivec4(-99999);
        return offsets;
    }

    // Which other tiles to sample.
    //int dx = xyTileInterpFactor.x < 0.f ? -1 : 1;
    //int dy = xyTileInterpFactor.y < 0.f ? -1 : 1;

    xyAlign = texelFetch(alignment, xyTileDiv, 0);
    ivec4 xAlignMid = (ivec4(xyAlign % 256u) - 128);
    ivec4 yAlignMid = (ivec4(xyAlign / 256u) - 128);

    xyAlign = texelFetch(alignment, xyTileDiv + ivec2(dx, 0), 0);
    ivec4 xAlignHorz = (ivec4(xyAlign % 256u) - 128);
    ivec4 yAlignHorz = (ivec4(xyAlign / 256u) - 128);

    // Horizontally interpolate the middle row.
    ivec4 xAlignMidHorz = (xyTileInterpFactorCos.x * xAlignMid
        + (COS_INT_RES - xyTileInterpFactorCos.x) * xAlignHorz) / COS_INT_RES;
    ivec4 yAlignMidHorz = (xyTileInterpFactorCos.x * yAlignMid
        + (COS_INT_RES - xyTileInterpFactorCos.x) * yAlignHorz) / COS_INT_RES;

    xyAlign = texelFetch(alignment, xyTileDiv + ivec2(0, dy), 0);
    ivec4 xAlignVert = (ivec4(xyAlign % 256u) - 128);
    ivec4 yAlignVert = (ivec4(xyAlign / 256u) - 128);

    xyAlign = texelFetch(alignment, xyTileDiv + ivec2(dx, dy), 0);
    ivec4 xAlignCorner = (ivec4(xyAlign % 256u) - 128);
    ivec4 yAlignCorner = (ivec4(xyAlign / 256u) - 128);

    // Horizontally interpolate the other row.
    ivec4 xAlignVertCorner = (xyTileInterpFactorCos.x * xAlignVert
        + (COS_INT_RES - xyTileInterpFactorCos.x) * xAlignCorner) / COS_INT_RES;
    ivec4 yAlignVertCorner = (xyTileInterpFactorCos.x * yAlignVert
        + (COS_INT_RES - xyTileInterpFactorCos.x) * yAlignCorner) / COS_INT_RES;

    // Vertically interpolate between the two rows.
    offsets[0] = (xyTileInterpFactorCos.y * xAlignMidHorz
        + (COS_INT_RES - xyTileInterpFactorCos.y) * xAlignVertCorner) / COS_INT_RES;
    offsets[1] = (xyTileInterpFactorCos.y * yAlignMidHorz
        + (COS_INT_RES - xyTileInterpFactorCos.y) * yAlignVertCorner) / COS_INT_RES;

    // Bypass.
    offsets[0] = xAlignMid;
    offsets[1] = yAlignMid;

    // Multiply by two, as we used a 2x boxdowned grayscale image.
    offsets[0] *= 2;
    offsets[1] *= 2;

    return offsets;
}
*/

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);

    ivec2 xyTileDiv = xy / TILE_SIZE;
    uvec4 xyAlign = texelFetch(alignment, xyTileDiv, 0);
    vec4 xyAlignWeight = texelFetch(alignmentWeight, xyTileDiv, 0);
    ivec4 xAlign = (ivec4(xyAlign % 256u) - 128) * 2;
    ivec4 yAlign = (ivec4(xyAlign / 256u) - 128) * 2;

    float px = float(texelFetch(refFrame, xy, 0).x);
    float pxWeight = 1.f;

    float weight;
    uint val;

    // Same code but for x, y, z, w.
    for (int i = 1; i <= alignCount; i++) {
        switch (i) {
            case 1:
                val = texelFetch(altFrame1, xy + ivec2(xAlign.x, yAlign.x), 0).x;
                weight = xyAlignWeight.x;
                break;
            case 2:
                val = texelFetch(altFrame2, xy + ivec2(xAlign.y, yAlign.y), 0).x;
                weight = xyAlignWeight.y;
                break;
            case 3:
                val = texelFetch(altFrame3, xy + ivec2(xAlign.z, yAlign.z), 0).x;
                weight = xyAlignWeight.z;
                break;
            case 4:
                val = texelFetch(altFrame4, xy + ivec2(xAlign.w, yAlign.w), 0).x;
                weight = xyAlignWeight.w;
                break;
        }

        px += weight * float(val);
        pxWeight += weight;
    }

    result = int(px / pxWeight);
}
