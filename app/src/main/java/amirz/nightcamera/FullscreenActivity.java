package amirz.nightcamera;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import static android.support.v4.app.NotificationCompat.PRIORITY_MAX;

public class FullscreenActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 200;

    public MotionTracker motionTracker;
    public CameraWrapper camera;
    public int useCamera = 0;

    private PathFinder pathFinder;

    public FloatingActionButton switcher;
    public FloatingActionButton shutter;
    public FloatingActionButton video;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            init();
        else
            ActivityCompat.requestPermissions(FullscreenActivity.this, new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                init();
            else
                finish();
    }

    private void init() {
        final FullscreenActivity instance = this;

        motionTracker = new MotionTracker(this);

        try {
            camera = new CameraWrapper(this);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            finish();
        }

        switcher = (FloatingActionButton) findViewById(R.id.switcher);
        shutter = (FloatingActionButton) findViewById(R.id.shutter);
        video = (FloatingActionButton) findViewById(R.id.video);

        switcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setUIEnabled(false);

                useCamera ^= 1;
                camera.openCamera(useCamera, pathFinder.previewSurface);
            }
        });

        shutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setUIEnabled(false);
                camera.zslQueue.takeAndProcessAsync(instance);
            }
        });

        video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*start
                video*/
            }
        });
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
                shutter.setEnabled(enabled);
                int toScale = enabled ? 1 : 0;
                shutter.animate().scaleX(toScale).scaleY(toScale).setDuration(200).setInterpolator(new AccelerateDecelerateInterpolator()).start();
                video.setEnabled(enabled);
                switcher.setEnabled(enabled);
            }
        });
    }

    private TextureView tv;

    @Override
    protected void onResume() {
        super.onResume();
        motionTracker.start();

        tv = new TextureView(this);
        addContentView(tv, new ViewGroup.LayoutParams(1080, 1440));

        pathFinder = new PathFinder(tv, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera.closeCamera();
        motionTracker.stop();

        ((ViewGroup) tv.getParent()).removeView(tv);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (camera != null) {
            camera.closeCamera();
            camera.close();
            camera = null;
        }
    }
}
