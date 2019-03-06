#version 300 es

precision mediump float;

uniform usampler2D centerFrame;
uniform usampler2D alignFrame;
uniform int width;
uniform int height;
uniform ivec2 alignment;

// Out
out int outFrame;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xy2 = xy + alignment;

    int center = int(texelFetch(centerFrame, xy, 0).x);
    if (xy2.x >= 0 && xy2.x < width && xy2.y >= 0 && xy2.y < height) {
        int aligned = int(texelFetch(alignFrame, xy2, 0).x);
        outFrame = (center + aligned) / 2;
    } else {
        outFrame = center;
    }
}
