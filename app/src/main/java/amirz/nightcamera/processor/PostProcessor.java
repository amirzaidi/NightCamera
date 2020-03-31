package amirz.nightcamera.processor;

import java.io.File;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.server.CameraServer;

public abstract class PostProcessor {
    CameraServer.CameraStreamFormat mStreamFormat;
    DevicePreset mDevice;

    PostProcessor(CameraServer.CameraStreamFormat streamFormat) {
        mStreamFormat = streamFormat;
        mDevice = DevicePreset.getInstance();
    }

    public abstract File[] processToFiles(ImageData[] images);
}
