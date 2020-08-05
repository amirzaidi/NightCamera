#version 300 es

#define TILE_SIZE 8

#define MIN_NOISE 500.f
#define MAX_NOISE 750.f

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

    ivec2 xyFrame = xy * TILE_SIZE;
    ivec2 xyRef;
    float refDataVal;
    vec4 altDataVal, noisef;
    vec4 currXYNoise = vec4(0.f);

    for (int y = 0; y < TILE_SIZE; y++) {
        for (int x = 0; x < TILE_SIZE; x++) {
            xyRef = xyFrame + ivec2(x, y);
            refDataVal = texelFetch(refFrame, xyRef, 0).x;
            altDataVal.x = texelFetch(altFrame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x;
            altDataVal.y = texelFetch(altFrame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y;
            altDataVal.z = texelFetch(altFrame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z;
            altDataVal.w = texelFetch(altFrame, xyRef + ivec2(xAlign.w, yAlign.w), 0).w;

            // All frame data is loaded, compare reference frame with other frames.
            // Linear noise model.
            noisef = abs(altDataVal - refDataVal);
            currXYNoise += noisef;
        }
    }

    result = 1.f - smoothstep(MIN_NOISE, MAX_NOISE, currXYNoise);
}
