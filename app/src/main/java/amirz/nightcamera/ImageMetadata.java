package amirz.nightcamera;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

public class ImageMetadata {
    public long time;
    public long frame;
    public CaptureRequest request;
    public TotalCaptureResult result;
    public MotionSnapshot motion;

    public ImageMetadata(long time, long frame, CaptureRequest request, TotalCaptureResult result, MotionSnapshot motion) {
        this.time = time;
        this.frame = frame;
        this.request = request;
        this.result = result;
        this.motion = motion;
    }
}
