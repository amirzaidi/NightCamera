#version 300 es

precision mediump float;

uniform usampler2D raw;
uniform int cfa;

out float intermediate;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    uint result;
    for (int i = 0; i < 4; i++) {
        // Only use green channel, so check if this is not R or B
        if (i != cfa && i != 3 - cfa) {
            result += texelFetch(raw, xy + ivec2(i % 2, i / 2), 0).x;
        }
    }
    intermediate = float(result) / 2.f;
}
