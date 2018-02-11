package amirz.nightcamera;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;

import java.nio.ByteBuffer;

public class ImageData {
    public CaptureRequest request;
    public TotalCaptureResult result;
    public long timestamp;
    public MotionSnapshot motion;
    public Image image;

    public void close() {
        if (image != null)
            image.close();
    }

    public Image.Plane plane(int number) {
        return image.getPlanes()[number];
    }

    public ByteBuffer buffer(int number) {
        return plane(number).getBuffer();
    }
}
