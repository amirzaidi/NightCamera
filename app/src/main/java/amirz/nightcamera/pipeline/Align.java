package amirz.nightcamera.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.nightcamera.R;
import amirz.nightcamera.data.ImageData;

import static android.opengl.GLES20.GL_TEXTURE0;

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

        // Load and downscale all frames.
        converter.seti("frame", 0);
        List<Texture> downscaled = new ArrayList<>();

        int dsw = mDownscaleConfig.w;
        int dsh = mDownscaleConfig.h;

        // First downscale all buffers, and drop chroma information.
        for (int i = 0; i < mImages.size(); i++) {
            ImageData imageData = mImages.get(i);
            mConfig.pixels = imageData.buffer(0);
            Texture in = new Texture(mConfig);
            mTextures.add(in);
            in.bind(GL_TEXTURE0);

            Texture ds = new Texture(mDownscaleConfig);
            ds.setFrameBuffer();

            converter.drawBlocks(dsw, dsh);
            downscaled.add(ds);

            mAlignments.add(new int[2]);
        }

        for (Texture ds : downscaled) {
            ds.close();
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage0_downscale_fs;
    }
}
