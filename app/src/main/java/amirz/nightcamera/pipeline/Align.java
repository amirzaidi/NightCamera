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

import static amirz.dngprocessor.util.Constants.BLOCK_HEIGHT;
import static android.opengl.GLES20.*;
import static android.opengl.GLES30.GL_RGBA_INTEGER;

public class Align extends Stage {
    private static final String TAG = "Align";

    private final List<ImageData> mImages;
    private final int mWidth, mHeight;
    private final Texture.Config mConfig;
    private final List<Texture> mTextures = new ArrayList<>();
    private Texture mAlign, mWeights;

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

    Texture getWeights() {
        return mWeights;
    }

    private class TexPyramid {
        private static final int DOWNSAMPLE_SCALE = 4;
        private static final int TILE_SIZE = 8;

        private Texture mLargeResRef, mMidResRef, mSmallResRef;
        private Texture mLargeRes, mMidRes, mSmallRes;

        private Texture mLargeResRefSumHorz, mLargeResRefSumVert;
        private Texture mLargeResSumHorz, mLargeResSumVert;

        private Texture mLargeResRefSumHorzDiff, mLargeResRefSumVertDiff;
        private Texture mLargeResSumHorzDiff, mLargeResSumVertDiff;

        private Texture mSmallAlign, mMidAlign, mLargeAlign;
        private Texture mLargeWeights;

        private void downsample() {
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
                converter.drawBlocks(mLargeResRef, BLOCK_HEIGHT);
            }
            converter.useProgram(R.raw.stage0_downscale_gaussdown4_fs);
            {
                converter.seti("frame", 0);
                mLargeResRef.bind(GL_TEXTURE0);
                converter.seti("bounds", mLargeResRef.getWidth(), mLargeResRef.getHeight());
                converter.drawBlocks(mMidResRef, BLOCK_HEIGHT);
            }
            {
                mMidResRef.bind(GL_TEXTURE0);
                converter.seti("bounds", mMidResRef.getWidth(), mMidResRef.getHeight());
                converter.drawBlocks(mSmallResRef, BLOCK_HEIGHT, true);
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
                converter.drawBlocks(mLargeRes, BLOCK_HEIGHT);
            }
            converter.useProgram(R.raw.stage0_downscale_gaussdown4_4frames_fs);
            converter.seti("frame", 0);
            {
                mLargeRes.bind(GL_TEXTURE0);
                converter.seti("bounds", mLargeRes.getWidth(), mLargeRes.getHeight());
                converter.drawBlocks(mMidRes, BLOCK_HEIGHT);
            }
            {
                mMidRes.bind(GL_TEXTURE0);
                converter.seti("bounds", mMidRes.getWidth(), mMidRes.getHeight());
                converter.drawBlocks(mSmallRes, BLOCK_HEIGHT, true);
            }
        }

        public void integrate() {
            GLPrograms converter = getConverter();

            // Single-channel ref frame.
            converter.useProgram(R.raw.stage1_integrate_fs);
            converter.seti("refFrame", 0);

            mLargeResRefSumHorz = new Texture(mLargeResRef.getWidth(), mLargeResRef.getHeight(), 1,
                    Texture.Format.Float16, null);
            mLargeResRefSumVert = new Texture(mLargeResRef.getWidth(), mLargeResRef.getHeight(), 1,
                    Texture.Format.Float16, null);

            mLargeResRef.bind(GL_TEXTURE0);
            converter.seti("bounds", mLargeResRef.getWidth(), mLargeResRef.getHeight());
            converter.seti("direction", 1, 0);
            converter.drawBlocks(mLargeResRefSumHorz, BLOCK_HEIGHT, true);
            converter.seti("direction", 0, 1);
            converter.drawBlocks(mLargeResRefSumVert, BLOCK_HEIGHT, true);

            // Quad-channel alt frames.
            converter.useProgram(R.raw.stage1_integrate_4frames_fs);
            converter.seti("altFrame", 0);

            mLargeResSumHorz = new Texture(mLargeRes.getWidth(), mLargeRes.getHeight(), 4,
                    Texture.Format.Float16, null);
            mLargeResSumVert = new Texture(mLargeRes.getWidth(), mLargeRes.getHeight(), 4,
                    Texture.Format.Float16, null);

            mLargeRes.bind(GL_TEXTURE0);
            converter.seti("bounds", mLargeRes.getWidth(), mLargeRes.getHeight());
            converter.seti("direction", 1, 0);
            converter.drawBlocks(mLargeResSumHorz, BLOCK_HEIGHT, true);
            converter.seti("direction", 0, 1);
            converter.drawBlocks(mLargeResSumVert, BLOCK_HEIGHT, true);

            //DEBUG(this);
        }

        public void differentiate() {
            GLPrograms converter = getConverter();

            // Single-channel ref frame.
            converter.useProgram(R.raw.stage1_diff_fs);
            converter.seti("refFrame", 0);
            converter.seti("bounds", mLargeRes.getWidth(), mLargeRes.getHeight());

            // Shuffle other texture around instead of creating new ones.
            mLargeResRefSumHorzDiff = new Texture(mLargeResRefSumHorz.getWidth(),
                    mLargeResRefSumHorz.getHeight(), 1,
                    Texture.Format.Float16, null);
            mLargeResRefSumHorz.bind(GL_TEXTURE0);
            converter.seti("direction", 0, 1); // Diff vertically.
            converter.drawBlocks(mLargeResRefSumHorzDiff, BLOCK_HEIGHT, true);

            mLargeResRefSumVertDiff = mLargeResRefSumHorz;
            mLargeResRefSumVert.bind(GL_TEXTURE0);
            converter.seti("direction", 1, 0); // Diff horizontally.
            converter.drawBlocks(mLargeResRefSumVertDiff, BLOCK_HEIGHT, true);

            // Release resources.
            mLargeResRefSumVert.close();

            converter.useProgram(R.raw.stage1_diff_4frames_fs);
            converter.seti("altFrame", 0);
            converter.seti("bounds", mLargeRes.getWidth(), mLargeRes.getHeight());

            // Shuffle other texture around instead of creating new ones.
            mLargeResSumHorzDiff = new Texture(mLargeResRefSumHorz.getWidth(),
                    mLargeResRefSumHorz.getHeight(), 4,
                    Texture.Format.Float16, null);
            mLargeResSumHorz.bind(GL_TEXTURE0);
            converter.seti("direction", 0, 1); // Diff vertically.
            converter.drawBlocks(mLargeResSumHorzDiff, BLOCK_HEIGHT, true);

            mLargeResSumVertDiff = mLargeResSumHorz;
            mLargeResSumVert.bind(GL_TEXTURE0);
            converter.seti("direction", 1, 0); // Diff horizontally.
            converter.drawBlocks(mLargeResSumVertDiff, BLOCK_HEIGHT, true);

            // Release resources
            mLargeResSumVert.close();
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

            converter.seti("refFrame", 0);
            converter.seti("altFrame", 2);
            converter.seti("prevLayerAlign", 4);
            converter.seti("prevLayerScale", 0);

            mSmallResRef.bind(GL_TEXTURE0);
            mSmallRes.bind(GL_TEXTURE2);
            // No PrevAlign on GL_TEXTURE2
            converter.drawBlocks(mSmallAlign, BLOCK_HEIGHT);

            // Close resources.
            mSmallResRef.close();
            mSmallRes.close();

            mMidAlign = new Texture(mMidRes.getWidth() / TILE_SIZE + 1,
                    mMidRes.getHeight() / TILE_SIZE + 1, 4,
                    Texture.Format.UInt16, null);

            // Enable previous layers from here.
            converter.seti("prevLayerScale", 4);

            mMidResRef.bind(GL_TEXTURE0);
            mMidRes.bind(GL_TEXTURE2);
            mSmallAlign.bind(GL_TEXTURE4);
            converter.drawBlocks(mMidAlign, BLOCK_HEIGHT / DOWNSAMPLE_SCALE, true);
            // We reduce the block height because of stuttering.

            // Close resources.
            mMidResRef.close();
            mMidRes.close();
            mSmallAlign.close();

            converter.useProgram(R.raw.stage1_alignlayer_approximate_fs);

            converter.seti("refFrameHorz", 0);
            converter.seti("refFrameVert", 2);
            converter.seti("altFrameHorz", 4);
            converter.seti("altFrameVert", 6);
            converter.seti("prevLayerAlign", 8);
            converter.seti("prevLayerScale", 4);

            mLargeAlign = new Texture(mLargeRes.getWidth() / TILE_SIZE + 1,
                    mLargeRes.getHeight() / TILE_SIZE + 1, 4,
                    Texture.Format.UInt16, null);

            mLargeResRefSumHorzDiff.bind(GL_TEXTURE0);
            mLargeResRefSumVertDiff.bind(GL_TEXTURE2);
            mLargeResSumHorzDiff.bind(GL_TEXTURE4);
            mLargeResSumVertDiff.bind(GL_TEXTURE6);
            mMidAlign.bind(GL_TEXTURE8);
            converter.drawBlocks(mLargeAlign, BLOCK_HEIGHT / DOWNSAMPLE_SCALE, true);
            // We reduce the block height because of stuttering.

            // Close resources.
            mLargeResRefSumHorz.close();
            mLargeResRefSumVert.close();
            mLargeResSumHorz.close();
            mLargeResSumVert.close();
            mMidAlign.close();
        }

        private void weigh() {
            GLPrograms converter = getConverter();
            converter.useProgram(R.raw.stage2_weightiles_fs);

            mLargeWeights = new Texture(mLargeAlign.getWidth(), mLargeAlign.getHeight(), 4,
                    Texture.Format.Float16, null);

            converter.seti("refFrame", 0);
            converter.seti("altFrame", 2);
            converter.seti("alignment", 4);

            mLargeResRef.bind(GL_TEXTURE0);
            mLargeRes.bind(GL_TEXTURE2);
            mLargeAlign.bind(GL_TEXTURE4);

            // We reduce the block height because of stuttering.
            converter.drawBlocks(mLargeWeights, BLOCK_HEIGHT / DOWNSAMPLE_SCALE, true);

            // Close resources.
            mLargeResRef.close();
            mLargeRes.close();
        }
    }

    private void DEBUG(TexPyramid pyramid) {
        Texture tex = pyramid.mLargeResSumHorz;
        int w = tex.getWidth();
        int h = tex.getHeight();

        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4 * 4);
        float[] floats = new float[w * h * 4];
        //int[] uints = new int[w * h * 4];

        // Extract floats
        glReadPixels(0, h / 2, w, 1, GL_RGBA, GL_FLOAT, buffer);
        //glReadPixels(0, 0, w, h, GL_RGBA_INTEGER, GL_UNSIGNED_INT, buffer);
        buffer.position(0);
        buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats);
        //buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(uints);
        Log.d("Align", "Test " + glGetError());
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

            TexPyramid pyramid = new TexPyramid();
            Timer timer = new Timer();

            pyramid.downsample();
            Log.d(TAG, "Downsample time " + timer.reset() + "ms");

            pyramid.integrate();
            Log.d(TAG, "Integrate time " + timer.reset() + "ms");

            pyramid.differentiate();
            Log.d(TAG, "Differentiate time " + timer.reset() + "ms");

            pyramid.align();
            Log.d(TAG, "Align time " + timer.reset() + "ms");

            pyramid.weigh();
            Log.d(TAG, "Weigh time " + timer.reset() + "ms");

            mAlign = pyramid.mLargeAlign;
            mWeights = pyramid.mLargeWeights;
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage0_downscale_boxdown2_fs;
    }
}
