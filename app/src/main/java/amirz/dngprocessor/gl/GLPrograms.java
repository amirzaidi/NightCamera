package amirz.dngprocessor.gl;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import amirz.dngprocessor.math.BlockDivider;
import amirz.nightcamera.R;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;

public class GLPrograms implements AutoCloseable {
    private final ByteBuffer mFlushBuffer = ByteBuffer.allocateDirect(4 * 4 * 4096);
    private final int vertexShader;

    private final ShaderLoader mShaderLoader;
    private final SquareModel mSquare = new SquareModel();
    private final Map<Integer, Integer> mPrograms = new HashMap<>();
    private int mProgramActive;

    public GLPrograms(ShaderLoader shaderLoader) {
        mShaderLoader = shaderLoader;
        vertexShader = loadShader(GL_VERTEX_SHADER, shaderLoader.readRaw(R.raw.passthrough_vs));
        mFlushBuffer.mark();
    }

    @SuppressWarnings("ConstantConditions")
    public void useProgram(int fragmentRes) {
        int program = mPrograms.containsKey(fragmentRes)
                ? mPrograms.get(fragmentRes)
                : createProgram(vertexShader, fragmentRes);

        glLinkProgram(program);
        glUseProgram(program);
        mProgramActive = program;
    }

    private int createProgram(int vertex, int fragmentRes) {
        int fragment;
        String fragmentCode = mShaderLoader.readRaw(fragmentRes);
        try {
            fragment = loadShader(GL_FRAGMENT_SHADER, fragmentCode);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error initializing fragment shader:\n" + fragmentCode, e);
        }

        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        mPrograms.put(fragmentRes, program);
        return program;
    }

    public void drawBlocks(Texture texture, int bh) {
        drawBlocks(texture, bh, false);
    }

    public void drawBlocks(Texture texture, int bh, boolean forceFlush) {
        texture.setFrameBuffer();
        drawBlocks(texture.getWidth(), texture.getHeight(), bh, -1, forceFlush ? texture.type() : -1);
    }

    public void drawBlocks(int w, int h, int bh, int flushFormat, int flushType) {
        mFlushBuffer.reset();
        if (flushFormat == -1) {
            flushFormat = flushType == GL_FLOAT ? GL_RGBA : GL_RGBA_INTEGER;
        }

        BlockDivider divider = new BlockDivider(h, bh);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            mSquare.draw(vPosition());

            // Force flush.
            glFlush();
            if (flushType != -1) {
                glReadPixels(0, row[0], 1, 1, flushFormat, flushType, mFlushBuffer);
                int glError = glGetError();
                if (glError != 0) {
                    Log.d("GLPrograms", "GLError: " + glError);
                }
            }
        }
    }

    @Override
    public void close() {
        // Clean everything up
        for (int program : mPrograms.values()) {
            glDeleteProgram(program);
        }
    }

    protected static int loadShader(int type, String shaderCode) {
        int shader = glCreateShader(type);
        glShaderSource(shader, shaderCode);
        glCompileShader(shader);

        int[] status = new int[1];
        glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL_FALSE) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(shader));
        }

        return shader;
    }

    private int vPosition() {
        return glGetAttribLocation(mProgramActive, "vPosition");
    }

    public void seti(String var, int... vals) {
        int loc = loc(var);
        switch (vals.length) {
            case 1: glUniform1i(loc, vals[0]); break;
            case 2: glUniform2i(loc, vals[0], vals[1]); break;
            case 3: glUniform3i(loc, vals[0], vals[1], vals[2]); break;
            case 4: glUniform4i(loc, vals[0], vals[1], vals[2], vals[3]); break;
            default: throw new RuntimeException("Cannot set " + var + " to " + Arrays.toString(vals));
        }
    }

    public void setui(String var, int... vals) {
        int loc = loc(var);
        switch (vals.length) {
            case 1: glUniform1ui(loc, vals[0]); break;
            case 2: glUniform2ui(loc, vals[0], vals[1]); break;
            case 3: glUniform3ui(loc, vals[0], vals[1], vals[2]); break;
            case 4: glUniform4ui(loc, vals[0], vals[1], vals[2], vals[3]); break;
            default: throw new RuntimeException("Cannot set " + var + " to " + Arrays.toString(vals));
        }
    }

    public void setf(String var, float... vals) {
        int loc = loc(var);
        switch (vals.length) {
            case 1: glUniform1f(loc, vals[0]); break;
            case 2: glUniform2f(loc, vals[0], vals[1]); break;
            case 3: glUniform3f(loc, vals[0], vals[1], vals[2]); break;
            case 4: glUniform4f(loc, vals[0], vals[1], vals[2], vals[3]); break;
            case 9: glUniformMatrix3fv(loc, 1, true, vals, 0); break;
            default: throw new RuntimeException("Cannot set " + var + " to " + Arrays.toString(vals));
        }
    }

    private int loc(String var) {
        return glGetUniformLocation(mProgramActive, var);
    }
}
