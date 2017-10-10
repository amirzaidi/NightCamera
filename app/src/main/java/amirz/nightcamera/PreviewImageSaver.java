package amirz.nightcamera;

import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class PreviewImageSaver implements ImageReader.OnImageAvailableListener {

    public MotionSnapshot motionStart;

    public LinkedList<Image> images = new LinkedList<>();
    public LinkedList<ImageMetadata> metadatas = new LinkedList<>();
    public Semaphore waiter = new Semaphore(1);
    public int count;

    private static int rowSize = 5000;
    private int[] out = new int[rowSize];

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static byte[] RGBMap = new byte[0x400];

    static {
        ORIENTATIONS.append(0, 90);
        ORIENTATIONS.append(90, 0);
        ORIENTATIONS.append(180, 270);
        ORIENTATIONS.append(270, 180);

        for (int i = 0; i < RGBMap.length; i++)
            RGBMap[i] = (byte)Math.floor(64 + 64 * Math.pow((double)i * 2 / RGBMap.length - 1, 3) + i / 8);
    }

    private MotionTracker mMotionTracker;
    private FullscreenActivity mActivity;

    private HandlerThread backgroundSaveThread;
    public Handler backgroundSaveHandler;

    private HandlerThread backgroundReprocessThread;
    public Handler backgroundReprocessHandler;

    public PreviewImageSaver(MotionTracker motionTracker, FullscreenActivity activity) {
        mMotionTracker = motionTracker;
        mActivity = activity;

        backgroundSaveThread = new HandlerThread("save");
        backgroundSaveThread.setPriority(Thread.MIN_PRIORITY);
        backgroundSaveThread.start();
        backgroundSaveHandler = new Handler(backgroundSaveThread.getLooper());

        backgroundReprocessThread = new HandlerThread("reprocess");
        backgroundReprocessThread.setPriority(Thread.MIN_PRIORITY);
        backgroundReprocessThread.start();
        backgroundReprocessHandler = new Handler(backgroundReprocessThread.getLooper());
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

    private static AtomicInteger counter = new AtomicInteger(0);
    private LinkedBlockingQueue<Image> queue = new LinkedBlockingQueue<>(Constants.zslImageReprocessCount);
    public TotalCaptureResult lastRes = null;
    public boolean requestedPic = false;

    @Override
    public void onImageAvailable(ImageReader reader) {
        try {
            Image newImg = reader.acquireLatestImage();
            if (newImg != null) {
                if (queue.remainingCapacity() == 0)
                    queue.remove().close();
                queue.add(newImg);
            }

            if (requestedPic) {
                requestedPic = false;
                final Image[] imgs = queue.toArray(new Image[queue.size()]);
                queue.clear();

                //Parallelize here
                backgroundSaveHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d("ImageAvailable", "Taking pic");

                            int rotate = mMotionTracker.getRotation();
                            if (mActivity.useCamera == 1 && rotate % 180 == 0)
                                rotate = 180 - rotate;
                            final int rot = rotate;

                            //decrement to keep track of processed images
                            final AtomicInteger countdown = new AtomicInteger(imgs.length);

                            CaptureRequest.Builder b = mActivity.cameraDevice.createReprocessCaptureRequest(lastRes);
                            b.addTarget(mActivity.reprocessedSaver.getSurface());

                            List<CaptureRequest> crs = new ArrayList<>();
                            CaptureRequest cr = b.build();
                            for (int i = imgs.length - 1; i >= 0; i--) {
                                crs.add(cr);
                                // Add all images for reprocessing in reverse order, newest is most important
                                mActivity.reprocessSurfaceWriter.queueInputImage(imgs[i]);
                            }

                            final Runnable onComplete = new Runnable() {
                                @Override
                                public void run() {
                                    Log.d("ImageAvailable", "Start real processing");

                                    //Process and save here
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

                                    Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                                    int[]   Ys = new int[imgs.length],
                                            Crs = new int[imgs.length],
                                            Cbs = new int[imgs.length];

                                    ByteBuffer[] imgBuff;
                                    int Y, Cr = 0, Cb = 0, B, G, R;
                                    boolean evenX;

                                    for (int y = 0; y < height; y++) {
                                        int jDiv2 = y >> 1;
                                        for (int x = 0; x < width; x++) {
                                            evenX = (x & 0x1) == 0;

                                            for (int i = 0; i < imgs.length; i++) {
                                                imgBuff = buffers[i];
                                                Ys[i] = 0xFF & imgBuff[0].get(y * rowStride + x);

                                                if (evenX) {
                                                    int cOff = jDiv2 * rowStride + (x >> 1) * 2;
                                                    int buff = 2;
                                                    if (cOff >= buffer2limit) {
                                                        buff = 1;
                                                        cOff -= buffer2limit;
                                                    }
                                                    Cbs[i] = (0xFF & imgBuff[buff].get(cOff)) - 128;

                                                    buff = 2;
                                                    if (++cOff >= buffer2limit) {
                                                        buff = 1;
                                                        cOff -= buffer2limit;
                                                    }
                                                    Crs[i] = (0xFF & imgBuff[buff].get(cOff)) - 128;
                                                }
                                            }

                                            Y = (3 * Ys[0] + Ys[1]) >> 2;

                                            //Medians
                                            if (evenX) {
                                                Arrays.sort(Cbs);
                                                Cb = Cbs[imgs.length / 2];

                                                Arrays.sort(Crs);
                                                Cr = Crs[imgs.length / 2];
                                            }

                                            //YCbCr to RGB
                                            R = 1192*Y + 2066*Cb;
                                            //G = 1192*Y - 833*Cr - 400*Cb;
                                            G = 1192*Y - 833*Cr - 400*Cb; //Better colour balance
                                            B = 1192*Y + 1634*Cr;

                                            R = Math.min(Math.max(R >> 10, 0), 0xFF);
                                            G = Math.min(Math.max(G >> 10, 0), 0xFF);
                                            B = Math.min(Math.max(B >> 10, 0), 0xFF);
                                            out[x] = 0xFF000000 | (R << 16) | (G << 8) | B;
                                        }

                                        bm.setPixels(out, 0, width, 0, y, width, 1);
                                    }

                                    Log.d("ImageAvailable", "Processing 2");

                                    for (int i = 0; i < imgs.length; i++)
                                        imgs[i].close();

                                    File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
                                    String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date()) + "_" + counter.incrementAndGet();
                                    File mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + ".jpg");

                                    try {
                                        FileOutputStream fos = new FileOutputStream(mediaFile);
                                        Log.d("ImageAvailable", "Saving");
                                        bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                        fos.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    SparseIntArray ORIENTATIONS = new SparseIntArray();
                                    ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_90);
                                    ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
                                    ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_270);
                                    ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);

                                    try {
                                        ExifInterface exif = new ExifInterface(mediaFile.getPath());
                                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ORIENTATIONS.get(rot)));
                                        exif.saveAttributes();

                                        MediaScannerConnection.scanFile(mActivity, new String[]{mediaFile.getPath()}, null, null);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    mActivity.afterCaptureAttempt();
                                    Log.d("ImageAvailable", "Finished");
                                }
                            };

                            // Handler for successful reprocess
                            mActivity.reprocessedSaver.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                                @Override
                                public void onImageAvailable(ImageReader imageReader) {
                                    Log.d("ImageAvailable", "Reprocessed Image");
                                    int index = countdown.decrementAndGet();
                                    imgs[index].close();
                                    imgs[index] = imageReader.acquireNextImage();
                                    if (index == 0)
                                        onComplete.run();
                                }
                            }, backgroundReprocessHandler);

                            // Handler for failed reprocess
                            CameraCaptureSession.CaptureCallback onFail = new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                                    super.onCaptureFailed(session, request, failure);
                                    throw new IllegalStateException("Reprocess Failure");
                                }
                            };

                            mActivity.captureSession.captureBurst(crs, onFail, backgroundReprocessHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
/*
            if (true) return;
            Image image = null;
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

                    Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Log.d("ImageAvailable", "Processing");

                    int[]   Ys = new int[imgs.length],
                            Crs = new int[imgs.length],
                            Cbs = new int[imgs.length];

                    ByteBuffer[] imgBuff;
                    int Y, Cr = 0, Cb = 0, B, G, R;
                    boolean evenX;

                    for (int y = 0; y < height; y++) {
                        int jDiv2 = y >> 1;
                        for (int x = 0; x < width; x++) {
                            evenX = (x & 0x1) == 0;

                            for (int i = 0; i < imgs.length; i++) {
                                imgBuff = buffers[i];
                                Ys[i] = 0xFF & imgBuff[0].get(y * rowStride + x);

                                if (evenX) {
                                    int cOff = jDiv2 * rowStride + (x >> 1) * 2;
                                    int buff = 2;
                                    if (cOff >= buffer2limit) {
                                        buff = 1;
                                        cOff -= buffer2limit;
                                    }
                                    Cbs[i] = (0xFF & imgBuff[buff].get(cOff)) - 128;

                                    buff = 2;
                                    if (++cOff >= buffer2limit) {
                                        buff = 1;
                                        cOff -= buffer2limit;
                                    }
                                    Crs[i] = (0xFF & imgBuff[buff].get(cOff)) - 128;
                                }
                            }

                            int lightFactor = Math.max(Math.min(Ys[0] * 10, 256), 0);
                            int darkFactor = 256 + 6 - lightFactor;

                            Y = (((Ys[1] + Ys[2]) * darkFactor + (Ys[3] + Ys[0]) * lightFactor) * 1192) / (512 + 12);

                            if (evenX) {
                                Arrays.sort(Cbs);
                                Arrays.sort(Crs);
                                Cb = (Cbs[1] + Cbs[2]) / 2;
                                Cr = (Crs[1] + Crs[2]) / 2;
                            }

                            //YCbCr to RGB
                            R = Y + 2066*Cb;
                            G = Y - 833*Cr - 400*Cb;
                            B = Y + 1634*Cr;

                            R = Math.min(Math.max(R >> 10, 0), 0xFF);
                            G = Math.min(Math.max(G >> 10, 0), 0xFF);
                            B = Math.min(Math.max(B >> 10, 0), 0xFF);
                            out[x] = 0xFF000000 | (R << 16) | (G << 8) | B;
                        }

                        bm.setPixels(out, 0, width, 0, y, width, 1);
                    }

                    Log.d("ImageAvailable", "Processing 2");

                    for (int i = 0; i < imgs.length; i++)
                        imgs[i].close();

                    File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
                    String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date()) + "_" + integer.incrementAndGet();
                    File mediaFile = new File(mediaStorageDir.getPath() + File.separator + timeStamp + ".jpg");

                    try {
                        FileOutputStream fos = new FileOutputStream(mediaFile);
                        Log.d("ImageAvailable", "Saving");
                        bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        fos.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

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
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
