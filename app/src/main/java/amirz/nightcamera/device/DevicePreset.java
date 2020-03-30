package amirz.nightcamera.device;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;
import android.util.Range;
import android.view.Surface;

import amirz.nightcamera.server.CameraServer;

public abstract class DevicePreset {
    private static DevicePreset sInstance;

    public static DevicePreset getInstance() {
        if (sInstance == null) {
            sInstance = getNewInstance();
        }
        return sInstance;
    }

    private static DevicePreset getNewInstance() {
        switch (Build.MODEL) {
            case "ONEPLUS A3000":
            case "ONEPLUS A3003":
            case "ONEPLUS A5000":
            case "ONEPLUS A5003":
                return new OnePlus3();
            default:
                return new Generic();
        }
    }

    private static class Generic extends DevicePreset {
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
            return 0;
        }
    }

    private boolean mBright;

    public void setBright(boolean isBright) {
        mBright = isBright;
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

        if (mBright) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f);

            Range<Integer> isoRange = stream.characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            Range<Integer> boostRange = stream.characteristics.get(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE);

            // 1/2s exposure time, at maximum ISO.
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 500 * 1000000L);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 500 * 1000000L);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoRange.getUpper());
            builder.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, boostRange.getLower());

        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
        }

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
