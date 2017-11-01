package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PostProcessor {

    private final static String TAG = "Processor";

    public final static int maxConcurrentProcessing = 3;
    public Semaphore waiter = new Semaphore(maxConcurrentProcessing - 1);

    private AtomicInteger counter = new AtomicInteger(0);
    private HandlerThread thread;
    public Handler handler;

    protected FullscreenActivity activity;

    public PostProcessor(FullscreenActivity activity) {
        this.activity = activity;

        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void process(final ImageData[] images) throws CameraAccessException {
        if (images.length != 0) {
            waiter.acquireUninterruptibly();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    MediaScannerConnection.scanFile(activity, internalProcessAndSave(images), null, null);
                    for (ImageData img : images)
                        img.close();
                    waiter.release();
                    activity.toast("Saved");
                }
            });
        }
    }

    protected String getSavePath(String extention) {
        File folder = new File(Environment.getExternalStorageDirectory() + "/DCIM/NightCamera");
        if (!folder.exists() && !folder.mkdir())
            throw new RuntimeException("Cannot create /DCIM/NightCamera");
        return folder.getPath()+ File.separator + new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date()) + "_" + counter.incrementAndGet() + "." + extention;
    }

    protected abstract String[] internalProcessAndSave(ImageData[] images);

    public void close() {
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
    }
}
