package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.TonemapCurve;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;

public class CaptureSettings {
    public static long exposureTime = 1000000000L / 30L;
    public static long frameDuration = 1000000000L / 30L;
    public static int sensitivity = 640;

    public static ArrayList<CaptureRequest> getCaptureRequests(CameraDevice cameraDevice, Surface surface) throws CameraAccessException {
        ArrayList<CaptureRequest> requests = new ArrayList<>();

        CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
        captureRequestBuilder.addTarget(surface);

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE);
        captureRequestBuilder.set(CaptureRequest.TONEMAP_CURVE, new TonemapCurve(
                new float[] { 0, 0, 0.3f, 0.5f, 0.7f, 0.8f, 1.0f, 1.0f },
                new float[] { 0, 0, 0.3f, 0.5f, 0.7f, 0.8f, 1.0f, 1.0f },
                new float[] { 0, 0, 0.3f, 0.5f, 0.7f, 0.8f, 1.0f, 1.0f }));

        //for (int i = 1; i <= 5; i++) {
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime * 3);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration * 3);
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 3200);

            requests.add(captureRequestBuilder.build());
            Log.d("r", 1000000000L / exposureTime + " " + sensitivity);
        //}

        return requests;
    }

    public static CaptureRequest.Builder getPreviewRequestBuilder(CameraDevice cameraDevice) throws CameraAccessException {
        CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        return previewRequestBuilder;
    }
}
