package amirz.nightcamera;

import android.graphics.Bitmap;
import android.support.media.ExifInterface;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PostProcessorYUV extends PostProcessor {
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static { //Selfie orientation
        ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    public PostProcessorYUV(FullscreenActivity activity, CameraFormatSize cameraFormatSize) {
        super(activity, cameraFormatSize);
    }

    @Override
    protected String[] internalProcessAndSave(ImageData[] images) {
        final int rotate = images[images.length - 1].motion.mRot;

        final int width = images[0].image.getWidth();
        final int height = images[0].image.getHeight();

        //Something weird near the edges
        final int cutOff = 8;

        ByteBuffer[][] buffers = new ByteBuffer[images.length][];
        for (int i = 0; i < images.length; i++)
            buffers[i] = new ByteBuffer[] { images[i].plane(0), images[i].plane(1), images[i].plane(2) };

        int rowStride = images[0].image.getPlanes()[0].getRowStride();
        int buffer2limit = buffers[0][2].remaining();

        double R = 0, G = 0, B = 0, LowBound = 0, HighBound = 0, Y, Cb = 0, Cr = 0;
        boolean evenX;

        //Start processing
        Bitmap bm = Bitmap.createBitmap(width - cutOff, height - cutOff, Bitmap.Config.ARGB_8888);
        int[] out = new int[width];

        for (int y = 0; y < height - cutOff; y++) {
            int jDiv2 = y >> 1;
            for (int x = 0; x < width - cutOff; x++) {
                evenX = (x & 0x1) == 0;
                Y = 0;
                for (int i = 0; i < images.length; i++) {
                    Y += 0xFF & buffers[i][0].get(y * rowStride + x);
                    if (evenX) {
                        int cOff = jDiv2 * rowStride + (x >> 1) * 2;
                        int buff = 2;
                        if (cOff >= buffer2limit) {
                            buff = 1;
                            cOff %= buffer2limit;
                        }
                        Cb += 0xFF & buffers[i][buff].get(cOff);

                        buff = 2;
                        if (++cOff >= buffer2limit) {
                            buff = 1;
                            cOff %= buffer2limit;
                        }
                        Cr += 0xFF & buffers[i][buff].get(cOff);
                    }
                }

                if (evenX) {
                    Cb = Cb / images.length - 128;
                    Cr = Cr / images.length - 128;

                    R = 1.402 * Cb;
                    G = -0.34414 * Cr - 0.71414 * Cb;
                    B = 1.772 * Cr;

                    LowBound = Math.max(-R, Math.max(-G, -B));
                    HighBound = Math.min(255 + R, Math.min(255 + G, 255 + B));
                } else
                    Cb = Cr = 0; //Reset for next loop

                Y = Math.min(HighBound, Math.max(LowBound, Y / images.length));
                out[x] = 0xFF000000 | ((int)(Y + R) << 16) | ((int)(Y + G) << 8) | (int)(Y + B);
            }

            bm.setPixels(out, 0, width - cutOff, 0, y, width - cutOff, 1);
        }

        String mediaFile = getSavePath("jpeg");
        try {
            FileOutputStream fos = new FileOutputStream(mediaFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();

            ExifInterface exif = new ExifInterface(mediaFile);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rotate)));
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String[] { mediaFile };
    }
}