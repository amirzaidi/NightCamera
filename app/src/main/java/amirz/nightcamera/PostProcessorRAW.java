package amirz.nightcamera;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import android.support.media.ExifInterface;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;

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
        String[] files = new String[images.length];
        int i = 0;
        for (ImageData img : images) {
            files[i] = getSavePath("dng");
            DngCreator dngCreator = new DngCreator(characteristics, img.result);
            try {
                FileOutputStream output = new FileOutputStream(files[i++]);
                dngCreator.writeImage(output, img.image);
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            dngCreator.setOrientation(ORIENTATIONS.get(img.motion.mRot));
            dngCreator.close();
        }
        return files;
    }
}
