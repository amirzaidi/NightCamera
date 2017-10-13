package amirz.nightcamera;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import java.util.LinkedList;

public abstract class CameraFramesSaver extends CameraCaptureSession.CaptureCallback implements ImageReader.OnImageAvailableListener {

    private int imageReprocessCount;
    private int tempResultsBufferCount;

    private final LinkedList<ImageData> imageQueue = new LinkedList<>();
    private final LinkedList<ImageData> tempResults = new LinkedList<>();

    protected CameraFramesSaver(int imageReprocessCount, int tempResultsBufferCount) {
        this.imageReprocessCount = imageReprocessCount;
        this.tempResultsBufferCount = tempResultsBufferCount;
    }

    @Override
    public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
        synchronized (imageQueue) {
            for (ImageData temp : tempResults)
                if (temp.timestamp == timestamp) {
                    temp.motion = motionSnapshot(request);
                    return;
                }

            ImageData data = new ImageData();
            data.timestamp = timestamp;
            data.motion = motionSnapshot(request);

            tempResults.addLast(data);
            if (tempResults.size() > tempResultsBufferCount)
                tempResults.removeFirst().close();
        }
    }

    @Override
    public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        long stamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        synchronized (imageQueue) {
            for (ImageData temp : tempResults)
                if (temp.timestamp == stamp) {
                    temp.request = request;
                    temp.result = result;
                    if (temp.image != null)
                        moveToQueue(temp);
                    break;
                }
        }
    }

    @Override
    public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
        throw new RuntimeException("Capture Failed");
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image img = imageReader.acquireNextImage();
        if (img != null) {
            long stamp = img.getTimestamp();
            synchronized (imageQueue) {
                for (ImageData temp : tempResults)
                    if (temp.timestamp == stamp) {
                        temp.image = img;
                        if (temp.result != null)
                            moveToQueue(temp);
                        return;
                    }

                ImageData temp = new ImageData();
                temp.timestamp = stamp;
                temp.image = img;
                tempResults.addLast(temp);
            }
        } else {
            throw new RuntimeException("Null Image");
        }
    }

    protected int imageQueueCount() {
        return imageQueue.size();
    }

    /* Always in synchronized imageQueue */
    protected void moveToQueue(ImageData temp) {
        tempResults.remove(temp);
        imageQueue.addLast(temp);
        if (imageQueueCount() > imageReprocessCount)
            imageQueue.removeFirst().close();
    }

    protected ImageData[] pullEntireQueue() {
        synchronized (imageQueue) {
            ImageData[] images = imageQueue.toArray(new ImageData[imageQueueCount()]);
            imageQueue.clear();
            return images;
        }
    }

    protected abstract MotionSnapshot motionSnapshot(CaptureRequest request);
}
