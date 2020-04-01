#version 300 es

precision mediump float;

uniform usampler2D frame;
uniform int M, N;

uniform float dftSize;

out vec2 result;

void main() {
    ivec2 kl = ivec2(gl_FragCoord.xy);

    vec2 s = vec2(0.f);
    for (int n = 0; n < N; n++) {
        float ph1 = float(n * kl.y) / float(N);
        for (int m = 0; m < M; m++) {
            float mag = float(texelFetch(frame, ivec2(m, n), 0).x);
            float phase = float(m * kl.x) / float(M) + ph1;
            phase *= 6.28318530718f; // 2 * PI
            s += mag * vec2(cos(phase), sin(phase));
        }
    }

    s /= sqrt(float(M * N));
    float mag = length(s);
    float phase = acos(normalize(s).x);

    result = vec2(mag, phase);
}
