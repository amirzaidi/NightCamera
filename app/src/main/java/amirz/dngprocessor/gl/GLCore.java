package amirz.dngprocessor.gl;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;

import java.nio.ByteBuffer;

import static android.opengl.EGL14.*;
import static android.opengl.GLES20.GL_UNSIGNED_SHORT;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES30.GL_RED_INTEGER;

public class GLCore implements AutoCloseable {
    private static final String TAG = "GLCore";

    public final int width, height;

    private final EGLDisplay mDisplay;
    private final EGLContext mContext;
    private final EGLSurface mSurface;
    private final GLPrograms mProgram;

    public GLCore(int surfaceWidth, int surfaceHeight, ShaderLoader loader) {
        width = surfaceWidth;
        height = surfaceHeight;

        int[] major = new int[2];
        int[] minor = new int[2];

        // No error checking performed, minimum required code to elucidate logic
        mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(mDisplay, major, 0, minor, 0);

        int[] attribList2 = {
                EGL_DEPTH_SIZE, 0,
                EGL_STENCIL_SIZE, 0,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_BIND_TO_TEXTURE_RGBA, EGL_TRUE,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };

        // No error checking performed, minimum required code to elucidate logic
        // Expand on this logic to be more selective in choosing a configuration
        int[] numConfig = new int[1];
        if (!eglChooseConfig(mDisplay, attribList2, 0,
                null, 0, 0, numConfig, 0)
                || numConfig[0] == 0) {
            throw new RuntimeException("OpenGL config count zero");
        }

        int configSize = numConfig[0];
        EGLConfig[] configs = new EGLConfig[configSize];
        if (!eglChooseConfig(mDisplay, attribList2, 0,
                configs, 0, configSize, numConfig, 0)) {
            throw new RuntimeException("OpenGL config loading failed");
        }

        if (configs[0] == null) {
            throw new RuntimeException("OpenGL config is null");
        }

        mContext = eglCreateContext(mDisplay, configs[0], EGL_NO_CONTEXT, new int[] {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE
        }, 0);

        mSurface = eglCreatePbufferSurface(mDisplay, configs[0], new int[] {
                EGL_WIDTH, width,
                EGL_HEIGHT, height,
                EGL_NONE
        }, 0);

        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
        mProgram = new GLPrograms(loader);
    }

    public ByteBuffer resultBuffer() {
        ByteBuffer out = ByteBuffer.allocate(width * height * 2);
        glReadPixels(0, 0, width, height, GL_RED_INTEGER, GL_UNSIGNED_SHORT, out);
        return (ByteBuffer) out.flip();
    }

    public GLPrograms getProgram() {
        return mProgram;
    }

    @Override
    public void close() {
        Log.d(TAG, "Closing program");
        mProgram.close();

        Log.d(TAG, "Making NO_SURFACE Current");
        eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

        Log.d(TAG, "Destroying Context");
        eglDestroyContext(mDisplay, mContext);

        Log.d(TAG, "Destroying Surface");
        eglDestroySurface(mDisplay, mSurface);

        Log.d(TAG, "Terminating Display");
        eglTerminate(mDisplay);
    }
}
