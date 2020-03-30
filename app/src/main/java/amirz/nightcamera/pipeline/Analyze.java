package amirz.nightcamera.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.nightcamera.R;
import amirz.nightcamera.data.ImageData;

public class Analyze extends Stage {
    private final List<ImageData> mImages;
    private final Texture.Config mConfig;
    private final List<Texture> mTextures = new ArrayList<>();
    private final List<int[]> mAlignments = new ArrayList<>();

    Analyze(List<ImageData> images, int width, int height) {
        mImages = images;
        mConfig = new Texture.Config();
        mConfig.w = width;
        mConfig.h = height;
        mConfig.format = Texture.Format.UInt16;
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
        converter.seti("frame", 0);
        converter.setf("freq", 1f, 0f);

        // Remove all previous textures.
        for (Texture texture : mTextures) {
            texture.close();
        }
        mTextures.clear();

        for (int i = 0; i < mImages.size(); i++) {
            ImageData imageData = mImages.get(i);
            if (mTextures.size() <= i) {
                mConfig.pixels = imageData.buffer(0);
                mTextures.add(new Texture(mConfig));
            }
            if (mAlignments.size() <= i) {
                mAlignments.add(new int[2]);
            }
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage1_analyze_fs;
    }
}
