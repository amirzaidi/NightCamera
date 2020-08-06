#version 300 es

#define FLT_MAX 3.402823466e+38

// Tiles of 16x16 with the middle 8x8 being the important region.
#define TILE_MIN_INDEX -4
#define TILE_MAX_INDEX 12

precision mediump float;

uniform sampler2D altFrame;
uniform ivec2 maxXY;
uniform ivec2 direction;

out vec4 result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec4 sum = vec4(0.f);
    // Use the same coordinate system as alignment stage.
    // Middle of the tile is 8x8, with 4 offset on all sides.
    for (int i = TILE_MIN_INDEX; i < TILE_MAX_INDEX; i++) {
        sum += texelFetch(altFrame, clamp(xy + i * direction, ivec2(0), maxXY), 0);
    }
    result = sum;
}
