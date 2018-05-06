package amirz.nightcamera.ui;

import android.renderscript.RenderScript;
import android.view.Surface;

import amirz.nightcamera.FullscreenActivity;
import amirz.nightcamera.motion.MotionTracker;
import amirz.nightcamera.server.CameraStreamCallbacks;

public class MainThreadDelegate implements CameraStreamCallbacks {
    private final FullscreenActivity mActivity;

    public MainThreadDelegate(FullscreenActivity activity) {
        mActivity = activity;
    }

    @Override
    public Surface getPreviewSurface() {
        return mActivity.getPreviewSurface();
    }

    @Override
    public RenderScript getRsInstance() {
        return mActivity.getRsInstance();
    }

    @Override
    public MotionTracker getMotionTracker() {
        return mActivity.getMotionTracker();
    }

    @Override
    public void onCameraStartAvailable() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.onCameraStartAvailable();
            }
        });
    }

    @Override
    public void onCameraStarted() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.onCameraStarted();
            }
        });
    }

    @Override
    public void onCameraStopped() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.onCameraStopped();
            }
        });
    }

    @Override
    public void onProcessingCount(final int count) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.onProcessingCount(count);
            }
        });
    }

    @Override
    public void onFocused() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.onFocused();
            }
        });
    }

    @Override
    public void onTaken(final String[] paths) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.onTaken(paths);
            }
        });
    }
}
