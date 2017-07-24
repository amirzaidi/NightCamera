package amirz.nightcamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
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
import android.util.Range;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
    //private Size[][] outputPreviewSizes;
    //private Size[][] outputPrivateSizes;
    //private Size[][] outputJpegSizes;
    private Size[][] outputYuvSizes;
    //private Size[][] outputRawSizes;

    public static int useCamera = 0;
    //private int usePreviewSize = 0;
    private int useCaptureSize = 0;
    private float UiRotate = 0;

    public Surface previewSurface;

    public ImageReader captureSurfaceReader;
    public ImageReader reprocessSurfaceReader;

    private ImagePreview imagePreview;
    private ImageSaver imageSaver;

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession captureSession;

    private Handler backgroundPreviewHandler;
    private HandlerThread backgroundPreviewThread;

    private Handler backgroundCaptureHandler;
    private HandlerThread backgroundCaptureThread;

    private Handler backgroundReprocessHandler;
    private HandlerThread backgroundReprocessThread;

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
            //outputPreviewSizes = new Size[cameras.length][];
            //outputPrivateSizes = new Size[cameras.length][];
            //outputJpegSizes = new Size[cameras.length][];
            outputYuvSizes = new Size[cameras.length][];
            //outputRawSizes = new Size[cameras.length][];
            for (int i = 0; i < cameras.length; i++) {
                cameraCharacteristics[i] = cameraManager.getCameraCharacteristics(cameras[i]);

                int[] cap = cameraCharacteristics[i].get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                for (int j = 0; j < cap.length; j++) {
                    Log.d("CameraCap", i + " has " + cap[j]);
                }

                streamConfigurationMaps[i] = cameraCharacteristics[i].get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //Object o = cameraCharacteristics[i].get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE); //100 to 3199

                //outputPreviewSizes[i] = streamConfigurationMaps[i].getOutputSizes(SurfaceTexture.class);
                //outputPrivateSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.PRIVATE);
                //outputJpegSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.JPEG);
                outputYuvSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.YUV_420_888);
                //outputRawSizes[i] = streamConfigurationMaps[i].getOutputSizes(ImageFormat.RAW_SENSOR);
                //Log.d("CameraInit", "Camera " + i + ", Jpeg sizes " + outputJpegSizes[i].length + ",  Private sizes " + outputPrivateSizes[i].length);
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
            finish();
        }

        imagePreview = new ImagePreview(this);
        imageSaver = new ImageSaver(motionTracker, this);

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

        backgroundReprocessThread = new HandlerThread("reprocess");
        backgroundReprocessThread.start();
        backgroundReprocessHandler = new Handler(backgroundReprocessThread.getLooper());

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

                    Size yuv = outputYuvSizes[useCamera][useCaptureSize];
                    captureSurfaceReader = ImageReader.newInstance(
                            yuv.getWidth(),
                            yuv.getHeight(),
                            //1440, 1080,
                            //2304, 1728,
                            ImageFormat.YUV_420_888, 11);

                    captureSurfaceReader.setOnImageAvailableListener(imageSaver, imageSaver.backgroundSaveHandler);

                    /*Size priv = outputPrivateSizes[useCamera][useCaptureSize];

                    captureSurfaceReader = ImageReader.newInstance(
                            priv.getWidth(),
                            priv.getHeight(),
                            ImageFormat.PRIVATE, 11);

                    captureSurfaceReader.setOnImageAvailableListener(imagePreview, backgroundCaptureHandler);*/

                    /*reprocessSurfaceReader = ImageReader.newInstance(
                            outputJpegSizes[useCamera][useCaptureSize].getWidth(),
                            outputJpegSizes[useCamera][useCaptureSize].getHeight(),
                            ImageFormat.JPEG, 11);

                    reprocessSurfaceReader.setOnImageAvailableListener(imageSaver, imageSaver.backgroundCopyHandler);*/

                    try {
                        cameraDevice.createCaptureSession(Arrays.asList(previewSurface, captureSurfaceReader.getSurface()), new CameraCaptureSession.StateCallback() {
                        /*cameraDevice.createReprocessableCaptureSession(
                                new InputConfiguration(priv.getWidth(), priv.getHeight(), ImageFormat.PRIVATE),
                                Arrays.asList(previewSurface, captureSurfaceReader.getSurface(), reprocessSurfaceReader.getSurface()), new CameraCaptureSession.StateCallback() {*/
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                captureSession = cameraCaptureSession;
                                //imagePreview.zslWriter = ImageWriter.newInstance(captureSurfaceReader.getSurface(), 11);

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
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startPreview() {
        try {
            CaptureRequest.Builder previewRequestBuilder = CaptureSettings.getPreviewRequestBuilder(cameraDevice);
            previewRequestBuilder.addTarget(previewSurface);
            //previewRequestBuilder.addTarget(captureSurfaceReader.getSurface());
            //previewRequestBuilder.addTarget(reprocessSurfaceReader.getSurface());

            captureSession.stopRepeating();
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                //AtomicInteger counter = new AtomicInteger(0);

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //imagePreview.result = result;

                    /*Image img = captureSurfaceReader.acquireLatestImage();
                    if (img != null) {
                        imagePreview.images.add(new ImagePreview.ImageItem(img, result));

                        if (imagePreview.images.size() > 11) {
                            try {
                                imagePreview.images.take().image.close();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }*/

                    //Log.d("startPreview", "onCaptureCompleted " + counter.incrementAndGet());
                    /*try {
                        //if (imagePreview.results.size() > 9)
                            //imagePreview.results.take();

                        //imagePreview.results.add(result);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                    CaptureSettings.previewResult = result;
                    //Log.d("PicSettings", "Using night mode - exposure " + result.get(CaptureResult.SENSOR_EXPOSURE_TIME) + ", frame duration " + result.get(CaptureResult.SENSOR_FRAME_DURATION));
                    /*CaptureSettings.exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    CaptureSettings.frameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
                    CaptureSettings.sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);*/
                }
            }, backgroundPreviewHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startCapture() {
        if (cameraDevice != null) {
            imagePreview.takeImage = true;
            try {
                /*imageSaver.rotate = motionTracker.getRotation();
                if (FullscreenActivity.useCamera == 1 && imageSaver.rotate % 180 == 0)
                    imageSaver.rotate = 180 - imageSaver.rotate;*/

                //Image image = captureSurfaceReader.acquireLatestImage();
                //long stamp = image.getTimestamp();

                /*Image image = imagePreview.zslWriter.dequeueInputImage();
                TotalCaptureResult res = imagePreview.results.peek();

                CaptureRequest.Builder builder = cameraDevice.createReprocessCaptureRequest(res);*/

                List<CaptureRequest> requests = CaptureSettings.getCaptureRequests(cameraDevice, Arrays.asList(previewSurface, captureSurfaceReader.getSurface()));
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

                        /*try {
                            CaptureRequest.Builder builder = cameraDevice.createReprocessCaptureRequest(result);
                            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
                            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                            builder.addTarget(reprocessSurfaceReader.getSurface());
                            captureSession.capture(builder.build(), null, backgroundReprocessHandler);
                        }
                        catch (CameraAccessException e) {
                            e.printStackTrace();
                        }*/
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
                }, backgroundCaptureHandler);
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
        backgroundReprocessThread.quitSafely();
        try {
            backgroundPreviewThread.join();
            backgroundPreviewThread = null;
            backgroundPreviewHandler = null;

            backgroundCaptureThread.join();
            backgroundCaptureThread = null;
            backgroundCaptureHandler = null;

            backgroundReprocessThread.join();
            backgroundReprocessThread = null;
            backgroundReprocessHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
