/*package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class CaptureSettings {
    public static TotalCaptureResult previewResult;

    public static ArrayList<CaptureRequest> getCaptureRequests(CameraDevice cameraDevice, List<Surface> surfaces) throws CameraAccessException {
        ArrayList<CaptureRequest> requests = new ArrayList<>();

        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        for (Surface surface : surfaces)
            builder.addTarget(surface);

        //builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, (FullscreenActivity.useCamera == 0) ? CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON : CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
        setSaturation(builder, 4);

        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
        requests.add(builder.build());
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 12);
        requests.add(builder.build());
        requests.add(builder.build());

        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, 100);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 80L * 1000000);
        builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 80L * 1000000);
        builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 0);
        requests.add(builder.build());

        return requests;
    }

    private static void setSaturation(CaptureRequest.Builder builder, int saturation) {
        try {
            Class classInfo = CaptureRequest.Key.class;
            Constructor<?> construct = classInfo.getConstructor(String.class, Class.class);
            Object saturator = construct.newInstance("org.codeaurora.qcamera3.saturation.use_saturation", Integer.class);
            builder.set((CaptureRequest.Key<Integer>)saturator, saturation);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CaptureRequest.Builder getPreviewRequestBuilder(CameraDevice cameraDevice) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

        //builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, (FullscreenActivity.useCamera == 0) ? CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON : CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        //builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
        //builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        //builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
        //setSaturation(builder, 6);

        return builder;
    }
}
*/