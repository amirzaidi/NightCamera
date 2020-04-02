package amirz.nightcamera.pipeline;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.nightcamera.R;
import amirz.nightcamera.data.ImageData;

import static android.opengl.GLES10.GL_RGBA;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.glReadPixels;

public class Align extends Stage {
    private final List<ImageData> mImages;
    private final Texture.Config mConfig;
    private final Texture.Config mDownscaleConfig;
    private final List<Texture> mTextures = new ArrayList<>();
    private final List<int[]> mAlignments = new ArrayList<>();

    Align(List<ImageData> images, int width, int height) {
        mImages = images;

        mConfig = new Texture.Config();
        mConfig.w = width;
        mConfig.h = height;
        mConfig.format = Texture.Format.UInt16;

        mDownscaleConfig = new Texture.Config();
        mDownscaleConfig.w = width / 2;
        mDownscaleConfig.h = height / 2;
        mDownscaleConfig.format = Texture.Format.UInt16;
    }

    List<Texture> getImages() {
        return Collections.unmodifiableList(mTextures.subList(0, mImages.size()));
    }

    List<int[]> getAlignments() {
        return Collections.unmodifiableList(mAlignments.subList(0, mImages.size()));
    }

    @Override
    public void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        // Remove all previous textures.
        for (Texture texture : mTextures) {
            texture.close();
        }
        mTextures.clear();
        mAlignments.clear();

        Pyramid baseImage = null;

        int dsw = mDownscaleConfig.w;
        int dsh = mDownscaleConfig.h;

        // Downscale all buffers, and drop chroma information.
        for (int i = 0; i < mImages.size(); i++) {
            // Program resets in pyramid creation and alignment call.
            converter.useProgram(getShader());
            converter.seti("frame", 0);

            ImageData imageData = mImages.get(i);
            mConfig.pixels = imageData.buffer(0);
            Texture in = new Texture(mConfig);
            mTextures.add(in);
            in.bind(GL_TEXTURE0);

            Texture ds = new Texture(mDownscaleConfig);
            ds.setFrameBuffer();
            converter.drawBlocks(dsw, dsh);

            if (i == 0) {
                // First alignment should just be zero.
                baseImage = new Pyramid(converter, ds);
                mAlignments.add(new int[2]);
            } else {
                try (Pyramid alignImage = new Pyramid(converter, ds)) {
                    mAlignments.add(align(converter, baseImage, alignImage));
                }
            }
        }
    }

    private int[] align(GLPrograms converter, Pyramid baseImage, Pyramid alignImage) {
        converter.useProgram(R.raw.stage2_align);

        // Diff tex dimensions.
        int w = baseImage.mBottom.getWidth() / 2;
        int h = baseImage.mBottom.getHeight() / 2;

        int[] offset = { 0, 0 };
        try (Texture diffTex = new Texture(w / 4, h, 4, Texture.Format.Float16, null)) {
            converter.seti("buf1", 0);
            converter.seti("buf2", 2);
            diffTex.setFrameBuffer();

            ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
            float[] floats = new float[w * h];

            // Align bottom first.
            baseImage.mBottom.bind(GL_TEXTURE0);
            alignImage.mBottom.bind(GL_TEXTURE2);
            converter.seti("maxXy", baseImage.mBottom.getWidth() - 1, baseImage.mBottom.getHeight() - 1);
            converter.seti("stride", 2);

            int bestDir = Integer.MIN_VALUE;
            float bestDirVal = Float.MAX_VALUE;
            for (int dir = 0; dir < 25; dir++) {
                int dy = (dir / 5) - 2;
                int dx = (dir % 5) - 2;

                converter.seti("offset", offset[0] + dx, offset[1] + dy);
                converter.drawBlocks(w / 4, h);

                glReadPixels(0, 0, w / 4, h, GL_RGBA, GL_FLOAT, buffer);
                buffer.position(0);
                buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats);

                // Extract floats
                float dirVal = 0f;
                for (float aFloat : floats) {
                    dirVal += aFloat;
                }
                //Log.d("Align", "Iterate one " + dx + " " + dy + " " + dirVal);
                if (dirVal < bestDirVal) {
                    bestDirVal = dirVal;
                    bestDir = dir;
                }
            }

            offset[0] += Pyramid.SCALE * ((bestDir % 5) - 2);
            offset[1] += Pyramid.SCALE * ((bestDir / 5) - 2);
            Log.d("Align", "Step one result: " + offset[0] + " " + offset[1]);

            // Align middle next.
            baseImage.mMid.bind(GL_TEXTURE0);
            alignImage.mMid.bind(GL_TEXTURE2);
            converter.seti("maxXy", baseImage.mMid.getWidth() - 1, baseImage.mMid.getHeight() - 1);
            converter.seti("stride", 2 * Pyramid.SCALE);

            bestDirVal = Float.MAX_VALUE;
            for (int dir = 0; dir < 25; dir++) {
                int dy = (dir / 5) - 2;
                int dx = (dir % 5) - 2;

                diffTex.setFrameBuffer();
                converter.seti("offset", offset[0] + dx, offset[1] + dy);
                converter.drawBlocks(w / 4, h);

                glReadPixels(0, 0, w / 4, h, GL_RGBA, GL_FLOAT, buffer);
                buffer.position(0);
                buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats);

                // Extract floats
                float dirVal = 0f;
                for (float aFloat : floats) {
                    dirVal += aFloat;
                }
                //Log.d("Align", "Iterate two " + dx + " " + dy + " " + dirVal);
                if (dirVal < bestDirVal) {
                    bestDirVal = dirVal;
                    bestDir = dir;
                }
            }

            offset[0] *= Pyramid.SCALE;
            offset[1] *= Pyramid.SCALE;
            offset[0] += Pyramid.SCALE * ((bestDir % 5) - 2);
            offset[1] += Pyramid.SCALE * ((bestDir / 5) - 2);
            Log.d("Align", "Step two result: " + offset[0] + " " + offset[1]);

            // Align top last.
            baseImage.mTop.bind(GL_TEXTURE0);
            alignImage.mTop.bind(GL_TEXTURE2);
            converter.seti("maxXy", baseImage.mTop.getWidth() - 1, baseImage.mTop.getHeight() - 1);
            converter.seti("stride", 2 * Pyramid.SCALE * Pyramid.SCALE);

            bestDirVal = Float.MAX_VALUE;
            for (int dir = 0; dir < 25; dir++) {
                int dy = (dir / 5) - 2;
                int dx = (dir % 5) - 2;

                diffTex.setFrameBuffer();
                converter.seti("offset", offset[0] + dx, offset[1] + dy);
                converter.drawBlocks(w / 4, h);

                glReadPixels(0, 0, w / 4, h, GL_RGBA, GL_FLOAT, buffer);
                buffer.position(0);
                buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats);

                // Extract floats
                float dirVal = 0f;
                for (float aFloat : floats) {
                    dirVal += aFloat;
                }
                //Log.d("Align", "Iterate three " + dx + " " + dy + " " + dirVal);
                if (dirVal < bestDirVal) {
                    bestDirVal = dirVal;
                    bestDir = dir;
                }
            }

            offset[0] += (bestDir % 5) - 2;
            offset[1] += (bestDir / 5) - 2;
            Log.d("Align", "Step three result: " + offset[0] + " " + offset[1]);
        }

        // We downscaled by 2 before.
        offset[0] *= 2;
        offset[1] *= 2;

        return offset;
    }

    @Override
    public int getShader() {
        return R.raw.stage0_downscale_fs;
    }

    private static class Pyramid implements AutoCloseable {
        private static final int SCALE = 5;

        private final Texture mTop, mMid, mBottom;

        private Pyramid(GLPrograms converter, Texture top) {
            mTop = top;
            mMid = new Texture(mTop.getWidth() / SCALE, mTop.getHeight() / SCALE, 1,
                    Texture.Format.UInt16, null);
            mBottom = new Texture(mMid.getWidth() / SCALE, mMid.getHeight() / SCALE, 1,
                    Texture.Format.UInt16, null);

            converter.useProgram(R.raw.stage1_downscale_pyramid);
            converter.seti("buf", 0);
            converter.seti("stride", SCALE);
            converter.setf("sigma", SCALE * 0.5f);

            mTop.bind(GL_TEXTURE0);
            converter.seti("bufSize", mTop.getWidth(), mTop.getHeight());
            mMid.setFrameBuffer();
            converter.drawBlocks(mMid.getWidth(), mMid.getHeight());

            mMid.bind(GL_TEXTURE0);
            converter.seti("bufSize", mMid.getWidth(), mMid.getHeight());
            mBottom.setFrameBuffer();
            converter.drawBlocks(mBottom.getWidth(), mBottom.getHeight());
        }

        @Override
        public void close() {
            mTop.close();
            mMid.close();
            mBottom.close();
        }
    }
}
