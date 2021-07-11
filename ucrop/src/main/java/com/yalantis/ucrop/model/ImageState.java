package com.yalantis.ucrop.model;

import android.graphics.Matrix;
import android.graphics.RectF;

/**
 * Created by Oleksii Shliama [https://github.com/shliama] on 6/21/16.
 */
public class ImageState {

    private final RectF mCropRect;
    private final RectF mCurrentImageRect;
    private final Matrix currentImageMatrix;
    private final float mCurrentScale;
    private final float mCurrentAngle;

    public ImageState(RectF cropRect, RectF currentImageRect, Matrix currentImageMatrix, float currentScale, float currentAngle) {
        mCropRect = cropRect;
        mCurrentImageRect = currentImageRect;
        this.currentImageMatrix = currentImageMatrix;
        mCurrentScale = currentScale;
        mCurrentAngle = currentAngle;
    }

    public RectF getCropRect() {
        return mCropRect;
    }

    public RectF getCurrentImageRect() {
        return mCurrentImageRect;
    }

    public Matrix getCurrentImageMatrix() {
        return currentImageMatrix;
    }

    public float getCurrentScale() {
        return mCurrentScale;
    }

    public float getCurrentAngle() {
        return mCurrentAngle;
    }
}
