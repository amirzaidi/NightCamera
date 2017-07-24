package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public class CaptureSettings {
    public static long exposureTime = 33333333L;
    public static long frameDuration = 33333333L;
    public static int sensitivity = 100;
    public static int CAPTURE_ON_CLICK = 5;

    public static ArrayList<CaptureRequest> getCaptureRequests(CameraDevice cameraDevice, List<Surface> surfaces) throws CameraAccessException {
        ArrayList<CaptureRequest> requests = new ArrayList<>();

        //CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        for (Surface surface : surfaces)
            builder.addTarget(surface);

        //builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        //builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        // 243613 33333330 100 (1/4000) <-> 62514864 62608560 799 (1/16) 799=reporting limit, goes to 6400
        if (false && FullscreenActivity.useCamera == 0 && sensitivity == 799) { //max darkness

            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);

            long useExposureTime = exposureTime * exposureTime / 20000000L;
            long useFrameDuration = frameDuration * frameDuration / 20000000L;

            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, useExposureTime);
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, useFrameDuration);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 3200);

            Log.d("PicSettings", "Using night mode - exposure " + useExposureTime / 1000000 + ", frame duration " + useFrameDuration / 1000000);

            requests.add(builder.build());
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);

            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);


            //builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -12);
            //for (int i = 0; i < 5; i++)
            //    requests.add(builder.build());
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 6);
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -6);
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 12);
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -12);
            requests.add(builder.build());
            //cut off towards the end on motion

            //for (int exposureCompensation = -12; exposureCompensation <= 12; exposureCompensation += 6) { //sorta HDR
                //builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation);
            //}
        }


        return requests;
    }

    public static CaptureRequest.Builder getPreviewRequestBuilder(CameraDevice cameraDevice) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

        //if (FullscreenActivity.useCamera == 0)
        //    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        //builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
        //builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        //builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(15, 15));

        return builder;
    }
}
