package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;

public class ImagePreview implements ImageReader.OnImageAvailableListener {

    public static class ImageItem {
        public Image image;
        public TotalCaptureResult result;

        public ImageItem(Image image, TotalCaptureResult result) {
            this.image = image;
            this.result = result;
        }
    }

    public LinkedBlockingQueue<ImageItem> images = new LinkedBlockingQueue<>();
    public ImageWriter zslWriter;
    public TotalCaptureResult result;
    public boolean takeImage = false;

    private FullscreenActivity mActivity;

    public ImagePreview(FullscreenActivity activity) {
        mActivity = activity;
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireNextImage();

        if (result != null && takeImage) {
            takeImage = false;

            try {
                Log.e("Reprocess", result.get(CaptureResult.SENSOR_EXPOSURE_TIME) + " " + result.getFrameNumber() + " " + result.getSequenceId() + " " + image.getTimestamp() + " " + image.getPlanes().length);

                CaptureRequest.Builder builder = mActivity.cameraDevice.createReprocessCaptureRequest(result);
                builder.addTarget(mActivity.reprocessSurfaceReader.getSurface());
                zslWriter.queueInputImage(image);

                /*builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                        CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                builder.set(CaptureRequest.EDGE_MODE,
                        CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                builder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);

                item.image.close();*/

                mActivity.captureSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                        super.onCaptureStarted(session, request, timestamp, frameNumber);
                        Log.e("captureStart", "a");
                    }

                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        Log.e("captureComplete", "b");
                        //imagePreview.zslWriter.queueInputImage(item.image);
                        mActivity.afterCaptureAttempt();
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Image image = mActivity.reprocessSurfaceReader.acquireLatestImage();
                        Log.e("captureFail", "c " + failure.wasImageCaptured() + " " + (image != null));

                        //imagePreview.zslWriter.queueInputImage(item.image);
                        mActivity.afterCaptureAttempt();
                    }
                }, null);

            } catch (Exception e) {
                e.printStackTrace();
            }

            result = null;
        } else {
            image.close();
        }
    }

}
