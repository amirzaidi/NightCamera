package amirz.nightcamera.device;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;
import android.view.Surface;

public abstract class DevicePreset {
    public static DevicePreset getInstance() {
        switch (Build.MODEL) {
            case "ONEPLUS A3000":
            case "ONEPLUS A3003":
                return new OnePlus3();
            default:
                return null;
        }
    }

    public final CaptureRequest getParams(int format, CameraDevice cameraDevice, TotalCaptureResult result, Surface previewSurface, Surface zslSurface) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        String id = cameraDevice.getId();
        switch (format) {
            case ImageFormat.RAW_SENSOR:
                setRawParams(id, builder, result);
                break;
            case ImageFormat.YUV_420_888:
                setYuvParams(id, builder, result);
                break;
            case ImageFormat.JPEG:
                setJpegParams(id, builder, result);
                break;
        }
        builder.addTarget(previewSurface);
        builder.addTarget(zslSurface);
        return builder.build();
    }

    protected abstract void setRawParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result);

    protected abstract void setYuvParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result);

    protected abstract void setJpegParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result);

    public abstract int getExifRotation(String id, int rot);
}
