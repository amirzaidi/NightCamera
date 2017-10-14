package amirz.nightcamera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;

import java.util.Arrays;

public class CameraWrapper {
    private FullscreenActivity mActivity;

    private CameraManager cameraManager;
    private String[] cameras;
    private CameraCharacteristics[] cameraCharacteristics;
    private StreamConfigurationMap[] streamConfigurationMaps;
    private Size[] privateSizes;
    private Size[] yuvSizes;
    private Size[] jpegSizes;
    private Size[] rawSizes;

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

        streamConfigurationMaps = new StreamConfigurationMap[cameras.length];
        privateSizes = new Size[cameras.length];
        yuvSizes = new Size[cameras.length];
        jpegSizes = new Size[cameras.length];
        rawSizes = new Size[cameras.length];
        for (int i = 0; i < cameras.length; i++) {
            cameraCharacteristics[i] = cameraManager.getCameraCharacteristics(cameras[i]);
            streamConfigurationMaps[i] = cameraCharacteristics[i].get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            privateSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.PRIVATE)[0];
            yuvSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.YUV_420_888)[0];
            jpegSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.JPEG)[0];
            rawSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.RAW_SENSOR)[0];
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

                Size priv = privateSizes[useCamera];
                Size raw = rawSizes[useCamera];
                zslQueue = new CameraZSLQueue(mActivity, priv, raw);
                try {
                    cameraDevice.createReprocessableCaptureSession(
                            new InputConfiguration(priv.getWidth(), priv.getHeight(), ImageFormat.PRIVATE),
                            Arrays.asList(previewSurface, zslQueue.previewReadSurface(), zslQueue.reprocessedReadSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                    captureSession = cameraCaptureSession;
                                    zslQueue.startZSL(mActivity, captureSession);

                                    try {
                                        captureSession.stopRepeating();

                                        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
                                        builder.addTarget(previewSurface); //preview screen
                                        builder.addTarget(zslQueue.previewReadSurface()); //ZSL saver
                                        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, (useCamera == 0) ? CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON : CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

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
