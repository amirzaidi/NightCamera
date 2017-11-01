package amirz.nightcamera;

import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

public class PathFinder {
    private float UiRotate = 0;

    private GestureDetector gestureDetector;
    private View.OnTouchListener touchListener;
    private TextureView.SurfaceTextureListener textureListener;

    public Surface previewSurface;

    public PathFinder(TextureView view, final FullscreenActivity activity) {
        gestureDetector =  new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
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

        touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }
        };

        view.setOnTouchListener(touchListener);

        textureListener = new TextureView.SurfaceTextureListener() {
            private RotateAnimation rotateAnimation;

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                surface.setDefaultBufferSize(1440, 1080);
                previewSurface = new Surface(surface);
                activity.camera.openCamera(activity.useCamera, previewSurface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //Update every frame -> Sync regular updates here
                if (rotateAnimation != null && rotateAnimation.hasEnded()) {
                    rotateAnimation = null;
                }

                if (rotateAnimation == null) {
                    float newRot = activity.motionTracker.getRotation();
                    if (UiRotate != newRot) {
                        rotateAnimation = new RotateAnimation(UiRotate, newRot, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                        rotateAnimation.setFillAfter(true);
                        rotateAnimation.setDuration(2 * (long)Math.abs(newRot - UiRotate));
                        activity.switcher.startAnimation(rotateAnimation);
                        activity.video.startAnimation(rotateAnimation);
                        UiRotate = newRot;
                    }
                }
            }
        };

        view.setSurfaceTextureListener(textureListener);
    }
}
