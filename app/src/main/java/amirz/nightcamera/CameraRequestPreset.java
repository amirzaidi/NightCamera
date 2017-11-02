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

        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -1); //80% brightness
        builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);

        return builder.build();
    }

    public static CaptureRequest yuvZsl(CameraDevice cameraDevice, Surface previewSurface, Surface zslSurface) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        builder.addTarget(previewSurface); //preview screen
        builder.addTarget(zslSurface); //ZSL saver

        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);

        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -2); //63% brightness

        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_PRESET_CURVE);
        builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CaptureRequest.TONEMAP_PRESET_CURVE_SRGB);

        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);


        return builder.build();
    }
}
