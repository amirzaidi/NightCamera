package amirz.nightcamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.media.ExifInterface;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageProcessor extends CameraFramesSaver {

    private final static String TAG = "Processor";

    public ImageWriter reprocessSurfaceWriter;
    public ImageReader reprocessedReader;

    public final static int maxConcurrentProcessing = 3;
    public final static int maxConcurrentFrames = CameraZSLQueue.imageReprocessCount * maxConcurrentProcessing;
    public Semaphore waiter = new Semaphore(maxConcurrentProcessing - 1);

    private HandlerThread thread;
    public Handler handler;

    private FullscreenActivity activity;

    public ImageProcessor(FullscreenActivity activity, ImageReader reprocessedReader, Surface inputSurface) {
        super(maxConcurrentFrames, maxConcurrentFrames);
        this.activity = activity;

        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());

        this.reprocessedReader = reprocessedReader;
        this.reprocessedReader.setOnImageAvailableListener(this, handler);
        reprocessSurfaceWriter = ImageWriter.newInstance(inputSurface, CameraZSLQueue.imageReprocessCount * maxConcurrentProcessing + 1);
    }

    /*@Override
    public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
        Log.d(TAG, "Started");
        super.onCaptureStarted(session, request, timestamp, frameNumber);
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        Log.d(TAG, "Completed");
        super.onCaptureCompleted(session, request, result);
    }

    @Override
    public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
        Log.d(TAG, "Failed");
        super.onCaptureFailed(session, request, failure);
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Log.d(TAG, "Image Available");
        super.onImageAvailable(imageReader);
    }*/

    public void process(CameraWrapper camera, ImageData[] images) throws CameraAccessException {
        if (images.length != 0) {
            Log.d(TAG, "Processing");
            images[0].burstEnd = true; //first because of the inverted order
            List<CaptureRequest> crs = new ArrayList<>();

            for (int i = images.length - 1; i >= 0; i--) {
                CaptureRequest.Builder b = CaptureSettings.getReprocessRequestBuilder(camera.cameraDevice, images[i]);
                b.addTarget(reprocessedReader.getSurface());
                b.setTag(images[i]);
                crs.add(b.build());
                reprocessSurfaceWriter.queueInputImage(images[i].image);
                // Add all images for reprocessing in reverse order, newest is most important
            }

            //wait before requesting the burst now
            waiter.acquireUninterruptibly();
            camera.captureSession.captureBurst(crs, this, handler);
        }
    }

    public void close() {
        if (reprocessSurfaceWriter != null) {
            reprocessSurfaceWriter.close();
            reprocessSurfaceWriter = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
    }

    @Override
    protected void moveToQueue(ImageData data) {
        super.moveToQueue(data);
        if (((ImageData) data.request.getTag()).burstEnd) {
            Log.d(TAG, "Reprocess " + imageQueueCount());
            ImageData[] allFrames = pullEntireQueue();
            internalProcess(allFrames);
            for (ImageData img : allFrames)
                img.close();
            waiter.release();
        }
    }

    private AtomicInteger counter = new AtomicInteger(0);
    private void internalProcess(ImageData[] images) {
        int rotate = ((ImageData) images[images.length - 1].request.getTag()).motion.mRot;
        if (activity.useCamera == 1 && rotate % 180 == 0)
            rotate = 180 - rotate;
        final int rot = rotate;

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
        Log.d("ImageAvailable", "Processing");

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

            MediaScannerConnection.scanFile(activity, new String[] { mediaFile.getPath() }, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d("ImageAvailable", "Done");

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected MotionSnapshot motionSnapshot(CaptureRequest request) {
        return null;
    }
}
