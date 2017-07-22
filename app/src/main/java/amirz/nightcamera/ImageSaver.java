package amirz.nightcamera;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.media.ExifInterface;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImageSaver implements ImageReader.OnImageAvailableListener {

    public static int MAX_IMAGES = 1;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    private MotionTracker mMotionTracker;

    public Handler backgroundSaveHandler;
    private HandlerThread backgroundSaveThread;

    public ImageSaver(MotionTracker motionTracker) {
        mMotionTracker = motionTracker;
        backgroundSaveThread = new HandlerThread("save");
        backgroundSaveThread.start();
        backgroundSaveHandler = new Handler(backgroundSaveThread.getLooper());
    }

    public void close() {
        backgroundSaveThread.quitSafely();

        try {
            backgroundSaveThread.join();
            backgroundSaveThread = null;
            backgroundSaveHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        try {
            Image image = reader.acquireNextImage();
            if (image != null) {
                int rotateInt = mMotionTracker.getRotation();
                if (FullscreenActivity.useCamera == 1 && rotateInt % 180 == 0)
                    rotateInt = 180 - rotateInt;

                final String rotate = String.valueOf(ORIENTATIONS.get(rotateInt));
                final int width = reader.getWidth();
                final int height = reader.getHeight();

                Log.d("ImageAvailable", "Available with rotation " + rotate);

                Image.Plane[] planes = image.getPlanes();

                ByteBuffer yBuffer = planes[0].getBuffer();
                ByteBuffer uBuffer = planes[1].getBuffer();
                ByteBuffer vBuffer = planes[2].getBuffer();

                final int yRowStride = planes[0].getRowStride();
                final int uvRowStride = planes[1].getRowStride();

                final byte[] nv21 = new byte[(yRowStride + 2 * uvRowStride) * height];

                yBuffer.get(nv21, 0, yBuffer.remaining());
                vBuffer.get(nv21, yRowStride * height, vBuffer.remaining()); //U and V are swapped
                uBuffer.get(nv21, (yRowStride + uvRowStride) * height, uBuffer.remaining());

                image.close();

                Log.d("ImageAvailable", "Background Processing");
                YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, new int[] { yRowStride, uvRowStride });

                File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
                String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
                File mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + ".jpg");
                try {
                    FileOutputStream fos = new FileOutputStream(mediaFile);
                    yuv.compressToJpeg(new Rect(0, 0, width, height), 100, fos);
                    fos.close();

                    ExifInterface exif = new ExifInterface(mediaFile.getPath());
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, rotate);
                    exif.saveAttributes();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Log.d("ImageAvailable", "Saved");
                //Show animation
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
