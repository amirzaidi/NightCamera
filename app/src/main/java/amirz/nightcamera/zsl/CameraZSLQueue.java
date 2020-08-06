package amirz.nightcamera.zsl;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import android.view.Surface;

import amirz.nightcamera.motion.MotionSnapshot;
import amirz.nightcamera.motion.MotionTracker;
import amirz.nightcamera.server.CameraServer;

public class CameraZSLQueue extends CameraFramesSaver {
    private final static String TAG = CameraZSLQueue.class.getName();

    private TotalCaptureResult mLastResult;
    private ImageReader mSurfaceReader;
    private MotionTracker mMotionTracker;

    private HandlerThread mThread;
    public Handler mHandler;

    public CameraZSLQueue(CameraServer.CameraStreamFormat streamFormat, MotionTracker motionTracker) {
        // Temporary hardcode
        super(5, 2);

        mSurfaceReader = ImageReader.newInstance(
                streamFormat.size.getWidth(),
                streamFormat.size.getHeight(),
                streamFormat.format, 30);
        mSurfaceReader.setOnImageAvailableListener(this, mHandler);

        mMotionTracker = motionTracker;

        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
        mLastResult = result;
        super.onCaptureCompleted(session, request, result);
    }

    public TotalCaptureResult getLastResult() {
        return mLastResult;
    }

    public Surface getReadSurface() {
        return mSurfaceReader.getSurface();
    }

    @Override
    protected MotionSnapshot motionSnapshot(CaptureRequest request) {
        return mMotionTracker.snapshot();
    }

    public void close() {
        if (mSurfaceReader != null) {
            mSurfaceReader.close();
            mSurfaceReader = null;
        }

        mMotionTracker = null;
        mLastResult = null;

        mThread.quitSafely();
    }
}
