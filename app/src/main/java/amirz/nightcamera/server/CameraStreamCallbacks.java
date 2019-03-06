package amirz.nightcamera.server;

import android.view.Surface;

import amirz.nightcamera.motion.MotionTracker;

public interface CameraStreamCallbacks {
    Surface getPreviewSurface();

    MotionTracker getMotionTracker();

    void onCameraStartAvailable();

    void onCameraStarted();

    void onCameraStopped();

    void onProcessingCount(int count);

    void onFocused();

    void onTaken(String[] paths);
}
