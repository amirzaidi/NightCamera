package amirz.nightcamera.processor;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import amirz.dngprocessor.gl.ShaderLoader;
import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.pipeline.StagePipeline;
import amirz.nightcamera.server.CameraServer;

public class PostProcessorRAW extends PostProcessor implements AutoCloseable {
    private static final String TAG = "PostProcessorRAW";

    private static final String TEMP_FILE_PREFIX = "NightCamera";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

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
    public File[] processToFiles(ImageData[] images) {
        ImageData img = images[images.length - 1];
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

            // Overwrite original buffer.
            ByteBuffer newBuffer = mStagePipeline.execute();
            img.buffer(0).put(newBuffer);
        }

        try (DngCreator dngCreator = new DngCreator(mStreamFormat.characteristics, img.result)) {
            dngCreator.setOrientation(mDevice.getExifRotation(mStreamFormat.id, img.motion.mRot));
            File tmp = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            try (FileOutputStream output = new FileOutputStream(tmp)) {
                dngCreator.writeImage(output, img.image);
            }
            return new File[] { tmp };
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new File[0];
    }

    @Override
    public void close() {
        if (mStagePipeline != null) {
            mStagePipeline.close();
        }
    }
}
