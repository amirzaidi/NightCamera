#version 300 es

precision mediump float;

uniform usampler2D frame;
uniform vec2 freq;

out vec2 dft;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    dft = vec2(0.5f, 0.5f);
}
