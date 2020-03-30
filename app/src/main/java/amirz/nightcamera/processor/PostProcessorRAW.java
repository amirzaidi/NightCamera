package amirz.nightcamera.processor;

import android.hardware.camera2.DngCreator;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import amirz.dngprocessor.gl.ShaderLoader;
import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.pipeline.StagePipeline;
import amirz.nightcamera.server.CameraServer;

public class PostProcessorRAW extends PostProcessor implements AutoCloseable {
    private static final String TAG = "PostProcessorRAW";

    private static ShaderLoader sShaderLoader;

    public static void setShaderLoader(ShaderLoader shaderLoader) {
        sShaderLoader = shaderLoader;
    }

    private StagePipeline mStagePipeline;
    private List<ImageData> mDeepList = new ArrayList<>();

    public PostProcessorRAW(CameraServer.CameraStreamFormat cameraFormatSize) {
        super(cameraFormatSize);
    }

    @Override
    public String[] processToFiles(ImageData[] images) {
        ImageData img = images[images.length - 1];
        ByteBuffer buffer = img.buffer(0);

        Log.d(TAG, "Process image count: " + images.length);
        if (images.length > 1 && DevicePreset.getInstance().isBright()) {
            mDeepList.clear();
            // Add in reverse order, so newest is 0.
            for (int i = images.length - 1; i >= 0; i--) {
                mDeepList.add(images[i]);
            }

            if (mStagePipeline == null) {
                mStagePipeline = new StagePipeline(Collections.unmodifiableList(mDeepList),
                        mStreamFormat.size.getWidth(),
                        mStreamFormat.size.getHeight(),
                        sShaderLoader);
            }

            buffer = mStagePipeline.execute();
        }

        String file = getSavePath("dng");

        DngCreator dngCreator = new DngCreator(mStreamFormat.characteristics, img.result);
        dngCreator.setOrientation(mDevice.getExifRotation(mStreamFormat.id, img.motion.mRot));
        try {
            FileOutputStream output = new FileOutputStream(file);
            dngCreator.writeByteBuffer(output, mStreamFormat.size, buffer, 0);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        dngCreator.close();

        return new String[] { file };
    }

    @Override
    public void close() {
        if (mStagePipeline != null) {
            mStagePipeline.close();
        }
    }
}
