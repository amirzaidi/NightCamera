package amirz.nightcamera.zsl;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import androidx.annotation.NonNull;

import java.util.LinkedList;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.motion.MotionSnapshot;

public abstract class CameraFramesSaver extends CameraCaptureSession.CaptureCallback
        implements ImageReader.OnImageAvailableListener {
    private int mImageReprocessCount;
    private int mTempResultsBufferCount;

    private final LinkedList<ImageData> imageQueue = new LinkedList<>();
    private final LinkedList<ImageData> tempResults = new LinkedList<>();

    CameraFramesSaver(int imageReprocessCount, int tempResultsBufferCount) {
        mImageReprocessCount = imageReprocessCount;
        mTempResultsBufferCount = tempResultsBufferCount;
    }

    @Override
    public synchronized void onCaptureStarted(@NonNull CameraCaptureSession session,
                                              @NonNull CaptureRequest request,
                                              long timestamp, long frameNumber) {
        for (ImageData temp : tempResults) {
            if (temp.timestamp == timestamp) {
                temp.motion = motionSnapshot(request);
                return;
            }
        }

        ImageData data = new ImageData();
        data.timestamp = timestamp;
        data.motion = motionSnapshot(request);

        tempResults.addLast(data);
        if (tempResults.size() > mTempResultsBufferCount) {
            tempResults.removeFirst().close();
        }
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureResult partialResult) {
    }

    @Override
    public synchronized void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull TotalCaptureResult result) {
        long stamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        for (ImageData temp : tempResults) {
            if (temp.timestamp == stamp) {
                temp.request = request;
                temp.result = result;
                if (temp.image != null) {
                    moveToQueue(temp);
                }
                break;
            }
        }
    }

    @Override
    public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                @NonNull CaptureRequest request,
                                @NonNull CaptureFailure failure) {
    }

    @Override
    public synchronized void onImageAvailable(ImageReader imageReader) {
        Image img = imageReader.acquireNextImage();
        if (img != null) {
            long stamp = img.getTimestamp();
            for (ImageData temp : tempResults) {
                if (temp.timestamp == stamp) {
                    temp.image = img;
                    if (temp.result != null) {
                        moveToQueue(temp);
                    }
                    return;
                }
            }

            ImageData temp = new ImageData();
            temp.timestamp = stamp;
            temp.image = img;
            tempResults.addLast(temp);
        }
    }

    private int imageQueueCount() {
        return imageQueue.size();
    }

    /* Always in synchronized imageQueue */
    private void moveToQueue(ImageData temp) {
        tempResults.remove(temp);
        imageQueue.addLast(temp);
        if (imageQueueCount() > mImageReprocessCount) {
            imageQueue.removeFirst().close();
        }
    }

    public synchronized ImageData[] pullEntireQueue() {
        ImageData[] images = imageQueue.toArray(new ImageData[imageQueueCount()]);
        imageQueue.clear();
        return images;
    }

    protected abstract MotionSnapshot motionSnapshot(CaptureRequest request);
}
