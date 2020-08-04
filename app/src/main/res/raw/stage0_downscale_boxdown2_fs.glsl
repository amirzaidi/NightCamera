#version 300 es

precision lowp float;

uniform usampler2D frame1;
uniform usampler2D frame2;
uniform usampler2D frame3;
uniform usampler2D frame4;

out uvec4 result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    uint val1 = texelFetch(frame1, xy, 0).x;
    val1 += texelFetch(frame1, xy + ivec2(1, 0), 0).x;
    val1 += texelFetch(frame1, xy + ivec2(0, 1), 0).x;
    val1 += texelFetch(frame1, xy + ivec2(1, 1), 0).x;

    uint val2 = texelFetch(frame2, xy, 0).x;
    val2 += texelFetch(frame2, xy + ivec2(1, 0), 0).x;
    val2 += texelFetch(frame2, xy + ivec2(0, 1), 0).x;
    val2 += texelFetch(frame2, xy + ivec2(1, 1), 0).x;

    uint val3 = texelFetch(frame3, xy, 0).x;
    val3 += texelFetch(frame3, xy + ivec2(1, 0), 0).x;
    val3 += texelFetch(frame3, xy + ivec2(0, 1), 0).x;
    val3 += texelFetch(frame3, xy + ivec2(1, 1), 0).x;

    uint val4 = texelFetch(frame4, xy, 0).x;
    val4 += texelFetch(frame4, xy + ivec2(1, 0), 0).x;
    val4 += texelFetch(frame4, xy + ivec2(0, 1), 0).x;
    val4 += texelFetch(frame4, xy + ivec2(1, 1), 0).x;

    result = uvec4(val1, val2, val3, val4) / 4u;
}
