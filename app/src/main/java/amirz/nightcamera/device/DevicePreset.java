package amirz.nightcamera.device;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;
import androidx.exifinterface.media.ExifInterface;

import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.SparseIntArray;
import android.view.Surface;

import java.util.Objects;

import amirz.nightcamera.server.CameraServer;

public abstract class DevicePreset {
    private static final String TAG = "DevicePreset";

    private static final long DAY_EXPOSURE = (1000 * 1000000L) / 10;
    private static final long NIGHT_EXPOSURE = (1000 * 1000000L) / 4;

    private static DevicePreset sInstance;

    public static DevicePreset getInstance() {
        if (sInstance == null) {
            sInstance = getNewInstance();
        }
        return sInstance;
    }

    private static DevicePreset getNewInstance() {
        switch (Build.MODEL) {
            default:
                return new Generic();
        }
    }

    private static class Generic extends DevicePreset {
        private static SparseIntArray ORIENTATIONS_0 = new SparseIntArray();
        static { // Regular camera.
            ORIENTATIONS_0.append(0, ExifInterface.ORIENTATION_ROTATE_90);
            ORIENTATIONS_0.append(90, ExifInterface.ORIENTATION_NORMAL);
            ORIENTATIONS_0.append(180, ExifInterface.ORIENTATION_ROTATE_270);
            ORIENTATIONS_0.append(270, ExifInterface.ORIENTATION_ROTATE_180);
        }

        private static SparseIntArray ORIENTATIONS_1 = new SparseIntArray();
        static { // Selfie camera.
            ORIENTATIONS_1.append(0, ExifInterface.ORIENTATION_ROTATE_270);
            ORIENTATIONS_1.append(90, ExifInterface.ORIENTATION_NORMAL);
            ORIENTATIONS_1.append(180, ExifInterface.ORIENTATION_ROTATE_90);
            ORIENTATIONS_1.append(270, ExifInterface.ORIENTATION_ROTATE_180);
        }

        @Override
        protected void setRawParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result) {
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

    private int mMode;

    public void setExposureMode(int mode) {
        mMode = mode;
    }

    public int getMode() {
        return mMode;
    }

    public final CaptureRequest getParams(CameraServer.CameraStreamFormat stream,
                                          CameraDevice cameraDevice, TotalCaptureResult result,
                                          Surface previewSurface, Surface zslSurface) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        String id = cameraDevice.getId();
        switch (stream.format) {
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

        if (result != null) {
            int iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            long frametime = result.get(CaptureResult.SENSOR_FRAME_DURATION);
            long exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Log.v(TAG, "ISO " + iso + ", Frametime " + (frametime / 1000000f)
                    + " ms, Exposure " + (exposure  / 1000000f) + " ms");
        }

        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT);
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

        if (mMode == 0) { // Auto
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

            Rational aeStep = stream.characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            Range<Integer> aeRange = stream.characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            int steps = Math.round(0.333f / aeStep.floatValue());
            int aeCompensate = Math.max(aeRange.getLower(), -steps);
            aeCompensate = 0;

            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensate);
            builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);

            Range<Integer> fpsRange = null;
            for (Range<Integer> r : Objects.requireNonNull(stream.characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))) {
                if (fpsRange == null || r.getUpper() > fpsRange.getUpper()) {
                    fpsRange = r;
                }
            }
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        } else { // Manual
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f);

            int maxIso = stream.characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);
            if (mMode == 1) {
                builder.set(CaptureRequest.SENSOR_FRAME_DURATION, DAY_EXPOSURE);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, DAY_EXPOSURE);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, maxIso / 6);
            } else if (mMode == 2) {
                builder.set(CaptureRequest.SENSOR_FRAME_DURATION, NIGHT_EXPOSURE);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, NIGHT_EXPOSURE);
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, maxIso);
            }
        }

        builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, 0);
        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_MANUAL);

        return builder.build();
    }

    protected abstract void setRawParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result);

    protected abstract void setYuvParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result);

    protected abstract void setJpegParams(String id, CaptureRequest.Builder builder, TotalCaptureResult result);

    public abstract int getExifRotation(String id, int rot);
}
