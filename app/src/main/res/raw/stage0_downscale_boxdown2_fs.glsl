#version 300 es

precision lowp float;

uniform usampler2D frame;

out uint result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    uint val = texelFetch(frame, xy, 0).x;
    val += texelFetch(frame, xy + ivec2(1, 0), 0).x;
    val += texelFetch(frame, xy + ivec2(0, 1), 0).x;
    val += texelFetch(frame, xy + ivec2(1, 1), 0).x;

    result = val / 4u;
}
