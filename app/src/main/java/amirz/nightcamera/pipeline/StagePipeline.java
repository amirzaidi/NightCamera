package amirz.nightcamera.pipeline;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.gl.GLCore;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.pipeline.Stage;
import amirz.nightcamera.data.ImageData;

public class StagePipeline implements AutoCloseable {
    private static final String TAG = "StagePipeline";

    private final List<Stage> mStages = new ArrayList<>();

    private final GLCore mCore;
    private final GLPrograms mConverter;

    public StagePipeline(List<ImageData> images, int width, int height, ShaderLoader loader) {
        mCore = new GLCore(width, height, loader);
        mConverter = mCore.getProgram();

        addStage(new Analyze(images, width, height));
        addStage(new Merge());
    }

    private void addStage(Stage stage) {
        if (stage.isEnabled()) {
            stage.init(mConverter);
            mStages.add(stage);
        }
    }

    public ByteBuffer execute() {
        int stageCount = mStages.size();
        for (int i = 0; i < stageCount; i++) {
            Stage stage = mStages.get(i);
            mConverter.useProgram(stage.getShader());
            stage.execute(new StageMap(mStages.subList(0, i)));
        }

        // Assume that last stage set everything but did not render yet.
        mCore.getProgram().drawBlocks(mCore.width, mCore.height);
        return mCore.resultBuffer();
    }

    @Override
    public void close() {
        for (Stage stage : mStages) {
            stage.close();
        }
        mCore.close();
    }

    public static class StageMap {
        private final List<Stage> mStages;

        private StageMap(List<Stage> stages) {
            mStages = stages;
        }

        @SuppressWarnings("unchecked")
        public <T> T getStage(Class<T> cls) {
            for (Stage stage : mStages) {
                if (stage.getClass() == cls) {
                    return (T) stage;
                }
            }
            return null;
        }
    }
}
