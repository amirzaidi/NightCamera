#version 300 es

precision mediump float;

uniform usampler2D buf1;
uniform usampler2D buf2;

uniform ivec2 maxXy;
uniform ivec2 offset;
uniform int stride;

out vec4 diff;

float getDiff(ivec2 xy) {
    // Stride handles skips for when the inputs are larger than the outputs.
    // In this case we only sample instead of covering the entire input.
    int z1 = int(texelFetch(buf1, clamp(stride * xy, ivec2(0, 0), maxXy), 0).x);
    int z2 = int(texelFetch(buf2, clamp(stride * xy + offset, ivec2(0, 0), maxXy), 0).x);
    float f = float(abs(z2 - z1));
    return f * f; // Square error
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy.x *= 4; // Do four at a time.
    // Act as if the shader is getting called 4 times from different coordinates.
    // Whatever happens at those coordinates will happen here too.
    diff.x = getDiff(xy);
    diff.y = getDiff(xy + ivec2(1, 0));
    diff.z = getDiff(xy + ivec2(2, 0));
    diff.w = getDiff(xy + ivec2(3, 0));
}
