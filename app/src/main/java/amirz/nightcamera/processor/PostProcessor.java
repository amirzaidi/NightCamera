package amirz.nightcamera.processor;

import android.annotation.SuppressLint;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.server.CameraServer;

public abstract class PostProcessor {
    CameraServer.CameraStreamFormat mStreamFormat;
    DevicePreset mDevice;
    private AtomicInteger counter = new AtomicInteger(0);
    private String mLastDate;

    PostProcessor(CameraServer.CameraStreamFormat streamFormat) {
        mStreamFormat = streamFormat;
        mDevice = DevicePreset.getInstance();
    }

    @SuppressLint("SimpleDateFormat")
    synchronized String getSavePath(String extension) {
        File folder = new File(Environment.getExternalStorageDirectory() + "/DCIM/NightCamera");
        if (!folder.exists() && !folder.mkdir())
            throw new RuntimeException("Cannot create /DCIM/NightCamera");
        String date = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (date.equals(mLastDate)) {
            date += "_" + counter.incrementAndGet();
        } else {
            mLastDate = date;
            counter.set(1);
        }
        return folder.getPath() + File.separator + "IMG_" + date + "." + extension;
    }

    public abstract String[] processToFiles(ImageData[] images);
}
