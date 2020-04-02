package amirz.nightcamera.processor;

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

    private static final int DEFAULT_PIXEL_STRIDE = 2; // bytes per sample

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

    private void initStagePipeline() {
        if (mStagePipeline == null) {
            mStagePipeline = new StagePipeline(Collections.unmodifiableList(mDeepList),
                    mStreamFormat.size.getWidth(),
                    mStreamFormat.size.getHeight(),
                    sShaderLoader);
        }
    }

    @Override
    public File[] processToFiles(ImageData[] images) {
        Log.d(TAG, "Process image count: " + images.length);

        ImageData img = images[images.length - 1];
        Image.Plane p = img.image.getPlanes()[0];
        ByteBuffer buffer = null;

        Size size = mStreamFormat.size;

        Log.d(TAG, "Size " + img.image.getWidth() + " " + img.image.getHeight());
        Log.d(TAG, "Plane: " + p.getPixelStride() + " " + p.getRowStride());

        if (images.length > 1) {
            // Add in reverse order, so newest is 0.
            mDeepList.clear();
            for (int i = images.length - 1; i >= 0; i--) {
                mDeepList.add(images[i]);
            }

            initStagePipeline();
            buffer = mStagePipeline.execute();
        }

        try (DngCreator dngCreator = new DngCreator(mStreamFormat.characteristics, img.result)) {
            dngCreator.setOrientation(mDevice.getExifRotation(mStreamFormat.id, img.motion.mRot));
            File tmp = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            try (FileOutputStream output = new FileOutputStream(tmp)) {
                if (buffer == null) {
                    Log.d(TAG, "Saving image directly");
                    dngCreator.writeImage(output, img.image);
                } else {
                    Log.d(TAG, "Saving rendered buffer");
                    dngCreator.writeByteBuffer(output, size, buffer, 0);
                }
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
