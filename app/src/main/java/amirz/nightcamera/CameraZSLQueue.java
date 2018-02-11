package amirz.nightcamera;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

public class CameraZSLQueue extends CameraFramesSaver {
    private final static String TAG = "ZSLQueue";

    public final static int imageSaveCount = 35; //real buffer for previewSurfaceReader
    public final static int tempResultsBufferCount = 5; //intermediate to sync capture result and image buffer

    private ImageReader surfaceReader;
    private MotionTracker motionTracker;
    public PostProcessor processor;

    private HandlerThread thread;
    public Handler handler;

    private HandlerThread shutterThread;
    public Handler shutterHandler;

    public CameraZSLQueue(FullscreenActivity activity, int imageReprocessCount, CameraFormatSize cameraFormatSize, CameraCharacteristics cameraCharacteristics) {
        super(imageReprocessCount, tempResultsBufferCount);

        surfaceReader = ImageReader.newInstance(
                cameraFormatSize.size.getWidth(),
                cameraFormatSize.size.getHeight(),
                cameraFormatSize.format, imageSaveCount);
        surfaceReader.setOnImageAvailableListener(this, handler);

        motionTracker = activity.motionTracker;

        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());

        shutterThread = new HandlerThread(TAG + "Shutter");
        shutterThread.start();
        shutterHandler = new Handler(shutterThread.getLooper());

        if (cameraFormatSize.format == ImageFormat.RAW_SENSOR)
            processor = new PostProcessorRAW(activity, cameraFormatSize, cameraCharacteristics);
        else if (cameraFormatSize.format == ImageFormat.YUV_420_888)
            processor = new PostProcessorYUV(activity, cameraFormatSize);
        else if (cameraFormatSize.format == ImageFormat.JPEG)
            processor = new PostProcessorJPEG(activity, cameraFormatSize);
    }

    public Surface getReadSurface() {
        return surfaceReader.getSurface();
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
                    activity.toast("Processing");
                    processor.process(pullEntireQueue());
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
        if (surfaceReader != null) {
            surfaceReader.close();
            surfaceReader = null;
        }
        handler = null;
    }
}
