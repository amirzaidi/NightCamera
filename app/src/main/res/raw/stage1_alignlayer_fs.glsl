#version 300 es

#define FLT_MAX 3.402823466e+38

#define TILE_SIZE 8
#define TILE_PX_COUNT 256

#define ALIGN_MAX_SHIFT 4

precision mediump float;

uniform usampler2D frame;
uniform ivec2 bounds;

uniform usampler2D prevLayerAlign;
uniform int prevLayerScale;

out uvec3 result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec3 xAlign = ivec3(0);
    ivec3 yAlign = ivec3(0);
    if (prevLayerScale > 0) {
        uvec3 xyAlign = texelFetch(prevLayerAlign, xy / 4, 0).xyz;
        xAlign = (ivec3(xyAlign % 256u) - 128) * prevLayerScale;
        yAlign = (ivec3(xyAlign / 256u) - 128) * prevLayerScale;
    }

    ivec2 xyFrame = xy * TILE_SIZE;
    uvec3 altData[TILE_PX_COUNT]; // Indices match with refData if we would keep the alignments at 0, 0
    uint refData[TILE_PX_COUNT];
    for (int i = 0; i < TILE_PX_COUNT; i++) {
        ivec2 xyRef = xyFrame + ivec2(i % TILE_SIZE, i / TILE_SIZE);
        altData[i].x = texelFetch(frame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x;
        altData[i].y = texelFetch(frame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y;
        altData[i].z = texelFetch(frame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z;
        refData[i] = texelFetch(frame, xyRef, 0).w;
    }

    ivec3 bestXShift;
    ivec3 bestYShift;
    vec3 bestXYNoise = vec3(FLT_MAX);

    for (int dY = -ALIGN_MAX_SHIFT; dY <= ALIGN_MAX_SHIFT; dY++) {
        for (int dX = -ALIGN_MAX_SHIFT; dX <= ALIGN_MAX_SHIFT; dX++) {
            vec3 currXYNoise = vec3(0.f);

            // Iterate over refData, processing all altData frames simultaneously.
            for (int y = 0; y < TILE_SIZE; y++) {
                int shiftedY = y + dY;
                bool isYInCache = shiftedY >= 0 && shiftedY < TILE_SIZE;
                for (int x = 0; x < TILE_SIZE; x++) {
                    // RefData is always in cache.
                    uint refDataVal = refData[y * TILE_SIZE + x];
                    uvec3 altDataVal;
                    int shiftedX = x + dX;
                    if (isYInCache && shiftedX >= 0 && shiftedX < TILE_SIZE) {
                        // Get from cache.
                        altDataVal = altData[shiftedY * TILE_SIZE + shiftedX];
                    } else {
                        // Do a slow texelFetch.
                        ivec2 xyRef = xyFrame + ivec2(shiftedX, shiftedY);
                        altDataVal.x = texelFetch(frame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x;
                        altDataVal.y = texelFetch(frame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y;
                        altDataVal.z = texelFetch(frame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z;
                    }

                    // All frame data is loaded, compare reference frame with other frames.
                    vec3 diff = vec3(abs(ivec3(altDataVal) - int(refDataVal)));
                    currXYNoise += diff;
                    //currXYNoise += diff * diff;
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
        }
    }

    uvec3 lowerBits = uvec3(
        uint(xAlign.x + bestXShift.x + 128),
        uint(xAlign.y + bestXShift.y + 128),
        uint(xAlign.z + bestXShift.z + 128)
    );

    uvec3 higherBits = uvec3(
        uint(yAlign.x + bestYShift.x + 128),
        uint(yAlign.y + bestYShift.y + 128),
        uint(yAlign.z + bestYShift.z + 128)
    );

    result = 256u * higherBits + lowerBits;
}
