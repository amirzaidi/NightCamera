#version 300 es

precision lowp float;

uniform usampler2D frame1;
uniform usampler2D frame2;
uniform usampler2D frame3;
uniform usampler2D frame4;

out vec4 result;

vec2 getValForTex(in usampler2D tex, ivec2 xy) {
    float topLeftAndBottomRight = float(
        texelFetch(tex, xy, 0).x
        + texelFetch(tex, xy + ivec2(1, 1), 0).x
    );
    float topRightAndBottomLeft = float(
        texelFetch(tex, xy + ivec2(1, 0), 0).x
        + texelFetch(tex, xy + ivec2(0, 1), 0).x
    );
    return vec2(topLeftAndBottomRight, topRightAndBottomLeft);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * 2;

    vec2 val1 = getValForTex(frame1, xy);
    vec2 val2 = getValForTex(frame2, xy);
    vec2 val3 = getValForTex(frame3, xy);
    vec2 val4 = getValForTex(frame4, xy);

    // Add weights using CFA later.
    result = (vec4(val1.x, val2.x, val3.x, val4.x)
        + vec4(val1.y, val2.y, val3.y, val4.y))/2.0;
}
