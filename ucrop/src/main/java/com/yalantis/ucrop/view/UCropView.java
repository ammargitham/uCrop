package com.yalantis.ucrop.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.yalantis.ucrop.R;
import com.yalantis.ucrop.callback.CropBoundsChangeListener;
import com.yalantis.ucrop.callback.OverlayViewChangeListener;

public class UCropView extends FrameLayout {

    public static final String TAG = UCropView.class.getSimpleName();
    private GestureCropImageView mGestureCropImageView;
    private final OverlayView mViewOverlay;

    public UCropView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UCropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.ucrop_view, this, true);
        mGestureCropImageView = findViewById(R.id.image_view_crop);
        mViewOverlay = findViewById(R.id.view_overlay);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ucrop_UCropView);
        mViewOverlay.processStyledAttributes(a);
        mGestureCropImageView.processStyledAttributes(a);

        a.recycle();


        setListenersToViews();
    }

    private void setListenersToViews() {
        mGestureCropImageView.setCropBoundsChangeListener(new CropBoundsChangeListener() {
            @Override
            public void onCropAspectRatioChanged(float cropRatio) {
                mViewOverlay.setTargetAspectRatio(cropRatio);
            }
        });
        mViewOverlay.setOverlayViewChangeListener(new OverlayViewChangeListener() {
            @Override
            public void onStartCropResize() {
                mGestureCropImageView.onStartCropResize();
            }

            @Override
            public void onCropRectUpdated(RectF cropRect) {
                mGestureCropImageView.setCropRect(cropRect);
            }
        });
    }

    // private void zoomToCenter(final RectF cropRect) {
    //     final RectF to = getZoomedOverlayRect(cropRect);
    //     final float diffLeft = to.left - cropRect.left;
    //     final float diffRight = to.right - cropRect.right;
    //     float translateX = diffLeft;
    //     if (Math.abs(diffRight) < Math.abs(diffLeft)) {
    //         translateX = diffRight;
    //     }
    //     final float translateY = Math.min(to.top - cropRect.top, to.bottom - cropRect.bottom);
    //     final float scale = to.width() / cropRect.width();
    //     final ValueAnimator cropAnimator = ObjectAnimator.ofObject(new RectFEvaluator(), cropRect, to);
    //     // mGestureCropImageView.postScale(scale, cropRect.centerX(), cropRect.centerY());
    //     mGestureCropImageView.postTranslate(to.centerX() - cropRect.centerX() + (mViewOverlay.getPaddingLeft() / 2f),
    //                                         to.centerY() - cropRect.centerY() + (mViewOverlay.getPaddingTop() / 2f));
    //     cropAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
    //         @Override
    //         public void onAnimationUpdate(final ValueAnimator animation) {
    //             final RectF updatedRect = (RectF) animation.getAnimatedValue();
    //             mViewOverlay.post(new Runnable() {
    //                 @Override
    //                 public void run() {
    //                     mViewOverlay.setCropViewRect(updatedRect);
    //                 }
    //             });
    //         }
    //     });
    //     // final ValueAnimator translateAnimator = ObjectAnimator.ofObject(new PointFEvaluator(new PointF()),
    //     //                                                                 new PointF(0, 0),
    //     //                                                                 new PointF(translateX, translateY));
    //     // translateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
    //     //     final PointF temp = new PointF(0, 0);
    //     //     final float oldScale = mGestureCropImageView.getCurrentScale();
    //     //
    //     //     @Override
    //     //     public void onAnimationUpdate(final ValueAnimator animation) {
    //     //         final PointF updatedPoint = (PointF) animation.getAnimatedValue();
    //     //         mGestureCropImageView.post(new Runnable() {
    //     //             @Override
    //     //             public void run() {
    //     //                 final float deltaX = updatedPoint.x - temp.x;
    //     //                 final float deltaY = updatedPoint.y - temp.y;
    //     //                 mGestureCropImageView.postTranslate(deltaX, deltaY);
    //     //                 temp.set(updatedPoint);
    //     //             }
    //     //         });
    //     //     }
    //     // });
    //     final float toScale = scale - mGestureCropImageView.getCurrentScale();
    //     Log.d(TAG, "zoomToCenter: toScale: " + toScale);
    //     final ValueAnimator scaleAnimator = ValueAnimator.ofFloat(0, toScale);
    //     scaleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
    //         float prevScale = 0;
    //         final float oldScale = mGestureCropImageView.getCurrentScale();
    //
    //         @Override
    //         public void onAnimationUpdate(final ValueAnimator animation) {
    //             final float newScale = (float) animation.getAnimatedValue();
    //             Log.d(TAG, "onAnimationUpdate: newScale: " + newScale);
    //             mGestureCropImageView.post(new Runnable() {
    //                 @Override
    //                 public void run() {
    //                     // final float deltaScale = newScale / scale;
    //                     // Log.d(TAG, "run: deltaScale: " + deltaScale);
    //                     mGestureCropImageView.postScale(oldScale + newScale, cropRect.centerX(), cropRect.centerY());
    //                     // mGestureCropImageView.
    //                     prevScale = newScale;
    //                 }
    //             });
    //         }
    //     });
    //     final AnimatorSet animatorSet = new AnimatorSet();
    //     animatorSet.playTogether(cropAnimator, scaleAnimator/*, translateAnimator, */);
    //     animatorSet.addListener(new AnimatorListenerAdapter() {
    //         @Override
    //         public void onAnimationEnd(final Animator animation) {
    //             // mGestureCropImageView.setCropRect(to);
    //         }
    //     });
    //     animatorSet.setDuration(400);
    //     animatorSet.start();
    // }

    public RectF getZoomedOverlayRect(final RectF cropRect) {
        float overlayWidth = mViewOverlay.mThisWidth;
        float overlayHeight = mViewOverlay.mThisHeight;
        float scaleY = overlayHeight / cropRect.height();
        float scaleX = overlayWidth / cropRect.width();
        float scale = Math.min(scaleX, scaleY);
        float newHeight = scale * cropRect.height();
        float newWidth = scale * cropRect.width();
        final float halfWidth = (overlayWidth - newWidth) / 2;
        final float halfHeight = (overlayHeight - newHeight) / 2;
        final float left = mViewOverlay.getPaddingLeft() + halfWidth;
        final float right = left + newWidth;
        final float top = mViewOverlay.getPaddingTop() + halfHeight;
        final float bottom = top + newHeight;
        return new RectF(left, top, right, bottom);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @NonNull
    public GestureCropImageView getCropImageView() {
        return mGestureCropImageView;
    }

    @NonNull
    public OverlayView getOverlayView() {
        return mViewOverlay;
    }

    /**
     * @param savedCropRect from previous edit.
     * @author azri92
     */
    public void setSavedState(RectF savedCropRect) {
        mViewOverlay.setSavedCropRect(savedCropRect);
    }

    /**
     * Method for reset state for UCropImageView such as rotation, scale, translation.
     * Be careful: this method recreate UCropImageView instance and reattach it to layout.
     */
    public void resetCropImageView() {
        //TODO: This actually causes a null pointer exception
        removeView(mGestureCropImageView);
        mGestureCropImageView = new GestureCropImageView(getContext());
        setListenersToViews();
        addView(mGestureCropImageView, 0);
    }
}