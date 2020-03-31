package amirz.nightcamera.storage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageSaver {
    private static final String TAG = "ImageSaver";

    private static final int COPY_BUFFER_SIZE = 1024;

    private static final AtomicInteger sCounter = new AtomicInteger(0);
    private static String sLastFilename;

    private static final HandlerThread sSaveThread;
    private static final Handler sSaveHandler;

    static {
        sSaveThread =  new HandlerThread("SaveThread");
        sSaveThread.start();
        sSaveHandler = new Handler(sSaveThread.getLooper());
    }

    public static synchronized void moveToDCIMAsync(Context context, File[] paths) {
        sSaveHandler.post(() -> moveToDCIM(context.getApplicationContext(), paths));
    }

    private static void moveToDCIM(Context context, File[] paths) {
        String loc = Environment.DIRECTORY_DCIM;
        String baseFilename = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        String[] saved = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File file = paths[i];
            String path = Uri.fromFile(file).toString();
            saved[i] = baseFilename;
            if (saved[i].equals(sLastFilename)) {
                saved[i] += "_" + sCounter.incrementAndGet();
            } else {
                sLastFilename = baseFilename;
                sCounter.set(1);
            }
            saved[i] += ".dng";

            try {
                OutputStream fos;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = context.getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, saved[i]);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, new File(loc, "NightCamera").toString());
                    Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (imageUri == null) {
                        throw new IOException("ImageUri is null");
                    }
                    fos = resolver.openOutputStream(imageUri);
                    saved[i] = imageUri.toString();
                } else {
                    File folder = new File(Environment.getExternalStorageDirectory() + "/DCIM/NightCamera");
                    if (!folder.exists() && !folder.mkdir()) {
                        throw new IOException("Cannot create directory");
                    }
                    saved[i] = folder.getPath() + File.separator + saved[i];
                    fos = new FileOutputStream(saved[i]);
                }
                if (fos == null) {
                    throw new IOException("FileOutputStream is null");
                }
                copyTempFileToOutputStream(file, fos);
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Failed saving " + path);
            }
        }

        for (int i = 0; i < paths.length; i++) {
            Log.d(TAG, "Moved " + paths[i] + " to " + saved[i]);
            if (!paths[i].delete()) {
                Log.d(TAG, "Cannot delete " + paths[i]);
            }
        }

        MediaScannerConnection.scanFile(context, saved, null, (path, uri) -> {
            if (path != null) {
                Log.d(TAG, "Scanned " + path);
            }
        });
    }

    private static void copyTempFileToOutputStream(@NonNull File tempFile,
                                                   @NonNull OutputStream outputStream) throws IOException {
        try (InputStream in = new FileInputStream(tempFile)) {
            byte[] buf = new byte[COPY_BUFFER_SIZE];
            int len;
            while ((len = in.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
        }
    }
}
