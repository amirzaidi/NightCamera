package amirz.nightcamera;

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
    public static long exposureTime = 33333333L;
    public static long frameDuration = 33333333L;
    public static int sensitivity = 100;
    public static int CAPTURE_ON_CLICK = 5;
    public static TotalCaptureResult previewResult;

    public static ArrayList<CaptureRequest> getCaptureRequests(CameraDevice cameraDevice, List<Surface> surfaces) throws CameraAccessException {
        ArrayList<CaptureRequest> requests = new ArrayList<>();

        //CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        for (Surface surface : surfaces)
            builder.addTarget(surface);

        int sensitivity = previewResult.get(CaptureResult.SENSOR_SENSITIVITY);
        int sensitivityBoost = previewResult.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST); //558, 979, 1135

        //builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        //builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);


        // 243613 33333330 100 (1/4000) <-> 62514864 62608560 799 (1/16) 799=reporting limit, goes to 6400
        /*if (false && FullscreenActivity.useCamera == 0 && sensitivity == 799) { //max darkness

            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);

            long useExposureTime = exposureTime * exposureTime / 20000000L;
            long useFrameDuration = frameDuration * frameDuration / 20000000L;

            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, useExposureTime);
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, useFrameDuration);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 3200);


            requests.add(builder.build());
        } else {*/

        if (FullscreenActivity.useCamera == 0)
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        else
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);

            /*List<CaptureResult.Key<?>> keys = previewResult.getKeys();
            for (CaptureResult.Key<?> key : keys) {
                Object o = previewResult.get(key);
                Log.d("ObjO", "");
            }*/

            Log.d("ModeBoost", sensitivity + " " + sensitivityBoost);
            if (sensitivity < 799 || sensitivityBoost < 250) {
                Log.d("Mode", "HDR");
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                setSaturation(builder, 5);

                int count = sensitivity < 400 ? 1 : 2;

                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -12);
                for (int i = 0; i < count; i++)
                    requests.add(builder.build());

                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 12);
                for (int i = 0; i < count; i++)
                    requests.add(builder.build());
            } else {
                Log.d("Mode", "Low light");
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                setSaturation(builder, 6);

                int startSensitivity = 200;
                int startSensitivityBoost = sensitivityBoost / 5;
                long shutterGrow = 100L;

                if (startSensitivityBoost > 175) {
                    Log.d("Mode", "Even lower light");
                    startSensitivity *= 2;
                    startSensitivityBoost = 175;
                    shutterGrow = 125L;
                }

                for (int i = 0; i < 3; i++) {
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, startSensitivity * (1 << (2 * i)));
                    builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 100 + startSensitivityBoost * i); //100 to 3199 | up to 450 here

                    long exp = (50L + shutterGrow * i) * 1000000;
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp);
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, exp);

                    requests.add(builder.build());
                }
            }


            /*requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -3);
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -6);
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -9);
            requests.add(builder.build());

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -12);
            requests.add(builder.build());*/
            //cut off towards the end on motion

            //for (int exposureCompensation = -12; exposureCompensation <= 12; exposureCompensation += 6) { //sorta HDR
                //builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation);
            //}
        //}


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
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        //if (FullscreenActivity.useCamera == 0)
        //    builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        //builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
        //builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        //builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));

        return builder;
    }
}
