package amirz.nightcamera.server;

import android.renderscript.RenderScript;
import android.view.Surface;

import amirz.nightcamera.motion.MotionTracker;

public interface CameraStreamCallbacks {
    Surface getPreviewSurface();

    RenderScript getRsInstance();

    MotionTracker getMotionTracker();

    void onCameraStartAvailable();

    void onCameraStarted();

    void onCameraStopped();

    void onProcessingCount(int count);

    void onFocused();

    void onTaken(String[] paths);
}
