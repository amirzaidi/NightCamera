package amirz.nightcamera.pipeline;

import java.util.List;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.nightcamera.R;

import static android.opengl.GLES20.*;

public class Merge extends Stage {
    private final int[] mFbo = new int[1];
    private Texture mTexture;

    @Override
    public void init(GLPrograms converter) {
        super.init(converter);
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, mFbo, 0);
    }

    @Override
    public void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Align align = previousStages.getStage(Align.class);

        // Assume same size.
        List<Texture> images = align.getImages();
        int[] size = align.getSize();

        converter.seti("alignCount", images.size() - 1);
        converter.seti("frameSize", size);

        converter.seti("refFrame", 0);
        images.get(0).bind(GL_TEXTURE0);

        for (int i = 1; i < images.size(); i++) {
            converter.seti("altFrame" + i, 2 * i);
            images.get(i).bind(GL_TEXTURE0 + 2 * i);
        }

        converter.seti("alignment", 2 * images.size());
        align.getAlign().bind(GL_TEXTURE0 + 2 * images.size());

        mTexture = new Texture(size[0], size[1], 1, Texture.Format.UInt16, null);
        mTexture.setFrameBuffer();
    }

    @Override
    public int getShader() {
        return R.raw.stage3_mergelayer_fs;
    }

    @Override
    public void close() {
        mTexture.close();
    }
}
