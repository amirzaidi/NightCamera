#version 300 es

precision mediump float;

uniform usampler2D frame;

out uint result;

uint getVal(ivec2 xy) {
    uint val = texelFetch(frame, xy + ivec2(0, 0), 0).x;
    val += texelFetch(frame, xy + ivec2(1, 0), 0).x;
    val += texelFetch(frame, xy + ivec2(0, 1), 0).x;
    val += texelFetch(frame, xy + ivec2(1, 1), 0).x;
    return uint(val);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    result = getVal(xy * ivec2(2, 2));
}
