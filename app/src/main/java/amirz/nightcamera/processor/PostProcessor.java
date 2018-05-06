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

    PostProcessor(CameraServer.CameraStreamFormat streamFormat) {
        mStreamFormat = streamFormat;
        mDevice = DevicePreset.getInstance();
    }

    @SuppressLint("SimpleDateFormat")
    String getSavePath(String extension) {
        File folder = new File(Environment.getExternalStorageDirectory() + "/DCIM/NightCamera");
        if (!folder.exists() && !folder.mkdir())
            throw new RuntimeException("Cannot create /DCIM/NightCamera");
        String date = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss").format(new Date());
        return folder.getPath()+ File.separator + date + "_" + counter.incrementAndGet() + "." + extension;
    }

    public abstract String[] processToFiles(ImageData[] images);
}
