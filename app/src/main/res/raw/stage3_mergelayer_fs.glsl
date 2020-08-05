#version 300 es

#define TILE_SIZE 16

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

vec4 getAlignedVals(ivec2 xy, ivec4 xAlign, ivec4 yAlign) {
    return vec4(
        texelFetch(altFrame1, xy + ivec2(xAlign.x, yAlign.x), 0).x,
        texelFetch(altFrame2, xy + ivec2(xAlign.y, yAlign.y), 0).x,
        texelFetch(altFrame3, xy + ivec2(xAlign.z, yAlign.z), 0).x,
        texelFetch(altFrame4, xy + ivec2(xAlign.w, yAlign.w), 0).x
    );
}

vec4 getAlignedVals(ivec2 xy, ivec2 xyTile) {
    uvec4 xyAlign = texelFetch(alignment, xyTile, 0);
    ivec4 xAlign = (ivec4(xyAlign % 256u) - 128) * 2;
    ivec4 yAlign = (ivec4(xyAlign / 256u) - 128) * 2;
    return getAlignedVals(xy, xAlign, yAlign);
}

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);

    // Divide by TILE_SIZE, so we select the alignments for the current tile.
    ivec2 xyTileDiv = xy / TILE_SIZE;
    ivec2 xyTileMod = xy % TILE_SIZE;
    vec2 xyTileInterp = vec2(float(xyTileMod.x), float(xyTileMod.y));
    vec2 xyTileInterpFactor = xyTileInterp / float(TILE_SIZE) - 0.5f; // [-0.5, 0.5]

    // -0.5 -> 0.5, 0 -> 1, 0.5 -> 0.5. Multiply it directly with Mid, and inverted with Corner.
    vec2 xyTileInterpFactorCos = 0.5f + 0.5f * vec2(
        cos(M_PI * xyTileInterpFactor.x),
        cos(M_PI * xyTileInterpFactor.y)
    );
    vec2 xyTileInterpFactorCosInv = 1.f - xyTileInterpFactorCos;

    // Which other tiles to sample.
    int dx = xyTileInterpFactor.x < 0.f ? -1 : 1;
    int dy = xyTileInterpFactor.y < 0.f ? -1 : 1;

    // Middle. 00
    vec4 xyAlignMidWeight = texelFetch(alignmentWeight, xyTileDiv, 0);
    vec4 xyAlignMidVal = getAlignedVals(xy, xyTileDiv);

    // Left or Right. 10
    vec4 xyAlignHorzWeight = texelFetch(alignmentWeight, xyTileDiv + ivec2(dx, 0), 0);
    vec4 xyAlignHorzVal = getAlignedVals(xy, xyTileDiv + ivec2(dx, 0));

    // Top or Bottom. 01
    vec4 xyAlignVertWeight = texelFetch(alignmentWeight, xyTileDiv + ivec2(0, dy), 0);
    vec4 xyAlignVertVal = getAlignedVals(xy, xyTileDiv + ivec2(0, dy));

    // Corner. 11
    vec4 xyAlignCornerWeight = texelFetch(alignmentWeight, xyTileDiv + ivec2(dx, dy), 0);
    vec4 xyAlignCornerVal = getAlignedVals(xy, xyTileDiv + ivec2(dx, dy));

    // Bypass for now.
    float px = float(texelFetch(refFrame, xy, 0).x);
    float pxWeight = 1.f;

    float midWeight, horzWeight, vertWeight, cornerWeight;
    vec4 dotter;

    // Same code but for x, y, z, w.
    for (int i = 0; i < alignCount; i++) {
        switch (i) {
            case 0:
                dotter = vec4(1.f, 0.f, 0.f, 0.f);
                break;
            case 1:
                dotter = vec4(0.f, 1.f, 0.f, 0.f);
                break;
            case 2:
                dotter = vec4(0.f, 0.f, 1.f, 0.f);
                break;
            case 3:
                dotter = vec4(0.f, 0.f, 0.f, 1.f);
                break;
        }

        midWeight = dot(dotter, xyAlignMidWeight);
        horzWeight = dot(dotter, xyAlignHorzWeight);
        vertWeight = dot(dotter, xyAlignVertWeight);
        cornerWeight = dot(dotter, xyAlignCornerWeight);

        // Cosine window, so middle only gets the mid pixel, and the edge gets split 50/50.
        midWeight *= xyTileInterpFactorCos.x * xyTileInterpFactorCos.y;
        horzWeight *= xyTileInterpFactorCosInv.x * xyTileInterpFactorCos.y;
        vertWeight *= xyTileInterpFactorCos.x * xyTileInterpFactorCosInv.y;
        cornerWeight *= xyTileInterpFactorCosInv.x * xyTileInterpFactorCosInv.y;

        // For this alt frame, add all four possible pixel values with their weights.
        px += midWeight * dot(dotter, xyAlignMidVal);
        pxWeight += midWeight;
        px += horzWeight * dot(dotter, xyAlignHorzVal);
        pxWeight += horzWeight;
        px += vertWeight * dot(dotter, xyAlignVertVal);
        pxWeight += vertWeight;
        px += cornerWeight * dot(dotter, xyAlignCornerVal);
        pxWeight += cornerWeight;
        break;
    }

    result = int(round(px / pxWeight));
}
