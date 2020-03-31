package amirz.nightcamera.ui;

import android.view.Surface;

import java.io.File;

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
    public MotionTracker getMotionTracker() {
        return mActivity.getMotionTracker();
    }

    @Override
    public void onCameraStartAvailable() {
        mActivity.runOnUiThread(mActivity::onCameraStartAvailable);
    }

    @Override
    public void onCameraStarted() {
        mActivity.runOnUiThread(mActivity::onCameraStarted);
    }

    @Override
    public void onCameraStopped() {
        mActivity.runOnUiThread(mActivity::onCameraStopped);
    }

    @Override
    public void onProcessingCount(final int count) {
        mActivity.runOnUiThread(() -> mActivity.onProcessingCount(count));
    }

    @Override
    public void onFocused() {
        mActivity.runOnUiThread(mActivity::onFocused);
    }

    @Override
    public void onTaken(final File[] paths) {
        mActivity.runOnUiThread(() -> mActivity.onTaken(paths));
    }
}
