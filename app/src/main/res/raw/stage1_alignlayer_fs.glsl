#version 300 es

#define FLT_MAX 3.402823466e+38

#define TILE_SCALE 8
#define TILE_SIZE 16

#define TILE_MIN_INDEX -4
#define TILE_MAX_INDEX 12

#define TILE_PX_COUNT 256

// Should be at least TILE_SCALE / 2.
#define ALIGN_MIN_SHIFT -4
#define ALIGN_MAX_SHIFT 4

precision mediump float;

uniform sampler2D refFrame;
uniform sampler2D altFrameHorz;
uniform sampler2D altFrameVert;
uniform ivec2 bounds;

uniform usampler2D prevLayerAlign;
uniform int prevLayerScale;

out uvec4 result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec4 xAlign = ivec4(0);
    ivec4 yAlign = ivec4(0);
    if (prevLayerScale > 0) {
        uvec4 xyAlign = texelFetch(prevLayerAlign, xy / prevLayerScale, 0);
        xAlign = (ivec4(xyAlign % 256u) - 128) * prevLayerScale;
        yAlign = (ivec4(xyAlign / 256u) - 128) * prevLayerScale;
    }

    ivec2 xyFrame = xy * TILE_SCALE;

    int x, y;
    float refDataVal;
    float refDataHorz[TILE_SIZE]; // Horizontally integrated, so a vertical line of data.
    float refDataVert[TILE_SIZE]; // Vertically integrated, so a horizontal line of data.

    // Init at zero.
    for (int i = 0; i < TILE_SIZE; i++) {
        refDataVert[i] = 0.f;
        refDataHorz[i] = 0.f;
    }

    // Compute CVN and CHN for reference frame.
    for (int i = 0; i < TILE_PX_COUNT; i++) {
        int x = i % TILE_SIZE;
        int y = i / TILE_SIZE;
        refDataVal = texelFetch(refFrame, xyFrame + ivec2(x, y) + TILE_MIN_INDEX, 0).x;
        refDataHorz[y] += refDataVal;
        refDataVert[x] += refDataVal;
    }

    // Optimize the bestXShift and bestYShift by minimizing bestXYNoise.
    ivec4 bestXShift, bestYShift;
    vec4 bestXYNoise = vec4(FLT_MAX);

    // Varying variables.
    vec4 altDataVal;
    ivec2 xyShifted, xyIndex;
    vec4 noisef;
    vec4 currXYNoise;
    for (int dY = ALIGN_MIN_SHIFT; dY <= ALIGN_MAX_SHIFT; dY++) {
        for (int dX = ALIGN_MIN_SHIFT; dX <= ALIGN_MAX_SHIFT; dX++) {
            currXYNoise = vec4(0.f);
            xyShifted = xyFrame + ivec2(dX, dY);

            // Check all horizontally integrated rows.
            for (y = TILE_MIN_INDEX; y < TILE_MAX_INDEX; y++) {
                xyIndex = xyShifted + ivec2(0, y);
                altDataVal.x = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.x, yAlign.x), 0).x;
                altDataVal.y = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.y, yAlign.y), 0).y;
                altDataVal.z = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.z, yAlign.z), 0).z;
                altDataVal.w = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.w, yAlign.w), 0).w;

                // All frame data is loaded, compare reference frame with other frames.
                // Linear noise model.
                noisef = abs(altDataVal - refDataHorz[y - TILE_MIN_INDEX]);
                currXYNoise += noisef;
            }

            // Check all vertically integrated columns.
            for (x = TILE_MIN_INDEX; x < TILE_MAX_INDEX; x++) {
                xyIndex = xyShifted + ivec2(x, 0);
                altDataVal.x = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.x, yAlign.x), 0).x;
                altDataVal.y = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.y, yAlign.y), 0).y;
                altDataVal.z = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.z, yAlign.z), 0).z;
                altDataVal.w = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.w, yAlign.w), 0).w;

                // All frame data is loaded, compare reference frame with other frames.
                // Linear noise model.
                noisef = abs(altDataVal - refDataVert[x - TILE_MIN_INDEX]);
                currXYNoise += noisef;
            }

            // Manually update the four frames' best shift.
            if (currXYNoise.x < bestXYNoise.x) {
                bestXYNoise.x = currXYNoise.x;
                bestXShift.x = dX;
                bestYShift.x = dY;
            }
            if (currXYNoise.y < bestXYNoise.y) {
                bestXYNoise.y = currXYNoise.y;
                bestXShift.y = dX;
                bestYShift.y = dY;
            }
            if (currXYNoise.z < bestXYNoise.z) {
                bestXYNoise.z = currXYNoise.z;
                bestXShift.z = dX;
                bestYShift.z = dY;
            }
            if (currXYNoise.w < bestXYNoise.w) {
                bestXYNoise.w = currXYNoise.w;
                bestXShift.w = dX;
                bestYShift.w = dY;
            }
        }
    }

    // Vectorizing this mathematical operation seems to create bugs.
    result = 256u * uvec4(
        uint(yAlign.x + bestYShift.x + 128),
        uint(yAlign.y + bestYShift.y + 128),
        uint(yAlign.z + bestYShift.z + 128),
        uint(yAlign.w + bestYShift.w + 128)
    ) + uvec4(
        uint(xAlign.x + bestXShift.x + 128),
        uint(xAlign.y + bestXShift.y + 128),
        uint(xAlign.z + bestXShift.z + 128),
        uint(xAlign.w + bestXShift.w + 128)
    );
}
