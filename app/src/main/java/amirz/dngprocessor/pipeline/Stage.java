package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.nightcamera.pipeline.StagePipeline;

public abstract class Stage implements AutoCloseable {
    private GLPrograms mConverter;

    public void init(GLPrograms converter) {
        mConverter = converter;
    }

    protected GLPrograms getConverter() {
        return mConverter;
    }

    public boolean isEnabled() {
        return true;
    }

    public abstract void execute(StagePipeline.StageMap previousStages);

    public abstract int getShader();

    @Override
    public void close() {
    }
}
