#version 300 es

#define FLT_MAX 3.402823466e+38

#define TILE_SIZE 8
#define TILE_PX_COUNT 256

#define ALIGN_MAX_SHIFT 4

precision mediump float;

uniform usampler2D refFrame;
uniform usampler2D altFrame;
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

    ivec2 xyFrame = xy * TILE_SIZE;
    uint refData[TILE_PX_COUNT];
    for (int i = 0; i < TILE_PX_COUNT; i++) {
        ivec2 xyRef = xyFrame + ivec2(i % TILE_SIZE, i / TILE_SIZE);
        refData[i] = texelFetch(refFrame, xyRef, 0).x;
    }

    ivec4 bestXShift, bestYShift;
    vec4 bestXYNoise = vec4(FLT_MAX);

    for (int dY = -ALIGN_MAX_SHIFT; dY <= ALIGN_MAX_SHIFT; dY++) {
        for (int dX = -ALIGN_MAX_SHIFT; dX <= ALIGN_MAX_SHIFT; dX++) {
            vec4 currXYNoise = vec4(0.f);

            // Iterate over refData, processing all altData frames simultaneously.
            for (int y = 0; y < TILE_SIZE; y++) {
                int shiftedY = y + dY;
                //bool isYInCache = shiftedY >= 0 && shiftedY < TILE_SIZE;
                for (int x = 0; x < TILE_SIZE; x++) {
                    // RefData is always in cache.
                    uint refDataVal = refData[y * TILE_SIZE + x];
                    int shiftedX = x + dX;
                    ivec2 xyRef = xyFrame + ivec2(shiftedX, shiftedY);

                    // Do a very slow texelFetch.
                    uvec4 altDataVal = uvec4(
                        texelFetch(altFrame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x,
                        texelFetch(altFrame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y,
                        texelFetch(altFrame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z,
                        texelFetch(altFrame, xyRef + ivec2(xAlign.w, yAlign.w), 0).w
                    );

                    // All frame data is loaded, compare reference frame with other frames.
                    // Linear noise model.
                    currXYNoise += vec4(abs(ivec4(altDataVal) - int(refDataVal)));
                }
            }

            // Manually update the three frames' best shift.
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
