package amirz.nightcamera.server;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import android.view.Surface;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.processor.PostProcessor;
import amirz.nightcamera.processor.PostProcessorRAW;
import amirz.nightcamera.zsl.CameraZSLQueue;

/**
 * Single use stream, that gets disposed after being closed
 */
public class CameraStream extends CameraDevice.StateCallback implements AutoCloseable {
    private final static String TAG = CameraStream.class.getName();

    private final DevicePreset mDevice;
    private final CameraZSLQueue mZSLQueue;
    private final CameraServer.CameraStreamFormat mStreamFormat;
    private final CameraStreamCallbacks mStreamCallbacks;

    private PostProcessor mProcessor;
    private AtomicInteger mProcessCounter = new AtomicInteger(0);

    private HandlerThread mThread;
    private Handler mHandler;

    private HandlerThread mProcessThread;
    private Handler mProcessHandler;

    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private boolean mCameraStreamStarted;

    private CaptureRequest mCurrentRequest;

    public CameraStream(CameraServer.CameraStreamFormat streamFormat, CameraStreamCallbacks cb) {
        mDevice = DevicePreset.getInstance();
        mZSLQueue = new CameraZSLQueue(streamFormat, cb.getMotionTracker());
        mStreamFormat = streamFormat;
        mStreamCallbacks = cb;

        switch (mStreamFormat.format) {
            case ImageFormat.RAW_SENSOR:
                mProcessor = new PostProcessorRAW(mStreamFormat);
                break;
            default:
                throw new RuntimeException("Format not available");
        }

        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());

        mProcessThread = new HandlerThread(TAG);
        mProcessThread.setPriority(Thread.MIN_PRIORITY);
        mProcessThread.start();
        mProcessHandler = new Handler(mProcessThread.getLooper());
    }

    @Override
    public void onOpened(@NonNull CameraDevice camera) {
        mCamera = camera;
        mStreamCallbacks.onCameraStartAvailable();
    }

    public void startAsync() throws CameraAccessException {
        if (mCamera == null) {
            throw new CameraServerException(CameraServerException.Problem.CameraDeviceNull);
        }

        Surface previewSurface = mStreamCallbacks.getPreviewSurface();
        Surface zslSurface = mZSLQueue.getReadSurface();
        mCamera.createCaptureSession(Arrays.asList(previewSurface, zslSurface),
                new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mSession = session;
                mCameraStreamStarted = true;
                refreshPreviewRequest();
                mStreamCallbacks.onCameraStarted();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                throw new CameraServerException(CameraServerException.Problem.ConfigureFailed);
            }
        }, mZSLQueue.mHandler);
    }

    private void refreshPreviewRequest() {
        if (!mCameraStreamStarted) {
            return;
        }

        try {
            Surface previewSurface = mStreamCallbacks.getPreviewSurface();
            Surface zslSurface = mZSLQueue.getReadSurface();
            final CaptureRequest request = mDevice.getParams(mStreamFormat, mCamera,
                    mZSLQueue.getLastResult(), previewSurface, zslSurface);

            if (!request.equals(mCurrentRequest)) {
                // Always stop before requesting a new stream.
                mSession.stopRepeating();
                mSession.setRepeatingRequest(request, mZSLQueue, mZSLQueue.mHandler);
                mCurrentRequest = request;
            }

            // Check for new request templates four times per second.
            mHandler.postDelayed(this::refreshPreviewRequest, 250);
        } catch (CameraAccessException ignored) {
        }
    }

    public void takeAndProcessAsync() {
        if (mProcessor != null) {
            final ImageData[] images = mZSLQueue.pullEntireQueue();
            if (images.length > 0) {
                mStreamCallbacks.onProcessingCount(mProcessCounter.incrementAndGet());
                mProcessHandler.post(() -> {
                    String[] files = mProcessor.processToFiles(images);
                    for (ImageData img : images) {
                        img.close();
                    }
                    mStreamCallbacks.onProcessingCount(mProcessCounter.decrementAndGet());
                    mStreamCallbacks.onTaken(files);
                });
            }
        }
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {
        close();
    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {
        close();
    }

    @Override
    public void close() {
        if (mCameraStreamStarted) {
            mCameraStreamStarted = false;
            mStreamCallbacks.onCameraStopped();
        }

        if (mSession != null) {
            try {
                mSession.stopRepeating();
                mSession.abortCaptures();
            } catch (CameraAccessException ignored) {
            }
            mSession.close();
            mSession = null;
        }

        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }

        if (mProcessThread != null) {
            mProcessThread.quitSafely();
            mProcessThread = null;
        }

        if (mThread != null) {
            mThread.quitSafely();
            mThread = null;
        }

        mZSLQueue.close();
    }
}
