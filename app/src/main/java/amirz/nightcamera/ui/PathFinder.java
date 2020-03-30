package amirz.nightcamera.ui;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.LinearLayout;

import amirz.nightcamera.FullscreenActivity;

public class PathFinder {
    private float UiRotate = 0;

    //private GestureDetector gestureDetector;
    //private View.OnTouchListener touchListener;

    public Surface previewSurface;

    @SuppressLint("ClickableViewAccessibility")
    public PathFinder(TextureView tv, final FullscreenActivity activity) {
        /*
        gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
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

        tv.setOnTouchListener((view1, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
        */

        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            private RotateAnimation rotateAnimation;

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                previewSurface = new Surface(surface);
                activity.onSurfaceReady(width, height);
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
                // Update every frame -> Sync regular updates here
                if (rotateAnimation != null && rotateAnimation.hasEnded()) {
                    rotateAnimation = null;
                }

                if (rotateAnimation == null) {
                    float newRot = activity.mMotionTracker.getRotation();
                    if (UiRotate != newRot) {
                        rotateAnimation = new RotateAnimation(UiRotate, newRot, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                        rotateAnimation.setFillAfter(true);
                        rotateAnimation.setDuration(2 * (long) Math.abs(newRot - UiRotate));

                        activity.mSwitcher.startAnimation(rotateAnimation);
                        activity.mVideo.startAnimation(rotateAnimation);
                        UiRotate = newRot;
                    }
                }
            }
        });
    }
}
