package amirz.nightcamera;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;

public class ImageData {
    public CaptureRequest request;
    public TotalCaptureResult result;
    public long timestamp;
    public MotionSnapshot motion;
    public Image image;
    public boolean burstEnd = false;

    public void close() {
        if (image != null)
            image.close();
    }
}
