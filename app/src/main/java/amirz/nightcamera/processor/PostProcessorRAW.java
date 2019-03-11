package amirz.nightcamera.processor;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.gl.GLCore;
import amirz.nightcamera.gl.GLProgram;
import amirz.nightcamera.gl.generic.GLTex;
import amirz.nightcamera.server.CameraServer;

public class PostProcessorRAW extends PostProcessor {
    private static final String TAG = "PostProcessorRAW";

    public PostProcessorRAW(CameraServer.CameraStreamFormat cameraFormatSize) {
        super(cameraFormatSize);
    }

    @Override
    public String[] processToFiles(ImageData[] images) {
        ImageData img = images[0];
        ByteBuffer buffer;
        if (images.length >= 8) {
            Log.d(TAG, "Full shutter, merging frames");

            int width = mStreamFormat.size.getWidth();
            int height = mStreamFormat.size.getHeight();
            int cfa = mStreamFormat.characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);

            GLCore core = new GLCore(width, height);
            GLProgram program = (GLProgram) core.getProgram();

            GLTex[] tex = new GLTex[8];
            for (int i = 0; i < tex.length; i++) {
                tex[i] = program.frameToTexture(images[i].buffer(0), width, height);
            }

            GLTex first = program.alignAndMerge(tex[0], tex[1], width, height, cfa);
            GLTex second = program.alignAndMerge(tex[2], tex[3], width, height, cfa);
            GLTex third = program.alignAndMerge(tex[4], tex[5], width, height, cfa);
            GLTex fourth = program.alignAndMerge(tex[6], tex[7], width, height, cfa);

            GLTex firstSecond = program.alignAndMerge(first, second, width, height, cfa);
            GLTex thirdFourth = program.alignAndMerge(third, fourth, width, height, cfa);

            //program.alignAndMerge(firstSecond, thirdFourth, width, height);
            buffer = core.resultBuffer();

            core.close();
        } else {
            buffer = img.buffer(0);
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
}
