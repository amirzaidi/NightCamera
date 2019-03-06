package amirz.nightcamera.server;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
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
public class CameraStream extends CameraDevice.StateCallback {
    private final static String TAG = CameraStream.class.getName();

    private final DevicePreset mDevice;
    private final CameraZSLQueue mZSLQueue;
    private final CameraServer.CameraStreamFormat mStreamFormat;
    private final CameraStreamCallbacks mStreamCallbacks;

    private PostProcessor mProcessor;
    private AtomicInteger mProcessCounter = new AtomicInteger(0);

    private HandlerThread mThread;
    public Handler mHandler;

    private HandlerThread mProcessThread;
    public Handler mProcessHandler;

    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private boolean mPreviewActive;

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
        mCamera.createCaptureSession(Arrays.asList(previewSurface, zslSurface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mSession = session;
                mPreviewActive = true;
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
        if (mSession != null && mPreviewActive) {
            try {
                mSession.stopRepeating();
                Surface previewSurface = mStreamCallbacks.getPreviewSurface();
                Surface zslSurface = mZSLQueue.getReadSurface();
                final CaptureRequest request = mDevice.getParams(mStreamFormat.format, mCamera, mZSLQueue.getLastResult(), previewSurface, zslSurface);
                mSession.setRepeatingRequest(request, mZSLQueue, mZSLQueue.mHandler);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //refreshPreviewRequest();
                    }
                }, 1000); //Reset preview request every second
            } catch (CameraAccessException ignored) {
            }
        }
    }

    public void takeAndProcessAsync() {
        if (mProcessor != null) {
            final ImageData[] images = mZSLQueue.pullEntireQueue();
            if (images.length > 0) {
                mStreamCallbacks.onProcessingCount(mProcessCounter.incrementAndGet());
                mProcessHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String[] files = mProcessor.processToFiles(images);
                        for (ImageData img : images) {
                            img.close();
                        }
                        mStreamCallbacks.onProcessingCount(mProcessCounter.decrementAndGet());
                        mStreamCallbacks.onTaken(files);
                    }
                });
            }
        }
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {
        closeStream();
    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {
        closeStream();
    }

    public void closeStream() {
        if (mPreviewActive) {
            mPreviewActive = false;
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
