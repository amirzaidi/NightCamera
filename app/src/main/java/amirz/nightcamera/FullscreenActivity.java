package amirz.nightcamera;

import android.hardware.camera2.CameraAccessException;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import amirz.dngprocessor.gl.ShaderLoader;
import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.motion.MotionTracker;
import amirz.nightcamera.processor.PostProcessorRAW;
import amirz.nightcamera.server.CameraServer;
import amirz.nightcamera.server.CameraStream;
import amirz.nightcamera.server.CameraStreamCallbacks;
import amirz.nightcamera.ui.MainThreadDelegate;
import amirz.nightcamera.ui.PathFinder;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class FullscreenActivity extends AppCompatActivity implements CameraStreamCallbacks {
    private static final int REQUEST_PERMISSIONS = 200;

    private MainThreadDelegate mCallbackDelegate;
    public MotionTracker mMotionTracker;
    //public CameraWrapper camera;
    private CameraServer mServer;
    private CameraStream mStream;
    public int useCamera = 0;

    private PathFinder mPathFinder;

    public FloatingActionButton mSwitcher, mShutter, mVideo, mExposure;
    private boolean mExposureBright;

    private TextureView mTextureView;
    private int mWidth, mHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        PostProcessorRAW.setShaderLoader(new ShaderLoader(getResources()));

        if (DevicePreset.getInstance() == null) {
            Toast.makeText(this, "Device not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (hasPermissions()) {
            onCreateImpl();
        } else {
            ActivityCompat.requestPermissions(FullscreenActivity.this, new String[] {
                    CAMERA,
                    WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PERMISSION_GRANTED && grantResults[1] == PERMISSION_GRANTED) {
                onCreateImpl();
            } else {
                finish();
            }
        }
    }

    private void onCreateImpl() {
        mMotionTracker = new MotionTracker(this);

        mCallbackDelegate = new MainThreadDelegate(this);
        try {
            mServer = new CameraServer(this);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            finish();
        }

        mSwitcher = findViewById(R.id.switcher);
        mShutter = findViewById(R.id.shutter);
        mVideo = findViewById(R.id.video);
        mExposure = findViewById(R.id.exposure);

        setUIEnabled(false);

        mSwitcher.setOnClickListener(view -> {
            if (mStream != null) {
                mServer.requestClose(mStream);
            }

            useCamera ^= 1;
            requestOpenCamera();
        });

        mShutter.setOnClickListener(view -> {
            setUIEnabled(false);
            mStream.takeAndProcessAsync();
        });

        mVideo.setOnClickListener(view -> {
            /*start
            mVideo*/
        });

        DevicePreset.getInstance().setBright(mExposureBright);
        mExposure.setOnClickListener(view -> {
            mExposureBright = !mExposureBright;
            mExposure.setImageDrawable(mExposureBright
                    ? getDrawable(R.drawable.brightness_bright)
                    : getDrawable(R.drawable.brightness_auto));
            DevicePreset.getInstance().setBright(mExposureBright);
        });
    }

    public void onSurfaceReady(int width, int height) {
        mWidth = width;
        mHeight = height;
        requestOpenCamera();
    }

    private void requestOpenCamera() {
        CameraServer.CameraStreamFormat format = mServer.getStreamFormats().get(useCamera);
        float scale = (float) format.size.getWidth() / format.size.getHeight(); // Inverted
        mTextureView.setLayoutParams(new LinearLayout.LayoutParams(mWidth, (int)(mWidth * scale)));
        mStream = mServer.requestOpen(format, mCallbackDelegate);
    }

    @Override
    public void onCameraStartAvailable() {
        try {
            //Abort when rapidly switching cameras
            mStream.startAsync();
        } catch (CameraAccessException e) {
            //Permission handling went wrong
            e.printStackTrace();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            findViewById(R.id.fullscreen_content).setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    public void setUIEnabled(final boolean enabled) {
        runOnUiThread(() -> {
            mShutter.setEnabled(enabled);
            float toScale = enabled ? 1 : 0.25f;
            mShutter.animate().scaleX(toScale).scaleY(toScale).setDuration(200).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            mVideo.setEnabled(enabled);
            mSwitcher.setEnabled(enabled);
            mExposure.setEnabled(enabled);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions()) {
            mMotionTracker.start();

            mTextureView = new TextureView(this);
            mPathFinder = new PathFinder(mTextureView, this);

            LinearLayout l = findViewById(R.id.fullscreen_content);
            l.addView(mTextureView, 1, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (hasPermissions()) {
            mServer.requestClose(mStream);
            mMotionTracker.stop();

            ((ViewGroup) mTextureView.getParent()).removeView(mTextureView);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServer != null) {
            mServer.requestShutdown();
        }
    }

    @Override
    public Surface getPreviewSurface() {
        return mPathFinder.previewSurface;
    }

    @Override
    public MotionTracker getMotionTracker() {
        return mMotionTracker;
    }

    @Override
    public void onCameraStarted() {
        setUIEnabled(true);
    }

    @Override
    public void onCameraStopped() {
        setUIEnabled(false);
    }

    @Override
    public void onProcessingCount(int count) {
        //setUIEnabled(count <= 3);
        setUIEnabled(count <= 0);
    }

    @Override
    public void onFocused() {
    }

    @Override
    public void onTaken(String[] paths) {
        MediaScannerConnection.scanFile(this, paths, null, null);
    }
}
