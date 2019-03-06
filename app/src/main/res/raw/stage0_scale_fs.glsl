#version 300 es

precision mediump float;

uniform usampler2D raw;

out float intermediate;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    uint result;
    for (int i = 0; i < 4; i++) {
        result += texelFetch(raw, xy + ivec2(i % 2, i / 2), 0).x;
    }
    intermediate = float(result) / 4.f;
}
