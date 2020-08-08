package amirz.nightcamera.pipeline;

public class Timer {
    private long startTime;

    public Timer() {
        reset();
    }

    public long reset() {
        long endTime = System.currentTimeMillis();
        long timeDiff = endTime - startTime;
        startTime = endTime;
        return timeDiff;
    }
}