package amirz.nightcamera;

public class MotionSnapshot {
    public float[] mMovement;

    public MotionSnapshot(float[] movement) {
        mMovement = movement.clone();
    }
}
