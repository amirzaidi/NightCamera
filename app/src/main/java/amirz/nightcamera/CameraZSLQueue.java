package amirz.nightcamera;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;

public class CameraZSLQueue extends CameraFramesSaver {
    private final static String TAG = "ZSLQueue";

    public final static int imageSaveCount = 35; //real buffer for previewSurfaceReader

    public final static int imageReprocessCount = 1; //50-100ms of data to reprocess
    public final static int tempResultsBufferCount = 5; //intermediate to sync capture result and image buffer

    private ImageReader previewSurfaceReader;
    private ImageReader reprocessedReader;

    private MotionTracker motionTracker;

    public ImageProcessor processor;

    private HandlerThread thread;
    public Handler handler;

    private HandlerThread shutterThread;
    public Handler shutterHandler;

    public CameraZSLQueue(FullscreenActivity activity, Size priv, Size raw) {
        super(imageReprocessCount, tempResultsBufferCount);
        previewSurfaceReader = ImageReader.newInstance(
                priv.getWidth(),
                priv.getHeight(),
                ImageFormat.PRIVATE, imageSaveCount);
        previewSurfaceReader.setOnImageAvailableListener(this, handler);

        /*reprocessedReader = ImageReader.newInstance(
                raw.getWidth(),
                raw.getHeight(),
                ImageFormat.RAW_SENSOR, imageReprocessCount + 1);*/

        reprocessedReader = ImageReader.newInstance(
                priv.getWidth(),
                priv.getHeight(),
                ImageFormat.YUV_420_888, imageReprocessCount + 1);

        motionTracker = activity.motionTracker;

        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());

        shutterThread = new HandlerThread(TAG + "shutter");
        shutterThread.start();
        shutterHandler = new Handler(shutterThread.getLooper());
    }

    public Surface previewReadSurface() {
        return previewSurfaceReader.getSurface();
    }

    public Surface reprocessedReadSurface() {
        return reprocessedReader.getSurface();
    }

    public void startZSL(FullscreenActivity activity, CameraCaptureSession captureSession) {
        processor = new ImageProcessor(activity, reprocessedReader, captureSession.getInputSurface());
    }

    @Override
    protected MotionSnapshot motionSnapshot(CaptureRequest request) {
        return motionTracker.snapshot();
    }

    public void takeAndProcessAsync(final FullscreenActivity activity) {
        shutterHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    processor.process(activity.camera, pullEntireQueue());
                    activity.setUIEnabled(true);
                }
                catch (CameraAccessException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    public void close() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        if (shutterThread != null) {
            shutterThread.quitSafely();
            shutterThread = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        if (reprocessedReader != null) {
            reprocessedReader.close();
            reprocessedReader = null;
        }
        if (previewSurfaceReader != null) {
            previewSurfaceReader.close();
            previewSurfaceReader = null;
        }
        handler = null;
    }
}
