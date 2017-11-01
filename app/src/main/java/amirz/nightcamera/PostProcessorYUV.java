package amirz.nightcamera;

import android.graphics.Bitmap;
import android.media.Image;
import android.support.media.ExifInterface;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PostProcessorYUV extends PostProcessor {
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    public PostProcessorYUV(FullscreenActivity activity) {
        super(activity);
    }

    @Override
    protected String[] internalProcessAndSave(ImageData[] images) {
        int rotate = images[images.length - 1].motion.mRot;

        Image[] imgs = new Image[images.length];
        for (int i = 0; i < images.length; i++)
            imgs[i] = images[i].image;

        int width = imgs[0].getWidth();
        int height = imgs[0].getHeight();

        int rowSize = 5000;
        int[][][] roundBuf = new int[3][rowSize][3];
        int[] out = new int[rowSize];

        int rowStride = imgs[0].getPlanes()[0].getRowStride();

        ByteBuffer[][] buffers = new ByteBuffer[imgs.length][];
        for (int i = 0; i < imgs.length; i++) {
            Image.Plane[] planes = imgs[i].getPlanes();
            buffers[i] = new ByteBuffer[planes.length];

            for (int j = 0; j < planes.length; j++)
                buffers[i][j] = planes[j].getBuffer();
        }
        int buffer2limit = buffers[0][2].remaining();

        Bitmap bm = Bitmap.createBitmap(width - 2, height - 2, Bitmap.Config.ARGB_8888);

        int Y, Cr, Cb, tY, tCr = 0, tCb = 0, R, G, B;
        boolean evenX;

        for (int y = 0; y < height; y++) {
            int saveY = y > 2 ? 2 : y;
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
                            cOff -= buffer2limit;
                        }

                        tCb += (0xFF & buffers[i][buff].get(cOff)) - 128;

                        buff = 2;
                        if (++cOff >= buffer2limit) {
                            buff = 1;
                            cOff -= buffer2limit;
                        }

                        tCr += (0xFF & buffers[i][buff].get(cOff)) - 128;
                    }
                }

                roundBuf[saveY][x][0] = tY;
                roundBuf[saveY][x][1] = tCb;
                roundBuf[saveY][x][2] = tCr;

                if (saveY == 2 && x > 1) {
                    Y = roundBuf[1][x - 1][0];
                    Cb = 0;
                    Cr = 0;

                    Y *= 2;
                    for (int dX = -1; dX <= 1; dX++)
                        for (int dY = -1; dY <= 1; dY++)
                            if ((dX | dY) != 0) {
                                Y -= roundBuf[1 + dY][x + dX][0] >>> 3;
                                Cb += roundBuf[1 + dY][x + dX][1];
                                Cr += roundBuf[1 + dY][x + dX][2];
                            }
                    Cb /= 8;
                    Cr /= 8;

                    Y /= imgs.length;
                    Cb /= imgs.length;
                    Cr /= imgs.length;

                    //YCbCr to RGB
                    R = 1192*Y + 2066*Cb;
                    G = 1192*Y - 833*Cr - 400*Cb;
                    B = 1192*Y + 1634*Cr;

                    R = Math.min(Math.max(R >> 10, 0), 0xFF);
                    G = Math.min(Math.max(G >> 10, 0), 0xFF);
                    B = Math.min(Math.max(B >> 10, 0), 0xFF);

                    out[x] = 0xFF000000 | (R << 16) | (G << 8) | B;
                }
            }
            if (saveY == 2) {
                bm.setPixels(out, 0, width - 2, 0, y - 2, width - 2, 1);
                int[][] temp = roundBuf[2];
                roundBuf[2] = roundBuf[0];
                roundBuf[0] = roundBuf[1];
                roundBuf[1] = temp;
            }
        }

        String mediaFile = getSavePath("jpg");
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
