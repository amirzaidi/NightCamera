package amirz.nightcamera.gl;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import amirz.nightcamera.gl.generic.GLProgramBase;
import amirz.nightcamera.gl.generic.GLSquare;
import amirz.nightcamera.gl.generic.GLTex;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

public class GLProgram extends GLProgramBase {
    private static final String TAG = "GLProgram";

    private final GLSquare mSquare = new GLSquare();
    private final int mProgramScale;
    private final int mProgramAlign;
    private final int mProgramMerge;

    private final int[] fbo = new int[1];
    private GLTex mCenterFrame, mAlignFrame;

    public GLProgram() {
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, fbo, 0);

        int vertexShader = loadShader(GL_VERTEX_SHADER, Shaders.VS);

        mProgramScale = createProgram(vertexShader, Shaders.FS_SCALE);
        mProgramAlign = createProgram(vertexShader, Shaders.FS_ALIGN);
        mProgramMerge = createProgram(vertexShader, Shaders.FS_MERGE);
    }

    public GLTex frameToTexture(ByteBuffer frame, int width, int height) {
        return new GLTex(width, height, 1, GLTex.Format.UInt16, frame);
    }

    public GLTex alignAndMerge(GLTex centerFrame, GLTex alignFrame, int width, int height) {
        useProgram(mProgramScale);
        seti("raw", 0);

        glViewport(0, 0, width, height);

        mCenterFrame = centerFrame;
        mCenterFrame.bind(GL_TEXTURE0);

        GLTex centerFrameScaled = new GLTex(width / 2, height / 2, 1, GLTex.Format.Float16, null);
        centerFrameScaled.setFrameBuffer();
        draw();

        mAlignFrame = alignFrame;
        mAlignFrame.bind(GL_TEXTURE0);

        GLTex alignFrameScaled = new GLTex(width / 2, height / 2, 1, GLTex.Format.Float16, null);
        alignFrameScaled.setFrameBuffer();
        draw();

        useProgram(mProgramAlign);

        seti("centerFrame", 0);
        centerFrameScaled.bind(GL_TEXTURE0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);

        seti("alignFrame", 2);
        alignFrameScaled.bind(GL_TEXTURE2);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);

        GLTex alignTex = new GLTex(width / 2, height / 2, 4, GLTex.Format.UInt16, null);
        alignTex.setFrameBuffer();

        int[] alignment = new int[2];

        final int maxLod = 3;
        seti("MaxLOD", maxLod);

        int maxLodWidth = width >> maxLod;
        int maxLodHeight = height >> maxLod;

        ShortBuffer buffer = ShortBuffer.allocate((maxLodWidth - 2) * (maxLodHeight - 2) * 4);
        glViewport(0, 0, maxLodWidth - 2, maxLodHeight - 2);

        int lod = maxLod;
        while (true) {
            alignment[0] *= 2;
            alignment[1] *= 2;
            if (lod == 0) break;

            double[][] counts = new double[2][3];

            seti("LOD", lod);
            seti("alignment", alignment);

            draw();
            glReadPixels(0, 0, maxLodWidth - 2, maxLodHeight - 2, GL_RGBA_INTEGER, GL_UNSIGNED_SHORT, buffer);

            for (int i = 0; i < (maxLodWidth - 2) * (maxLodHeight - 2); i += 4) {
                if (buffer.get(i + 3) == 1) {
                    int x = buffer.get(i);
                    int y = buffer.get(i + 1);
                    double diff = buffer.get(i + 2);
                    counts[0][x + 1] += diff;
                    counts[1][y + 1] += diff;
                }
            }

            //Log.d(TAG, "Counts " + Arrays.toString(counts[0]) + " " + Arrays.toString(counts[1]));

            for (int c = 0; c < 2; c++) {
                if (counts[c][0] > counts[c][1] && counts[c][0] > counts[c][2]) {
                    alignment[c]--;
                } else if (counts[c][2] > counts[c][1] && counts[c][2] > counts[c][0]) {
                    alignment[c]++;
                }
            }

            lod--;
        }

        Log.d(TAG, "Alignment " + Arrays.toString(alignment));

        centerFrameScaled.delete();
        alignFrameScaled.delete();

        useProgram(mProgramMerge);

        seti("centerFrame", 0);
        mCenterFrame.bind(GL_TEXTURE0);

        seti("alignFrame", 2);
        mAlignFrame.bind(GL_TEXTURE2);

        seti("alignment", alignment);
        seti("width", width);
        seti("height", height);

        GLTex out = new GLTex(width, height, 1, GLTex.Format.UInt16, null);
        out.setFrameBuffer();

        glViewport(0, 0, width, height);
        draw();

        mCenterFrame.delete();
        mAlignFrame.delete();

        Log.d(TAG, "Merged");
        return out;
    }

    private void draw() {
        mSquare.draw(vPosition());
        glFlush();
    }
}
