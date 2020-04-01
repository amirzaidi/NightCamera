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

        Align analyze = previousStages.getStage(Align.class);

        // Assume same size.
        List<Texture> images = analyze.getImages();
        List<int[]> alignments = analyze.getAlignments();

        converter.seti("alignCount", images.size());
        converter.seti("centerFrame", 0);
        images.get(0).bind(GL_TEXTURE0);
        for (int i = 1; i <= 4; i++) {
            converter.seti("alignFrame" + i, 2 * i);
            images.get(i).bind(GL_TEXTURE0 + 2 * i);
            converter.seti("alignVec" + i, alignments.get(i));
        }

        mTexture = new Texture(images.get(0).getWidth(), images.get(0).getHeight(), 1, Texture.Format.UInt16, null);
        mTexture.setFrameBuffer();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_merge_fs;
    }

    @Override
    public void close() {
        mTexture.close();
    }
}
