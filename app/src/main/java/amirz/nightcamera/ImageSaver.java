package amirz.nightcamera;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageSaver implements ImageReader.OnImageAvailableListener {

    //private byte[][] YuvBuffers = new byte[CaptureSettings.CAPTURE_ON_CLICK][];
    public MotionSnapshot motionStart;

    public LinkedList<Image> images = new LinkedList<>();
    public LinkedList<ImageMetadata> metadatas = new LinkedList<>();
    public Semaphore waiter = new Semaphore(1);
    public int count;

    private static int rowSize = 5000;
    private int[][][] roundBuf = new int[3][rowSize][3];
    private int[] out = new int[rowSize];

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(0, 90);
        ORIENTATIONS.append(90, 0);
        ORIENTATIONS.append(180, 270);
        ORIENTATIONS.append(270, 180);
    }

    private MotionTracker mMotionTracker;
    private FullscreenActivity mActivity;

    //public Handler backgroundCopyHandler;
    //private HandlerThread backgroundCopyThread;

    public Handler backgroundSaveHandler;
    private HandlerThread backgroundSaveThread;

    public ImageSaver(MotionTracker motionTracker, FullscreenActivity activity) {
        mMotionTracker = motionTracker;
        mActivity = activity;

        /*backgroundCopyThread = new HandlerThread("copy");
        backgroundCopyThread.start();
        backgroundCopyHandler = new Handler(backgroundSaveThread.getLooper());*/

        backgroundSaveThread = new HandlerThread("save");
        backgroundSaveThread.setPriority(Thread.MAX_PRIORITY);
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

    /*public void reset() {
        YuvBuffers = new byte[YuvBuffers.length][];
        mActivity.afterCaptureAttempt();
    }*/

    private AtomicInteger integer = new AtomicInteger(0);

    @Override
    public void onImageAvailable(ImageReader reader) {
        try {
            Image image = reader.acquireNextImage();
            if (image != null) {
                //track motion data

                images.add(image);
                if (images.size() == count) {
                    int rotate = mMotionTracker.getRotation();
                    if (mActivity.useCamera == 1 && rotate % 180 == 0)
                        rotate = 180 - rotate;

                    Image[] imgs = images.toArray(new Image[images.size()]);
                    images.clear();

                    waiter.acquireUninterruptibly();

                    ImageMetadata[] meta = metadatas.toArray(new ImageMetadata[metadatas.size()]);
                    metadatas.clear();

                    /*List<CaptureResult.Key<?>> keys = meta[0].result.getKeys();
                    for (CaptureResult.Key k : keys) {
                        Object o = meta[0].result.get(k);
                        if (o != null)
                            Log.d("Res", k.getName() + " " + o.toString());
                    }*/

                    int width = reader.getWidth();
                    int height = reader.getHeight();
                    int rowStride = image.getPlanes()[0].getRowStride();

                    ByteBuffer[][] buffers = new ByteBuffer[imgs.length][];
                    for (int i = 0; i < imgs.length; i++) {
                        Image.Plane[] planes = imgs[i].getPlanes();
                        buffers[i] = new ByteBuffer[planes.length];

                        for (int j = 0; j < planes.length; j++)
                            buffers[i][j] = planes[j].getBuffer();
                    }
                    int buffer2limit = buffers[0][2].remaining();

                    Bitmap bm = Bitmap.createBitmap(width - 2, height - 2, Bitmap.Config.ARGB_8888);
                    Log.d("ImageAvailable", "Processing");

                    int saturationFactor = 9 * imgs.length; //9=no, lower=stronger
                    int Y, Cr, Cb, tY, tCr = 0, tCb = 0;
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

                            //don't divide until processing is done
                            roundBuf[saveY][x][0] = tY;
                            roundBuf[saveY][x][1] = tCb;
                            roundBuf[saveY][x][2] = tCr;

                            if (saveY == 2 && x > 1) {
                                Y = roundBuf[1][x - 1][0];
                                Cb = roundBuf[1][x - 1][1];
                                Cr = roundBuf[1][x - 1][2];

                                //Sharpening and blur
                                if (false) { //if hdr
                                    Y *= 3;
                                    for (int dX = -1; dX <= 1; dX++)
                                        for (int dY = -1; dY <= 1; dY++)
                                            if ((dX | dY) != 0) {
                                                Y -= roundBuf[1 + dY][x + dX][0] >>> 2;
                                                Cb += roundBuf[1 + dY][x + dX][1] >>> 2;
                                                Cr += roundBuf[1 + dY][x + dX][2] >>> 2;
                                            }
                                    Cb /= 3;
                                    Cr /= 3;
                                } else {
                                    Y *= 2;
                                    for (int dX = -1; dX <= 1; dX++)
                                        for (int dY = -1; dY <= 1; dY++)
                                            if ((dX | dY) != 0) {
                                                Y -= roundBuf[1 + dY][x + dX][0] >>> 3;
                                            }
                                }

                                //Saturation
                                //Cb /= saturationFactor;
                                //Cr /= saturationFactor;

                                Cb /= imgs.length;
                                Cr /= imgs.length;

                                //Brightness clipping
                                Y = Y * 1192 / imgs.length;
                                out[x - 1] = 0xff000000
                                        | ((Math.max(0, Math.min(Y + 2066 * Cb, 262143)) << 6) & 0x00ff0000) //b
                                        | ((Math.max(0, Math.min(Y - 833 * Cr - 400 * Cb, 262143)) >> 2) & 0x0000ff00) //g
                                        | ((Math.max(0, Math.min(Y + 1634 * Cr, 262143)) >> 10) & 0x000000ff); //r
                            }
                        }

                        if (saveY == 2) {
                            bm.setPixels(out, 0, width - 2, 0, y - 2, width - 2, 1);
                            //Rotate buffers
                            int[][] temp = roundBuf[2];
                            roundBuf[2] = roundBuf[0];
                            roundBuf[0] = roundBuf[1];
                            roundBuf[1] = temp;
                        }
                    }

                    Log.d("ImageAvailable", "Processing 2");

                    for (int i = 0; i < imgs.length; i++)
                        imgs[i].close();

                    /*Bitmap bm2 = Bitmap.createScaledBitmap(bm, bm.getWidth() / 4, bm.getHeight() / 4, true);
                    bm.recycle();
                    bm = bm2;

                    Matrix matrix = new Matrix();
                    matrix.postRotate(ORIENTATIONS.get(rotate));
                    bm = Bitmap.createBitmap(bm2, 0, 0, bm2.getWidth(), bm2.getHeight(), matrix, false);
                    bm2.recycle();
                    */

                    File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
                    String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date()) + "_" + integer.incrementAndGet();
                    File mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + ".jpg");
                    //File mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + ".png");

                    try {
                        FileOutputStream fos = new FileOutputStream(mediaFile);
                        Log.d("ImageAvailable", "Saving");
                        bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        fos.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //bm2.recycle();

                    SparseIntArray ORIENTATIONS = new SparseIntArray();
                    ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_90);
                    ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
                    ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_270);
                    ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);

                    try {
                        ExifInterface exif = new ExifInterface(mediaFile.getPath());
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rotate)));
                        exif.saveAttributes();

                        MediaScannerConnection.scanFile(mActivity, new String[]{mediaFile.getPath()}, null, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Log.d("ImageAvailable", "Done");
                    waiter.release();
                    mActivity.afterCaptureAttempt();
                }

                /*
                if (true) return;

                final int width = reader.getWidth();
                final int height = reader.getHeight();

                Image.Plane[] planes = image.getPlanes();
                /*ByteBuffer buffer = planes[0].getBuffer();

                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                image.close();

                Log.d("ImageSaver", "available");
                mActivity.afterCaptureAttempt();
                if (true) return;*//*

                ByteBuffer yBuffer = planes[0].getBuffer();
                ByteBuffer uBuffer = planes[1].getBuffer();
                ByteBuffer vBuffer = planes[2].getBuffer();

                final int rowStride = planes[0].getRowStride();

                int i = 0;
                for (; i < CaptureSettings.CAPTURE_ON_CLICK; i++) {
                    if (YuvBuffers[i] == null) {
                        YuvBuffers[i] = new byte[3 * rowStride * height];
                        break;
                    }
                }

                yBuffer.get(YuvBuffers[i], 0, yBuffer.remaining());
                vBuffer.get(YuvBuffers[i], rowStride * height, vBuffer.remaining()); //U and V are swapped
                uBuffer.get(YuvBuffers[i], 2 * rowStride * height, uBuffer.remaining());

                image.close();

                Log.d("imageAvailable", "copied to buffer " + i);

                if (i == CaptureSettings.CAPTURE_ON_CLICK - 1) {
                    int rotate = mMotionTracker.getRotation();
                    if (mActivity.useCamera == 1 && rotate % 180 == 0)
                        rotate = 180 - rotate;

                    final int rotateInt = rotate;
                    final byte[][] buffers = YuvBuffers;
                    final MotionSnapshot motionEnd = mMotionTracker.snapshot();

                    backgroundSaveHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            double[] movementDiff = new double[3];
                            for (int i = 0; i < 3; i++)
                                movementDiff[i] = (double)motionEnd.mMovement[i] - (double)motionStart.mMovement[i];

                            double mov = Math.sqrt(Math.pow(movementDiff[0], 2) + Math.pow(movementDiff[1], 2) + Math.pow(movementDiff[2], 2));
                            int useBuffers = (int)Math.ceil(0.01d / mov);
                            if (useBuffers > buffers.length)
                                useBuffers = buffers.length;

                            Log.d("Process", "Start with buffercount " + useBuffers + " " + mov);
                            //int bufMaxIndex = useBuffers - 1;

                            Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            //int[] out = new int[width * height];
                            int[] out = new int[width];
                            int channelSize = rowStride * height;
                            int R, G, B, Y, Cr = 0, Cb = 0;

                            for (int y = 0; y < height; y++) {
                                final int jDiv2 = y >> 1;
                                for (int x = 0; x < width; x++) {
                                    R = 0; G = 0; B = 0;
                                    for (int i = 0; i < useBuffers; i++) {
                                        Y = buffers[i][y * rowStride + x];

                                        if (Y < 0)
                                            Y += 255;
                                        if ((x & 0x1) != 1) {
                                            final int cOff = channelSize + jDiv2 * rowStride + (x >> 1) * 2;

                                            Cb = buffers[i][cOff];
                                            if (Cb < 0)
                                                Cb += 127;
                                            else
                                                Cb -= 128;

                                            Cr = buffers[i][cOff + 1];
                                            if (Cr < 0)
                                                Cr += 127;
                                            else
                                                Cr -= 128;
                                        }

                                        R += Y + Cr + (Cr >> 2) + (Cr >> 3) + (Cr >> 5);

                                        G += Y - (Cb >> 2) + (Cb >> 4) + (Cb >> 5) - (Cr >> 1)
                                                + (Cr >> 3) + (Cr >> 4) + (Cr >> 5);

                                        B += Y + Cb + (Cb >> 1) + (Cb >> 2) + (Cb >> 6);
                                    }

                                    R /= useBuffers;
                                    G /= useBuffers;
                                    B /= useBuffers;
                                    //Apply colour correction curve

                                    if (R < 0)
                                        R = 0;
                                    else if (R > 255)
                                        R = 255;

                                    if (G < 0)
                                        G = 0;
                                    else if (G > 255)
                                        G = 255;

                                    if (B < 0)
                                        B = 0;
                                    else if (B > 255)
                                        B = 255;

                                    //out[y * width + x] = 0xff000000 + (B << 16) + (G << 8) + R;
                                    out[x] = 0xff000000 + (B << 16) + (G << 8) + R;
                                }

                                bm.setPixels(out, 0, width, 0, y, width, 1);
                            }

                            //Process out

                            //bm.setPixels(out, 0, width, 0, 0, width, height);

                            File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
                            String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date()) + "_" + integer.incrementAndGet();
                            File mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + ".jpg");

                            try {
                                FileOutputStream fos = new FileOutputStream(mediaFile);
                                Log.d("ImageAvailable", "Jpeg Compression");
                                bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                fos.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            bm.recycle();

                            SparseIntArray ORIENTATIONS = new SparseIntArray();
                            ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_90);
                            ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
                            ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_270);
                            ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);

                            try {
                                ExifInterface exif = new ExifInterface(mediaFile.getPath());
                                exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rotateInt)));
                                exif.saveAttributes();

                                MediaScannerConnection.scanFile(mActivity, new String[]{mediaFile.getPath()}, null, null);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            Log.d("Process", "Done");
                            reset();
                        }
                    });
                }

                /*
                YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, new int[] { yRowStride, uvRowStride });

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);

                byte[] imageBytes = out.toByteArray();
                Matrix matrix = new Matrix();
                matrix.postRotate(ORIENTATIONS.get(rotateInt));

                //Bitmap bm = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length), 2 * 1440, 2 * 1080, false);
                Bitmap bm = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, false);*/

                //do processing here

                /*File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
                String time = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date()) + "_" + integer.incrementAndGet();
                File mediaFile = new File(mediaStorageDir.getPath() + File.separator + time + ".jpg");

                try {
                    FileOutputStream fos = new FileOutputStream(mediaFile);
                    Log.d("ImageAvailable", "Jpeg Compression (again, sorry)");
                    fos.write(data);
                    //bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                SparseIntArray ORIENTATIONS = new SparseIntArray();
                ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_90);
                ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
                ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_270);
                ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);

                int rotateInt = mMotionTracker.getRotation();
                if (mActivity.useCamera == 1 && rotateInt % 180 == 0)
                    rotateInt = 180 - rotateInt;

                ExifInterface exif = new ExifInterface(mediaFile.getPath());
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rotateInt)));
                exif.saveAttributes();

                MediaScannerConnection.scanFile(mActivity, new String[] { mediaFile.getPath() }, null, null);
                Log.d("ImageAvailable", "Saved");

                mActivity.afterCaptureAttempt();*/
                //Show animation
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
