package amirz.nightcamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

import java.util.Arrays;

public class FullscreenActivity extends AppCompatActivity {

    private MotionTracker motionTracker;

    private TextureView pathFinder;
    private FloatingActionButton switcher;
    private FloatingActionButton shutter;
    private FloatingActionButton video;

    private CameraManager cameraManager;
    private String[] cameras;
    private CameraCharacteristics[] cameraCharacteristics;
    private StreamConfigurationMap[] streamConfigurationMaps;
    private Size[][] outputYuvSizes;
    private Size[][] privateSizes;

    public static int useCamera = 0;
    //private int usePreviewSize = 0;
    private int useCaptureSize = 0;
    private float UiRotate = 0;

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession captureSession;

    public Surface previewSurface;
    public ImageReader previewSurfaceReader;
    public ImageWriter reprocessSurfaceWriter;
    public ImageReader reprocessedSaver;

    private PreviewImageSaver imageSaver;

    private Handler backgroundPreviewHandler;
    private HandlerThread backgroundPreviewThread;

    private Handler backgroundCaptureHandler;
    private HandlerThread backgroundCaptureThread;

    private static final int REQUEST_PERMISSIONS = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        motionTracker = new MotionTracker(this);
        motionTracker.start();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            initParameters();
        } else {
            ActivityCompat.requestPermissions(FullscreenActivity.this, new String[] {
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initParameters();
            } else {
                finish();
            }
        }
    }

    TextureView.SurfaceTextureListener pathFinderTextureListener = new TextureView.SurfaceTextureListener() {
        private RotateAnimation rotateAnimation;

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            //Update every frame -> Sync regular updates here
            if (rotateAnimation != null && rotateAnimation.hasEnded()) {
                rotateAnimation = null;
            }

            if (rotateAnimation == null) {
                float newRot = motionTracker.getRotation();
                if (UiRotate != newRot) {
                    rotateAnimation = new RotateAnimation(UiRotate, newRot, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    rotateAnimation.setFillAfter(true);
                    rotateAnimation.setDuration(2 * (long)Math.abs(newRot - UiRotate));
                    switcher.startAnimation(rotateAnimation);
                    video.startAnimation(rotateAnimation);
                    UiRotate = newRot;
                }
            }
        }
    };

    private void initParameters() {
        final Context context = this;
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameras = cameraManager.getCameraIdList();
            cameraCharacteristics = new CameraCharacteristics[cameras.length];

            streamConfigurationMaps = new StreamConfigurationMap[cameras.length];
            outputYuvSizes = new Size[cameras.length][];
            privateSizes = new Size[cameras.length][];
            for (int i = 0; i < cameras.length; i++) {
                cameraCharacteristics[i] = cameraManager.getCameraCharacteristics(cameras[i]);
                streamConfigurationMaps[i] = cameraCharacteristics[i].get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                outputYuvSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.YUV_420_888);
                privateSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.PRIVATE);
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
            finish();
        }

        imageSaver = new PreviewImageSaver(motionTracker, this);

        switcher = (FloatingActionButton) findViewById(R.id.switcher);
        shutter = (FloatingActionButton) findViewById(R.id.shutter);
        video = (FloatingActionButton) findViewById(R.id.video);

        pathFinder = (TextureView) findViewById(R.id.pathfinder);
        pathFinder.setSurfaceTextureListener(pathFinderTextureListener);
        pathFinder.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }

            private GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                private static final int SWIPE_THRESHOLD = 100;
                private static final int SWIPE_VELOCITY_THRESHOLD = 100;

                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                Log.d("Swipe", "Right");
                            } else {
                                Log.d("Swipe", "Left");
                            }
                            return true;
                        }
                    } else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            Log.d("Swipe", "Bottom");
                        } else {
                            Log.d("Swipe", "Top");
                        }
                        return true;
                    }
                    return false;
                }
            });
        });

        backgroundPreviewThread = new HandlerThread("preview");
        backgroundPreviewThread.start();
        backgroundPreviewHandler = new Handler(backgroundPreviewThread.getLooper());

        backgroundCaptureThread = new HandlerThread("capture");
        backgroundCaptureThread.start();
        backgroundCaptureHandler = new Handler(backgroundCaptureThread.getLooper());

        switcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switcher.setEnabled(false);
                backgroundPreviewHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        useCamera ^= 1;
                        closeCamera();
                        //imageSaver.reset();
                        openCamera();
                        afterCaptureAttempt();
                    }
                });
            }
        });

        shutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shutter.setEnabled(false);
                backgroundCaptureHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startCapture();
                    }
                });
            }
        });

        video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

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

    private void openCamera() {
        try {
            cameraManager.openCamera(cameras[useCamera], new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice camera) {
                    cameraDevice = camera;

                    // Preview surface
                    SurfaceTexture texture = pathFinder.getSurfaceTexture();
                    texture.setDefaultBufferSize(1440, 1080);
                    previewSurface = new Surface(texture);

                    Size priv = privateSizes[useCamera][useCaptureSize];
                    previewSurfaceReader = ImageReader.newInstance(
                            priv.getWidth(),
                            priv.getHeight(),
                            ImageFormat.PRIVATE, Constants.zslImageSaveCount);
                    previewSurfaceReader.setOnImageAvailableListener(imageSaver, imageSaver.backgroundSaveHandler);

                    Size yuv = outputYuvSizes[useCamera][useCaptureSize];
                    reprocessedSaver = ImageReader.newInstance(
                            yuv.getWidth(),
                            yuv.getHeight(),
                            ImageFormat.YUV_420_888, Constants.zslImageSaveCount);

                    try {
                        cameraDevice.createReprocessableCaptureSession(
                                new InputConfiguration(priv.getWidth(), priv.getHeight(), ImageFormat.PRIVATE),
                                Arrays.asList(previewSurface, previewSurfaceReader.getSurface(), reprocessedSaver.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                captureSession = cameraCaptureSession;
                                reprocessSurfaceWriter = ImageWriter.newInstance(captureSession.getInputSurface(), Constants.zslImageReprocessCount);
                                startPreview();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        switcher.setEnabled(true);
                                    }
                                });
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            }
                        }, null);
                    }
                    catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    closeCamera();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    closeCamera();
                }
            }, backgroundPreviewHandler);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != reprocessSurfaceWriter) {
            reprocessSurfaceWriter.close();
            reprocessSurfaceWriter = null;
        }

        if (null != previewSurfaceReader) {
            previewSurfaceReader.close();
            previewSurfaceReader = null;
        }

        if (null != captureSession) {
            captureSession.close();
            captureSession = null;
        }

        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startPreview() {
        try {
            CaptureRequest.Builder previewRequestBuilder = CaptureSettings.getPreviewRequestBuilder(cameraDevice);
            previewRequestBuilder.addTarget(previewSurface); //preview screen
            previewRequestBuilder.addTarget(previewSurfaceReader.getSurface()); //ZSL saver

            captureSession.stopRepeating();
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                //AtomicInteger counter = new AtomicInteger(0);

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //CaptureSettings.previewResult = result; //save metadata
                    imageSaver.lastRes = result;
                }
            }, backgroundPreviewHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startCapture() {
        if (cameraDevice != null) {
            try {
                imageSaver.requestedPic = true;
                /*List<CaptureRequest> requests = CaptureSettings.getCaptureRequests(cameraDevice, Arrays.asList(previewSurface, previewSurfaceReader.getSurface()));
                imageSaver.count = requests.size();
                imageSaver.waiter.acquireUninterruptibly();

                imageSaver.motionStart = motionTracker.snapshot();
                captureSession.captureBurst(requests, new CameraCaptureSession.CaptureCallback() {
                    private long mTimestamp;
                    private long mFrameNumber;

                    @Override
                    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                        super.onCaptureStarted(session, request, timestamp, frameNumber);
                        mTimestamp = timestamp;
                        mFrameNumber = frameNumber;
                    }

                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        imageSaver.metadatas.add(new ImageMetadata(mTimestamp, mFrameNumber, request, result, motionTracker.snapshot()));
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        Log.d("onCapture", "Failed " + mTimestamp + " " + mFrameNumber);
                    }

                    @Override
                    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                        super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                        Log.d("onCapture", "SeqComplete " + sequenceId + " " + frameNumber);
                        imageSaver.waiter.release();
                    }

                    @Override
                    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                        super.onCaptureSequenceAborted(session, sequenceId);
                        Log.d("onCapture", "SeqAbort " + sequenceId);
                    }

                    @Override
                    public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                        super.onCaptureBufferLost(session, request, target, frameNumber);
                        Log.d("onCapture", "BufLost " + frameNumber);
                    }
                }, backgroundCaptureHandler);*/
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void afterCaptureAttempt() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            shutter.setEnabled(true);
            //Show animation
            }
        });
    }

    private boolean paused = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (paused) {
            paused = false;
            motionTracker.start();
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        motionTracker.stop();
        closeCamera();
        paused = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageSaver.close();
        backgroundPreviewThread.quitSafely();
        backgroundCaptureThread.quitSafely();
        try {
            backgroundPreviewThread.join();
            backgroundPreviewThread = null;
            backgroundPreviewHandler = null;

            backgroundCaptureThread.join();
            backgroundCaptureThread = null;
            backgroundCaptureHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
