package amirz.nightcamera.server;

import android.view.Surface;

import java.io.File;

import amirz.nightcamera.motion.MotionTracker;

public interface CameraStreamCallbacks {
    Surface getPreviewSurface();

    MotionTracker getMotionTracker();

    void onCameraStartAvailable();

    void onCameraStarted();

    void onCameraStopped();

    void onProcessingCount(int count);

    void onFocused();

    void onTaken(File[] paths);
}
