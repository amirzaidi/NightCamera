package amirz.nightcamera.gl;

import java.nio.ByteBuffer;

import amirz.nightcamera.gl.generic.GLCoreBase;
import amirz.nightcamera.gl.generic.GLProgramBase;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;

public class GLCore extends GLCoreBase {
    private final int mOutWidth, mOutHeight;

    public GLCore(int width, int height) {
        super(width, height);

        mOutWidth = width;
        mOutHeight = height;
    }

    public ByteBuffer resultBuffer() {
        ByteBuffer out = ByteBuffer.allocate(mOutWidth * mOutHeight * 2);
        glReadPixels(0, 0, mOutWidth, mOutHeight, GL_RED_INTEGER, GL_UNSIGNED_SHORT, out);
        return (ByteBuffer) out.flip();
    }

    @Override
    protected GLProgramBase createProgram() {
        return new GLProgram();
    }
}
