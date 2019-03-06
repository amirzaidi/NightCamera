package amirz.nightcamera.device;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.support.media.ExifInterface;
import android.util.SparseIntArray;

import amirz.nightcamera.processor.PostProcessorRAW;

public class OnePlus3 extends DevicePreset {
    private static SparseIntArray ORIENTATIONS_0 = new SparseIntArray();
    static { //Regular camera
        ORIENTATIONS_0.append(0, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS_0.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS_0.append(180, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS_0.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    private static SparseIntArray ORIENTATIONS_1 = new SparseIntArray();
    static { //Selfie camera
        ORIENTATIONS_1.append(0, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS_1.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS_1.append(180, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS_1.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    @Override
    protected void setRawParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result) {
        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        boolean lowLight = result != null && result.get(CaptureResult.SENSOR_SENSITIVITY) > 750;

        int exposureCompensation = 0;
        switch (id) {
            case "0":
                //exposureCompensation = -1;
                break;
            case "1":
                //exposureCompensation = -3;
                break;
        }

        if (lowLight) {
            android.util.Log.i("RawParams", "Low Light");
            exposureCompensation += 6;
        } else {
            android.util.Log.i("RawParams", "Regular Light");
        }

        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation);
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
    }

    @Override
    protected void setYuvParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result) {
    }

    @Override
    protected void setJpegParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result) {
    }

    @Override
    public int getExifRotation(String id, int rot) {
        switch (id) {
            case "0":
                return ORIENTATIONS_0.get(rot);
            case "1":
                return ORIENTATIONS_1.get(rot);
        }
        return 0;
    }
}
