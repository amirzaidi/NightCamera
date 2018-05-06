package amirz.nightcamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.renderscript.RenderScript;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import amirz.nightcamera.device.DevicePreset;
import amirz.nightcamera.motion.MotionTracker;
import amirz.nightcamera.server.CameraServer;
import amirz.nightcamera.server.CameraStream;
import amirz.nightcamera.server.CameraStreamCallbacks;
import amirz.nightcamera.ui.MainThreadDelegate;
import amirz.nightcamera.ui.PathFinder;

public class FullscreenActivity extends AppCompatActivity implements CameraStreamCallbacks {

    private static final int REQUEST_PERMISSIONS = 200;

    private MainThreadDelegate mCallbackDelegate;
    public MotionTracker mMotionTracker;
    //public CameraWrapper camera;
    private CameraServer mServer;
    private CameraStream mStream;
    public int useCamera = 0;

    private RenderScript mRs;

    private PathFinder mPathFinder;

    public FloatingActionButton mSwitcher;
    public FloatingActionButton mShutter;
    public FloatingActionButton mVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        if (DevicePreset.getInstance() == null) {
            Toast.makeText(this, "Device not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (hasPermissions()) {
            onCreateImpl();
        } else {
            ActivityCompat.requestPermissions(FullscreenActivity.this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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

        setUIEnabled(false);

        mSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStream != null) {
                    mServer.requestClose(mStream);
                }

                useCamera ^= 1;
                mStream = mServer.requestOpen(mServer.getStreamFormats().get(useCamera), mCallbackDelegate);
            }
        });

        mShutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setUIEnabled(false);
                mStream.takeAndProcessAsync();
            }
        });

        mVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*start
                mVideo*/
            }
        });

        mRs = RenderScript.create(this);
    }

    public void onSurfaceReady() {
        mStream = mServer.requestOpen(mServer.getStreamFormats().get(useCamera), mCallbackDelegate);
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShutter.setEnabled(enabled);
                float toScale = enabled ? 1 : 0.25f;
                mShutter.animate().scaleX(toScale).scaleY(toScale).setDuration(200).setInterpolator(new AccelerateDecelerateInterpolator()).start();
                mVideo.setEnabled(enabled);
                mSwitcher.setEnabled(enabled);
            }
        });
    }

    private TextureView tv;
    private boolean mPaused = true;

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions()) {
            if (mPaused) {
                mMotionTracker.start();

                tv = new TextureView(this);
                addContentView(tv, new ViewGroup.LayoutParams(1080, 1440));

                mPathFinder = new PathFinder(tv, this);
            }
            mPaused = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (hasPermissions()) {
            mPaused = true;
            mServer.requestClose(mStream);
            mMotionTracker.stop();

            ((ViewGroup) tv.getParent()).removeView(tv);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mServer != null) {
            mServer.requestShutdown();
        }

        RenderScript.releaseAllContexts();
    }

    @Override
    public Surface getPreviewSurface() {
        return mPathFinder.previewSurface;
    }

    @Override
    public RenderScript getRsInstance() {
        return mRs;
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
