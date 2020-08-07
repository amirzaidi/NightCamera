#version 300 es

#define TILE_OFFSET 4
#define TILE_SCALE 8
#define TILE_SIZE 16
#define TILE_PX_COUNT 256.f

#define MIN_NOISE 1750.f
#define MAX_NOISE 2750.f

precision mediump float;

uniform sampler2D refFrame;
uniform sampler2D altFrame;
uniform usampler2D alignment;

// Out
out vec4 result;

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);

    uvec4 xyAlign = texelFetch(alignment, xy, 0);
    ivec4 xAlign = ivec4(xyAlign % 256u) - 128;
    ivec4 yAlign = ivec4(xyAlign / 256u) - 128;

    ivec2 xyFrame = xy * TILE_SCALE;
    ivec2 xyRef;
    float refDataVal;
    vec4 altDataVal, noisef;
    vec4 currXYNoise = vec4(0.f);

    for (int y = 0; y < TILE_SIZE; y++) {
        for (int x = 0; x < TILE_SIZE; x++) {
            // Use a bayer pattern to speed up this comparison.
            if ((x + y) % 2 == 1) {
                continue;
            }

            xyRef = xyFrame + ivec2(x, y) - TILE_OFFSET;
            refDataVal = texelFetch(refFrame, xyRef, 0).x;
            altDataVal.x = texelFetch(altFrame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x;
            altDataVal.y = texelFetch(altFrame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y;
            altDataVal.z = texelFetch(altFrame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z;
            altDataVal.w = texelFetch(altFrame, xyRef + ivec2(xAlign.w, yAlign.w), 0).w;

            // All frame data is loaded, compare reference frame with other frames.
            // Penalize noise with x^1.5 error model.
            noisef = abs(altDataVal - refDataVal);
            currXYNoise += noisef * sqrt(noisef);
        }
    }

    result = 1.f - smoothstep(vec4(MIN_NOISE), vec4(MAX_NOISE), currXYNoise);
}
