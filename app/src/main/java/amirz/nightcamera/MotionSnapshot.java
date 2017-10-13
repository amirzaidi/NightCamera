package amirz.nightcamera;

public class MotionSnapshot {
    public float[] mMovement;
    public int mRot;

    public MotionSnapshot(float[] movement, int rot) {
        mMovement = movement.clone();
        mRot = rot;
    }
}
