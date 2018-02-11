package amirz.nightcamera;

import android.graphics.Bitmap;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.support.media.ExifInterface;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PostProcessorJPEG extends PostProcessor {
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static { //Regular camera
        ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    public PostProcessorJPEG(FullscreenActivity activity, CameraFormatSize cameraFormatSize) {
        super(activity, cameraFormatSize);
    }

    @Override
    protected String[] internalProcessAndSave(ImageData[] images) {
        int rotate = images[images.length - 1].motion.mRot;
        String mediaFile = getSavePath("jpeg");

        ByteBuffer buffer = images[images.length - 1].buffer(0);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        try {
            FileOutputStream output = new FileOutputStream(mediaFile);
            output.write(bytes);
            output.close();

            ExifInterface exif = new ExifInterface(mediaFile);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rotate)));
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String[] { mediaFile };
    }
}
