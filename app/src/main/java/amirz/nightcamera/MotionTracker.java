package amirz.nightcamera;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;

public class MotionTracker {
    private Context mContext;
    private SensorManager mSensorManager;
    private Sensor mGravitySensor;
    private Sensor mLinearSensor;
    private Sensor mRotationSensor;
    private Sensor mGyroscopeSensor;

    public float[] mGravity;
    public float[] mLinear;
    public float[] mRotation;
    public float[] mGyroscope;

    public float[] mGravityDelta = new float[3];
    public float[] mLinearDelta = new float[3];
    public float[] mRotationDelta = new float[5];
    public float[] mGyroscopeDelta = new float[3];

    private long lastLinearUpdate = 0;
    private long lastLinearShake = 0;
    private float[] mVelocity = new float[3];
    private float[] mMovement = new float[3];

    public MotionTracker(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mLinearSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

    }

    public void start() {
        int SPEED = SensorManager.SENSOR_DELAY_FASTEST;
        mSensorManager.registerListener(mGravityTracker, mGravitySensor, SPEED);
        mSensorManager.registerListener(mLinearTracker, mLinearSensor, SPEED);
        mSensorManager.registerListener(mRotationTracker, mRotationSensor, SPEED);
        mSensorManager.registerListener(mGyroscopeTracker, mGyroscopeSensor, SPEED);
    }

    public void stop() {
        mSensorManager.unregisterListener(mGravityTracker, mGravitySensor);
        mSensorManager.unregisterListener(mLinearTracker, mLinearSensor);
        mSensorManager.unregisterListener(mRotationTracker, mRotationSensor);
        mSensorManager.unregisterListener(mGyroscopeTracker, mGyroscopeSensor);
    }

    public int getRotation() {
        if (mGravity == null) {
            return 0;
        }

        if (mGravity[2] > 9f) //pointing at the ground
            return 0;

        if (Math.abs(mGravity[0]) > Math.abs(mGravity[1])) {
            if (mGravity[0] > 0f)
                return 90;
            else
                return 270;
        } else {
            if (mGravity[1] > 0f)
                return 0;
            else
                return 180;
        }
    }

    public MotionSnapshot snapshot() {
        return new MotionSnapshot(mMovement);
    }

    private SensorEventListener mGravityTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (mGravity == null) {
                mGravity = sensorEvent.values.clone();
            }

            for (int i = 0; i < sensorEvent.values.length; i++) {
                mGravityDelta[i] += sensorEvent.values[i] - mGravity[i];
                mGravity[i] = sensorEvent.values[i];
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { }
    };

    private ArrayList<float[]> previousAcc = new ArrayList<>();

    private SensorEventListener mLinearTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            long newLinearUpdate = SystemClock.elapsedRealtimeNanos();
            float deltaTime = (float)(newLinearUpdate - lastLinearUpdate) / 1000000000f;
            lastLinearUpdate = newLinearUpdate;

            boolean shake = false;
            if (mLinear != null) {
                for (int i = 0; i < sensorEvent.values.length; i++) {
                    mLinearDelta[i] = sensorEvent.values[i] - mLinear[i];
                    mVelocity[i] += mLinearDelta[i] * deltaTime;
                    mMovement[i] += mVelocity[i] * deltaTime;

                    if (Math.abs(mLinearDelta[i]) > 0.25f && Math.abs(sensorEvent.values[i]) > 0.25f) {
                        shake = true;
                        //Log.d("diffShake", diff + " " + i);
                    }
                }
            }

            if (shake) {
                lastLinearShake = newLinearUpdate;
                previousAcc.clear();
            } else {
                previousAcc.add(sensorEvent.values.clone());
            }

            float timeFromLastShake = (float)(newLinearUpdate - lastLinearShake) / 1000000000f;
            if (timeFromLastShake > 0.2f) {
                mLinear = new float[3];
                for (float[] acc : previousAcc) {
                    for (int i = 0; i < 3; i++)
                        mLinear[i] += acc[i] / previousAcc.size(); //calibration
                }

                //Log.d("linearAcc", "reset " + mVelocity[0] + " " + mVelocity[1] + " " + mVelocity[2]);

                mLinearDelta = new float[3];
                mVelocity = new float[3];
            }

            //Log.d("velocity", );
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { }
    };
    private SensorEventListener mRotationTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (mRotation == null) {
                mRotation = sensorEvent.values.clone();
            }

            for (int i = 0; i < sensorEvent.values.length; i++) {
                mRotationDelta[i] += sensorEvent.values[i] - mRotation[i];
                mRotation[i] = sensorEvent.values[i];
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { }
    };
    private SensorEventListener mGyroscopeTracker = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (mGyroscope == null) {
                mGyroscope = sensorEvent.values.clone();
            }

            for (int i = 0; i < sensorEvent.values.length; i++) {
                mGyroscopeDelta[i] += sensorEvent.values[i] - mGyroscope[i];
                mGyroscope[i] = sensorEvent.values[i];
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) { }
    };
}
