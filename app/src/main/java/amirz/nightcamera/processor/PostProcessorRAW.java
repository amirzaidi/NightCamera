package amirz.nightcamera.processor;

import android.graphics.Bitmap;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.renderscript.RenderScript;
import android.support.media.ExifInterface;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import amirz.nightcamera.data.ImageData;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.processor.renderscript.BitmapTransformations;
import amirz.nightcamera.processor.renderscript.RawConverter;
import amirz.nightcamera.server.CameraServer;

public class PostProcessorRAW extends PostProcessor {
        private final RenderScript mRs;

    public PostProcessorRAW(CameraServer.CameraStreamFormat cameraFormatSize, RenderScript rs) {
        super(cameraFormatSize);
        mRs = rs;
    }

    @Override
    public String[] processToFiles(ImageData[] images) {
        DevicePreset.RawProcessSettings settings = mDevice.getRawProcessSettings(mStreamFormat.id, images[images.length - 1].result);

        int rotate = images[images.length - 1].motion.mRot;

        Image raw = images[0].image;
        Bitmap rawBitmap = Bitmap.createBitmap(raw.getWidth(), raw.getHeight(), Bitmap.Config.ARGB_8888);
        byte[] rawPlane = new byte[raw.getPlanes()[0].getRowStride() * raw.getHeight()];

        // Render RAW image to a bitmap
        raw.getPlanes()[0].getBuffer().get(rawPlane);
        raw.getPlanes()[0].getBuffer().rewind();

        RawConverter.convertToSRGB(mRs, raw.getWidth(),
                raw.getHeight(), raw.getPlanes()[0].getRowStride(), rawPlane,
                mStreamFormat.characteristics, images[0].result, /*offsetX*/0, /*offsetY*/0,
                settings.saturationFactor, settings.tonemapStrength,
                /*out*/rawBitmap);

        switch (settings.sharpenLevel) {
            case 1:
                rawBitmap = BitmapTransformations.sharpen(mRs, rawBitmap);
                break;
            case -1:
                rawBitmap = BitmapTransformations.blur(mRs, rawBitmap);
                break;
        }

        String mediaFile = getSavePath("jpeg");
        try {
            FileOutputStream fos = new FileOutputStream(mediaFile);
            rawBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            rawBitmap.recycle();
            fos.close();

            ExifInterface exif = new ExifInterface(mediaFile);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(mDevice.getExifRotation(mStreamFormat.id, rotate)));
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (true) return new String[] { mediaFile };

        ByteBuffer[] buffs = new ByteBuffer[images.length];
        //ByteBuffer[] buffs = new ByteBuffer[2];
        for (int i = 0; i < buffs.length; i++) {
            buffs[i] = images[i].buffer(0);
        }

        Log.d("PostProcessorRAW", "Start");
        byte[] bs = new byte[buffs[0].capacity()];
        //bs = new byte[1024];
        for (int offset = 0; offset < bs.length; offset += 2) {
            int[] vals = new int[buffs.length];
            for (int j = 0; j < buffs.length; j++) {
                ByteBuffer buff = buffs[j];
                vals[j] = (buff.get(offset + 1) << 8) + buff.get(offset);
            }
            Arrays.sort(vals);
            int value = vals[vals.length / 2];
            bs[offset] = (byte)(value & 0xFF);
            bs[offset + 1] = (byte)((value >> 8) & 0xFF);
        }
        Log.d("PostProcessorRAW", "P1");
        //Simple take average algorithm

        ImageData img = images[0];
        buffs[0].put(bs);

        String file = getSavePath("dng");
        DngCreator dngCreator = new DngCreator(mStreamFormat.characteristics, img.result);
        try {
            FileOutputStream output = new FileOutputStream(file);
            dngCreator.writeByteBuffer(output, mStreamFormat.size, buffs[0], 0);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //dngCreator.setOrientation(ORIENTATIONS.get(img.motion.mRot));
        dngCreator.close();

        return new String[] { file };
    }
}
