package amirz.nightcamera;

import android.graphics.Bitmap;
import android.media.Image;
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
        int rotate = images[images.length - 1].motion.mRot;

        Image[] imgs = new Image[images.length];
        for (int i = 0; i < images.length; i++)
            imgs[i] = images[i].image;

        int width = imgs[0].getWidth();
        int height = imgs[0].getHeight();

        int rowStride = imgs[0].getPlanes()[0].getRowStride();

        ByteBuffer[][] buffers = new ByteBuffer[imgs.length][];
        for (int i = 0; i < imgs.length; i++) {
            Image.Plane[] planes = imgs[i].getPlanes();
            buffers[i] = new ByteBuffer[planes.length];

            for (int j = 0; j < planes.length; j++)
                buffers[i][j] = planes[j].getBuffer();
        }
        int buffer2limit = buffers[0][2].remaining();

        int Cr, Cb, tY, tCr = 0, tCb = 0, R, G, B;
        boolean evenX;

        //Start processing
        int rowSize = cameraFormatSize.size.getWidth();
        int[] out = new int[rowSize];

        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            int jDiv2 = y >> 1;
            for (int x = 0; x < width; x++) {
                evenX = (x & 0x1) == 0;
                tY = 0;
                if (evenX)
                    tCr = tCb = 0;

                for (int i = 0; i < imgs.length; i++) {
                    tY += 0xFF & buffers[i][0].get(y * rowStride + x);
                    if (evenX) {
                        int cOff = jDiv2 * rowStride + (x >> 1) * 2;
                        int buff = 2;
                        if (cOff >= buffer2limit) {
                            buff = 1;
                            cOff %= buffer2limit;
                        }
                        tCb += (0xFF & buffers[i][buff].get(cOff)) - 128;

                        buff = 2;
                        if (++cOff >= buffer2limit) {
                            buff = 1;
                            cOff %= buffer2limit;
                        }
                        tCr += (0xFF & buffers[i][buff].get(cOff)) - 128;
                    }
                }

                tY = tY * 1192 / imgs.length;
                Cb = tCb / imgs.length;
                Cr = tCr / imgs.length;

                //YCbCr to RGB
                R = Math.min(Math.max(tY + 2066*Cb >> 10, 0), 0xFF);
                G = Math.min(Math.max(tY - 833*Cr - 400*Cb >> 10, 0), 0xFF);
                B = Math.min(Math.max(tY + 1634*Cr >> 10, 0), 0xFF);

                out[x] = 0xFF000000 | (R << 16) | (G << 8) | B;
            }

            bm.setPixels(out, 0, width, 0, y, width, 1);
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

        Log.d("Yuv", "Saved");
        return new String[] { mediaFile };
    }
}
