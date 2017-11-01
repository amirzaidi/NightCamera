package amirz.nightcamera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Range;
import android.view.Surface;

import java.util.Arrays;

public class CameraWrapper {
    private FullscreenActivity mActivity;

    private CameraManager cameraManager;
    private String[] cameras;
    private CameraCharacteristics[] cameraCharacteristics;
    private CameraFormatSize[] cameraFormatSizes;

    public CameraDevice cameraDevice;
    public CameraCaptureSession captureSession;

    public CameraZSLQueue zslQueue;

    private HandlerThread thread;
    private Handler handler;

    public CameraWrapper(FullscreenActivity activity) throws CameraAccessException {
        mActivity = activity;
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        cameras = cameraManager.getCameraIdList();
        cameraCharacteristics = new CameraCharacteristics[cameras.length];

        cameraFormatSizes = new CameraFormatSize[cameras.length];
        for (int i = 0; i < cameras.length; i++) {
            cameraCharacteristics[i] = cameraManager.getCameraCharacteristics(cameras[i]);

            CameraFormatSize cfs = new CameraFormatSize();
            cfs.format = i == 0 ? ImageFormat.RAW_SENSOR : ImageFormat.YUV_420_888;
            cfs.size = cameraCharacteristics[i].get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(cfs.format)[0];
            cameraFormatSizes[i] = cfs;
        }

        thread = new HandlerThread("camera");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void openCamera(final int useCamera, final Surface previewSurface) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    openCameraInternal(useCamera, previewSurface);
                } catch (SecurityException e) {
                    e.printStackTrace();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void openCameraInternal(final int useCamera, final Surface previewSurface) throws CameraAccessException, SecurityException {
        closeCamera();
        cameraManager.openCamera(cameras[useCamera], new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice camera) {
                cameraDevice = camera;
                zslQueue = new CameraZSLQueue(mActivity, cameraFormatSizes[useCamera], cameraCharacteristics[useCamera]);
                try {
                    cameraDevice.createCaptureSession(
                            Arrays.asList(previewSurface, zslQueue.getReadSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                    captureSession = cameraCaptureSession;
                                    try {
                                        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
                                        builder.addTarget(previewSurface); //preview screen
                                        builder.addTarget(zslQueue.getReadSurface()); //ZSL saver
                                        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 1 - useCamera);

                                        captureSession.setRepeatingRequest(builder.build(), zslQueue, zslQueue.handler);
                                        mActivity.setUIEnabled(true);
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                }
                            }, null);
                }
                catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                closeCamera();
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                closeCamera();
            }
        }, null);
    }

    public void closeCamera() {
        if (zslQueue != null) {
            zslQueue.close();
            zslQueue = null;
        }

        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public void close() {
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
    }
}
