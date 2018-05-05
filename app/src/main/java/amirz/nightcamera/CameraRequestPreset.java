package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.view.Surface;

public class CameraRequestPreset {
    public static CaptureRequest rawZsl(CameraDevice cameraDevice, Surface previewSurface, Surface zslSurface) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        builder.addTarget(previewSurface); //preview screen
        builder.addTarget(zslSurface); //ZSL saver

        //standard(builder);

        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        //builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -1); //80% brightness, reduce highlights
        //builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 6);

        //builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON); //Raw data

        return builder.build();
    }

    public static CaptureRequest yuvZsl(CameraDevice cameraDevice, Surface previewSurface, Surface zslSurface) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        builder.addTarget(previewSurface); //preview screen
        builder.addTarget(zslSurface); //ZSL saver

        standard(builder);

        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -2); //65% brightness, reduce highlights

        return builder.build();
    }

    private static void standard(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY); //Massive impact on chromatic noise

        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY); //Idk
        builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY); //Idk

        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR); //Set to HDR

        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
        builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB); //Map to sRGB

        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY); //Idk
    }
}
