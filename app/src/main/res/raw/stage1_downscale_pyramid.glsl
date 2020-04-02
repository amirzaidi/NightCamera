#version 300 es

precision mediump float;

uniform usampler2D buf;
uniform ivec2 bufSize;

uniform int stride;
uniform float sigma;

out uint result;

float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter *= stride;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - stride);
    ivec2 maxxy = min(bufSize - 1, xyCenter + stride);

    float I = 0.f;
    float W = 0.f;

    for (int y = minxy.y; y <= maxxy.y; y++) {
        for (int x = minxy.x; x <= maxxy.x; x++) {
            ivec2 xyPixel = ivec2(x, y);
            float z = float(texelFetch(buf, xyPixel, 0).x);
            float scale = unscaledGaussian(length(vec2(xyPixel - xyCenter)), sigma);
            I += z * scale;
            W += scale;
        }
    }

    result = uint(I / W);
}
