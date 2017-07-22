package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;

public class CaptureSettings {
    public static long exposureTime = 33333333L;
    public static long frameDuration = 33333333L;
    public static int sensitivity = 100;

    public static ArrayList<CaptureRequest> getCaptureRequests(CameraDevice cameraDevice, Surface surface) throws CameraAccessException {
        ArrayList<CaptureRequest> requests = new ArrayList<>();

        CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
        captureRequestBuilder.addTarget(surface);

        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);

        // 243613 33333330 100 (1/4000) <-> 62514864 62608560 799 (1/16) 799=reporting limit, goes to 6400
        if (FullscreenActivity.useCamera == 0 && sensitivity == 799) { //max darkness
            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);

            long useExposureTime = exposureTime * exposureTime / 10000000L;
            long useFrameDuration = frameDuration * frameDuration / 10000000L;

            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, useExposureTime);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, useFrameDuration);
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 3200);

            Log.d("PicSettings", "Using night mode - exposure " + useExposureTime / 1000000 + ", frame duration " + useFrameDuration / 1000000);
        } else {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);

            if (sensitivity < 120) {
                //HDR
            }
        }

        requests.add(captureRequestBuilder.build());

        return requests;
    }

    public static CaptureRequest.Builder getPreviewRequestBuilder(CameraDevice cameraDevice) throws CameraAccessException {
        CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

        if (FullscreenActivity.useCamera == 0)
            previewRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        return previewRequestBuilder;
    }
}
