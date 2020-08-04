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
        for (int i = 0; i < images.size() - 1; i++) {
            converter.seti("altFrame" + (i + 1), 2 * i);
            images.get(i).bind(GL_TEXTURE0 + 2 * i);
        }

        converter.seti("refFrame", 2 * (images.size() - 1));
        images.get(images.size() - 1).bind(GL_TEXTURE0 + 2 * (images.size() - 1));

        converter.seti("alignment", 2 * images.size() + 2);
        align.getAlign().bind(GL_TEXTURE0 + 2 * images.size() + 2);

        mTexture = new Texture(size[0], size[1], 1, Texture.Format.UInt16, null);
        mTexture.setFrameBuffer();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_mergelayer_fs;
    }

    @Override
    public void close() {
        mTexture.close();
    }
}
