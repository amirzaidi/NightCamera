package amirz.nightcamera.processor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.media.ExifInterface;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.server.CameraServer;

public class PostProcessorYUV extends PostProcessor {
    private static final int CPUs = Runtime.getRuntime().availableProcessors();
    private static ExecutorService executor = Executors.newFixedThreadPool(CPUs);

    public PostProcessorYUV(CameraServer.CameraStreamFormat cameraFormatSize) {
        super(cameraFormatSize);
    }

    @Override
    public String[] processToFiles(ImageData[] images) {
        final int rotate = images[images.length - 1].motion.mRot;

        final int width = images[0].image.getWidth();
        final int height = images[0].image.getHeight();

        if (true) {
            ByteBuffer yBuffer = images[0].buffer(0);
            ByteBuffer uBuffer = images[0].buffer(1);
            ByteBuffer vBuffer = images[0].buffer(2);
            int yRowStride = images[0].image.getPlanes()[0].getRowStride();
            int uvRowStride = images[0].image.getPlanes()[1].getRowStride();
            byte[] nv21 = new byte[(yRowStride + 2 * uvRowStride) * height];
            yBuffer.get(nv21, 0, yBuffer.remaining());
            vBuffer.get(nv21, yRowStride * height, vBuffer.remaining()); //U and V are swapped
            uBuffer.get(nv21, (yRowStride + uvRowStride) * height, uBuffer.remaining());

            String mediaFile = getSavePath("jpeg");
            try {
                FileOutputStream fos = new FileOutputStream(mediaFile);
                YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, new int[] { yRowStride, uvRowStride });
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, fos);
                fos.close();

                ExifInterface exif = new ExifInterface(mediaFile);
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(mDevice.getExifRotation(mStreamFormat.id, rotate)));
                exif.saveAttributes();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return new String[] { mediaFile };
        }

        //Something weird near the edges
        final int cutOff = 32;

        ByteBuffer[][] buffers = new ByteBuffer[images.length][];
        for (int i = 0; i < images.length; i++)
            buffers[i] = new ByteBuffer[] { images[i].buffer(0), images[i].buffer(1), images[i].buffer(2) };

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

                    LowBound = -Math.min(R, Math.min(G, B));
                    HighBound = 0xFF - Math.max(R, Math.max(G, B));
                    if (LowBound > HighBound)
                        throw new RuntimeException("Colour conversion");
                } else
                    Cb = Cr = 0; //Reset for next loop

                Y = clip(Y / images.length, LowBound, HighBound);
                out[x] = Color.argb(0xFF, (int)(Y + R), (int)(Y + G), (int)(Y + B));
            }

            bm.setPixels(out, 0, width - cutOff, 0, y, width - cutOff, 1);
        }

        //bm = matrixAndDispose(bm, blurMatrix);
        //bm = matrixAndDispose(bm, sharpenMatrix);

        String mediaFile = getSavePath("jpeg");
        try {
            FileOutputStream fos = new FileOutputStream(mediaFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            bm.recycle();
            fos.close();

            ExifInterface exif = new ExifInterface(mediaFile);
            //exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rotate)));
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new String[] { mediaFile };
    }

    private static int clip(double in, double low, double high) {
        return (int)Math.min(Math.max(in, low), high);
    }

    private final static float[] sharpenMatrix = {
            0, -1.0f, 0,
            -1.0f, 5.0f, -1.0f,
            0, -1.0f, 0
    };

    private final static float[] blurMatrix = {
            1f/25, 1f/25, 1f/25, 1f/25, 1f/25,
            1f/25, 1f/25, 1f/25, 1f/25, 1f/25,
            1f/25, 1f/25, 1f/25, 1f/25, 1f/25,
            1f/25, 1f/25, 1f/25, 1f/25, 1f/25,
            1f/25, 1f/25, 1f/25, 1f/25, 1f/25
    };

    /*public Bitmap matrixAndDispose(Bitmap in, float[] matrix) {
        Bitmap bitmap = Bitmap.createBitmap(in.getWidth(), in.getHeight(), Bitmap.Config.ARGB_8888);
        RenderScript rs = RenderScript.create(activity);

        Allocation allocIn = Allocation.createFromBitmap(rs, in);
        Allocation allocOut = Allocation.createFromBitmap(rs, bitmap);

        //ScriptIntrinsicConvolve5x5 convolution = ScriptIntrinsicConvolve5x5.create(rs, Element.U8_4(rs));
        ScriptIntrinsicConvolve3x3 convolution = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));
        convolution.setInput(allocIn);
        convolution.setCoefficients(matrix);
        convolution.forEach(allocOut);

        allocOut.copyTo(bitmap);
        rs.destroy();

        in.recycle();
        return bitmap;
    }*/
}