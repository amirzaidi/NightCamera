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

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.GL_RGBA_INTEGER;

public class Align extends Stage {
    private final List<ImageData> mImages;
    private final int mWidth, mHeight;
    private final Texture.Config mConfig;
    private final List<Texture> mTextures = new ArrayList<>();
    private Texture mAlign;

    Align(List<ImageData> images, int width, int height) {
        mImages = images;
        mWidth = width;
        mHeight = height;

        mConfig = new Texture.Config();
        mConfig.h = height;
        mConfig.format = Texture.Format.UInt16;
    }

    public int[] getSize() {
        return new int[] { mWidth, mHeight };
    }

    List<Texture> getImages() {
        return Collections.unmodifiableList(mTextures.subList(0, mImages.size()));
    }

    Texture getAlign() {
        return mAlign;
    }

    private class TexPyramid implements AutoCloseable {
        private static final int DOWNSAMPLE_SCALE = 4;
        private static final int TILE_SIZE = 8;
        private static final int BH = 1;

        private final Texture mLargeResRef, mMidResRef, mSmallResRef;
        private Texture mLargeRes, mMidRes, mSmallRes;
        private Texture mSmallAlign, mMidAlign, mLargeAlign;

        private TexPyramid() {
            GLPrograms converter = getConverter();

            Texture refTex = mTextures.get(0);
            int width = refTex.getWidth();
            int height = refTex.getHeight();

            // Part 1: Downscaling reference frame.
            mLargeResRef = new Texture(width / 2, height / 2, 1,
                    Texture.Format.Float16, null);

            mMidResRef = new Texture(mLargeResRef.getWidth() / DOWNSAMPLE_SCALE + 1,
                    mLargeResRef.getHeight() / DOWNSAMPLE_SCALE + 1, 1,
                    Texture.Format.Float16, null);

            mSmallResRef = new Texture(mMidResRef.getWidth() / DOWNSAMPLE_SCALE + 1,
                    mMidResRef.getHeight() / DOWNSAMPLE_SCALE + 1, 1,
                    Texture.Format.Float16, null);

            // Running the downscalers.
            converter.useProgram(R.raw.stage0_downscale_boxdown2_fs);
            {
                converter.seti("frame", 0);
                refTex.bind(GL_TEXTURE0);
                converter.drawBlocks(mLargeResRef);
            }
            converter.useProgram(R.raw.stage0_downscale_gaussdown4_fs);
            {
                converter.seti("frame", 0);
                mLargeResRef.bind(GL_TEXTURE0);
                converter.seti("bounds", mLargeResRef.getWidth(), mLargeResRef.getHeight());
                converter.drawBlocks(mMidResRef);
            }
            {
                mMidResRef.bind(GL_TEXTURE0);
                converter.seti("bounds", mMidResRef.getWidth(), mMidResRef.getHeight());
                converter.drawBlocks(mSmallResRef);
            }

            // Part 2: Downscaling alternative frames.
            mLargeRes = new Texture(width / 2, height / 2, 4,
                    Texture.Format.Float16, null);

            mMidRes = new Texture(mLargeRes.getWidth() / DOWNSAMPLE_SCALE + 1,
                    mLargeRes.getHeight() / DOWNSAMPLE_SCALE + 1, 4,
                    Texture.Format.Float16, null);

            mSmallRes = new Texture(mMidRes.getWidth() / DOWNSAMPLE_SCALE + 1,
                    mMidRes.getHeight() / DOWNSAMPLE_SCALE + 1, 4,
                    Texture.Format.Float16, null);

            // Running the downscalers.
            converter.useProgram(R.raw.stage0_downscale_boxdown2_4frames_fs);
            {
                for (int i = 1; i < mTextures.size(); i++) {
                    mTextures.get(i).bind(GL_TEXTURE0 + 2 * i);
                    converter.seti("frame" + i, 2 * i);
                }
                converter.drawBlocks(mLargeRes);
            }
            converter.useProgram(R.raw.stage0_downscale_gaussdown4_4frames_fs);
            converter.seti("frame", 0);
            {
                mLargeRes.bind(GL_TEXTURE0);
                converter.seti("bounds", mLargeRes.getWidth(), mLargeRes.getHeight());
                converter.drawBlocks(mMidRes);
            }
            {
                mMidRes.bind(GL_TEXTURE0);
                converter.seti("bounds", mMidRes.getWidth(), mMidRes.getHeight());
                converter.drawBlocks(mSmallRes);
            }
        }

        /**
         * best positions = null
         * cycle:
         *  select new shift
         *  foreach block:
         *   compute summed diff with new shift
         *   if new shift is better
         *    update position of block
         *  end foreach
         * end cycle
         */
        private void align() {
            GLPrograms converter = getConverter();
            converter.useProgram(R.raw.stage1_alignlayer_fs);

            mSmallAlign = new Texture(mSmallRes.getWidth() / TILE_SIZE + 1,
                    mSmallRes.getHeight() / TILE_SIZE + 1, 4,
                    Texture.Format.UInt16, null);

            mMidAlign = new Texture(mMidRes.getWidth() / TILE_SIZE + 1,
                    mMidRes.getHeight() / TILE_SIZE + 1, 4,
                    Texture.Format.UInt16, null);

            mLargeAlign = new Texture(mLargeRes.getWidth() / TILE_SIZE + 1,
                    mLargeRes.getHeight() / TILE_SIZE + 1, 4,
                    Texture.Format.UInt16, null);

            converter.seti("refFrame", 0);
            converter.seti("altFrame", 2);
            converter.seti("prevLayerAlign", 4);
            converter.seti("prevLayerScale", 0);

            mSmallResRef.bind(GL_TEXTURE0);
            mSmallRes.bind(GL_TEXTURE2);
            converter.seti("bounds", mSmallRes.getWidth(), mSmallRes.getHeight());
            // No PrevAlign on GL_TEXTURE2
            converter.drawBlocks(mSmallAlign, BH);

            // Enable previous layers from here.
            converter.seti("prevLayerScale", 4);

            mMidResRef.bind(GL_TEXTURE0);
            mMidRes.bind(GL_TEXTURE2);
            converter.seti("bounds", mMidRes.getWidth(), mMidRes.getHeight());
            mSmallAlign.bind(GL_TEXTURE4);
            converter.drawBlocks(mMidAlign, BH);

            mLargeResRef.bind(GL_TEXTURE0);
            mLargeRes.bind(GL_TEXTURE2);
            converter.seti("bounds", mLargeRes.getWidth(), mLargeRes.getHeight());
            mMidAlign.bind(GL_TEXTURE4);
            converter.drawBlocks(mLargeAlign, BH);
        }

        @Override
        public void close() {
            mLargeResRef.close();
            mMidResRef.close();
            mSmallRes.close();

            mLargeRes.close();
            mMidRes.close();
            mSmallRes.close();

            mSmallAlign.close();
            mMidAlign.close();
            // Keep mLargeAlign.
        }
    }

    @Override
    public void execute(StagePipeline.StageMap previousStages) {
        // Remove all previous textures.
        for (Texture texture : mTextures) {
            texture.close();
        }
        mTextures.clear();

        if (mImages.size() == 5) {
            // Might be wider than w.
            mConfig.w = mImages.get(0).image.getPlanes()[0].getRowStride() / 2;
            for (int i = 0; i < mImages.size(); i++) {
                ImageData imageData = mImages.get(i);
                mConfig.pixels = imageData.buffer(0);
                Texture in = new Texture(mConfig);
                mTextures.add(in);
            }

            long startTime = System.currentTimeMillis();
            try (TexPyramid pyramid = new TexPyramid()) {
                final boolean DEBUG = false;
                if (DEBUG) {
                    Texture tex = pyramid.mMidResRef;
                    int w = tex.getWidth();
                    int h = tex.getHeight();

                    ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4 * 4);
                    int[] uints = new int[w * h * 4];

                    // Extract floats
                    glReadPixels(0, 0, w, h, GL_RGBA_INTEGER, GL_UNSIGNED_INT, buffer);
                    buffer.position(0);
                    buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(uints);
                    Log.d("Align", "Test " + glGetError());
                }

                long subSampleTime = System.currentTimeMillis();
                long timeSpan = subSampleTime - startTime;
                // 54ms on non-debug mode
                Log.d("Align", "Downsample time " + timeSpan + "ms");

                pyramid.align();
                long alignTime = System.currentTimeMillis();
                timeSpan = alignTime - subSampleTime;
                Log.d("Align", "Align time " + timeSpan + "ms");

                mAlign = pyramid.mLargeAlign;
            }
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage0_downscale_boxdown2_fs;
    }
}
