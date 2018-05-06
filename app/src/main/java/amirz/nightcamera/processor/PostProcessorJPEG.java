package amirz.nightcamera.processor;

import android.support.media.ExifInterface;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.server.CameraServer;

public class PostProcessorJPEG extends PostProcessor {
    public PostProcessorJPEG(CameraServer.CameraStreamFormat cameraFormatSize) {
        super(cameraFormatSize);
    }

    @Override
    public String[] processToFiles(ImageData[] images) {
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
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(mDevice.getExifRotation(mStreamFormat.id, rotate)));
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String[] { mediaFile };
    }
}
