package amirz.nightcamera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.support.media.ExifInterface;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PostProcessorRAW extends PostProcessor {
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static { //Regular camera
        ORIENTATIONS.append(0, ExifInterface.ORIENTATION_ROTATE_90);
        ORIENTATIONS.append(90, ExifInterface.ORIENTATION_NORMAL);
        ORIENTATIONS.append(180, ExifInterface.ORIENTATION_ROTATE_270);
        ORIENTATIONS.append(270, ExifInterface.ORIENTATION_ROTATE_180);
    }

    private CameraCharacteristics characteristics;

    public PostProcessorRAW(FullscreenActivity activity, CameraFormatSize cameraFormatSize, CameraCharacteristics characteristics) {
        super(activity, cameraFormatSize);
        this.characteristics = characteristics;
    }

    @Override
    protected String[] internalProcessAndSave(ImageData[] images) {
        ByteBuffer[] buffs = new ByteBuffer[images.length];
        //ByteBuffer[] buffs = new ByteBuffer[2];
        for (int i = 0; i < buffs.length; i++) {
            buffs[i] = images[i].buffer(0);
        }

        Log.d("PostProcessorRAW", "Start");
        byte[] bs = new byte[buffs[0].capacity()];
        //bs = new byte[1024];
        for (int offset = 0; offset < bs.length; offset += 2) {
            int value = 0;
            for (ByteBuffer buff : buffs) {
                value += buff.get(offset);
                value += buff.get(offset + 1) << 8;
            }
            value /= buffs.length;
            bs[offset] = (byte)(value & 0xFF);
            bs[offset + 1] = (byte)((value >> 8) & 0xFF);
        }
        Log.d("PostProcessorRAW", "P1");
        //Simple take average algorithm

        ImageData img = images[0];
        buffs[0].put(bs);

        String file = getSavePath("dng");
        DngCreator dngCreator = new DngCreator(characteristics, img.result);
        try {
            FileOutputStream output = new FileOutputStream(file);
            dngCreator.writeByteBuffer(output, cameraFormatSize.size, buffs[0], 0);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        dngCreator.setOrientation(ORIENTATIONS.get(img.motion.mRot));
        dngCreator.close();

        return new String[] { file };
    }
}
