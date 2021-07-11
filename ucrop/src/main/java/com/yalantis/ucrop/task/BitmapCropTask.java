package com.yalantis.ucrop.task;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.model.CropParameters;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.model.ImageState;
import com.yalantis.ucrop.util.AppExecutors;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.FileUtils;
import com.yalantis.ucrop.util.ImageHeaderParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
public class BitmapCropTask {

    private static final String TAG = "BitmapCropTask";

    private static final String CONTENT_SCHEME = "content";

    private final WeakReference<Context> mContext;

    private Bitmap mViewBitmap;

    private final RectF mCropRect;
    private final RectF mCurrentImageRect;

    private float mCurrentScale;
    private final float mCurrentAngle;
    private final int mMaxResultImageSizeX;
    private final int mMaxResultImageSizeY;

    private final Bitmap.CompressFormat mCompressFormat;
    private final int mCompressQuality;
    private final String mImageInputPath;
    private final String mImageOutputPath;
    private final Uri mImageInputUri;
    private final Uri mImageOutputUri;
    private final BitmapCropCallback mCropCallback;

    private int mCroppedImageWidth;
    private int mCroppedImageHeight;
    private int cropOffsetX;
    private int cropOffsetY;

    private final float[] imageMatrixValues;

    public BitmapCropTask(@NonNull Context context,
                          @Nullable Bitmap viewBitmap,
                          @NonNull ImageState imageState,
                          @NonNull CropParameters cropParameters,
                          @Nullable BitmapCropCallback cropCallback) {

        mContext = new WeakReference<>(context);

        mViewBitmap = viewBitmap;
        mCropRect = imageState.getCropRect();
        mCurrentImageRect = imageState.getCurrentImageRect();

        mCurrentScale = imageState.getCurrentScale();
        mCurrentAngle = imageState.getCurrentAngle();
        mMaxResultImageSizeX = cropParameters.getMaxResultImageSizeX();
        mMaxResultImageSizeY = cropParameters.getMaxResultImageSizeY();

        mCompressFormat = cropParameters.getCompressFormat();
        mCompressQuality = cropParameters.getCompressQuality();

        mImageInputPath = cropParameters.getImageInputPath();
        mImageOutputPath = cropParameters.getImageOutputPath();
        mImageInputUri = cropParameters.getContentImageInputUri();
        mImageOutputUri = cropParameters.getContentImageOutputUri();
        final ExifInfo mExifInfo = cropParameters.getExifInfo();

        // azri92 - Get image matrix & crop rect values to be included in result
        imageMatrixValues = new float[9];
        imageState.getCurrentImageMatrix().getValues(imageMatrixValues);

        mCropCallback = cropCallback;
    }

    public void execute() {
        AppExecutors.getInstance().tasksThread().execute(() -> {
            try {
                if (mViewBitmap == null) {
                    throw new NullPointerException("ViewBitmap is null");
                } else if (mViewBitmap.isRecycled()) {
                    throw new NullPointerException("ViewBitmap is recycled");
                } else if (mCurrentImageRect.isEmpty()) {
                    throw new NullPointerException("CurrentImageRect is empty");
                }

                if (mImageOutputUri == null) {
                    throw new NullPointerException("ImageOutputUri is null");
                }
                crop();
                mViewBitmap = null;
                if (mCropCallback != null) {
                    AppExecutors.getInstance().mainThread().execute(() -> mCropCallback.onBitmapCropped(
                            mImageOutputUri,
                            cropOffsetX,
                            cropOffsetY,
                            mCroppedImageWidth,
                            mCroppedImageHeight,
                            imageMatrixValues,
                            mCropRect
                    ));
                }
            } catch (Throwable throwable) {
                if (mCropCallback != null) {
                    AppExecutors.getInstance().mainThread().execute(() -> mCropCallback.onCropFailure(throwable));
                }
            }
        });
    }

    private void crop() throws IOException {
        Context context = mContext.get();
        if (context == null) {
            return;
        }

        // Downsize if needed
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            float cropWidth = mCropRect.width() / mCurrentScale;
            float cropHeight = mCropRect.height() / mCurrentScale;

            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {

                float scaleX = mMaxResultImageSizeX / cropWidth;
                float scaleY = mMaxResultImageSizeY / cropHeight;
                float resizeScale = Math.min(scaleX, scaleY);

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                        mViewBitmap,
                        Math.round(mViewBitmap.getWidth() * resizeScale),
                        Math.round(mViewBitmap.getHeight() * resizeScale),
                        false
                );
                if (mViewBitmap != resizedBitmap) {
                    mViewBitmap.recycle();
                }
                mViewBitmap = resizedBitmap;
                mCurrentScale /= resizeScale;
            }
        }

        // Rotate if needed
        if (mCurrentAngle != 0) {
            Matrix tempMatrix = new Matrix();
            tempMatrix.setRotate(mCurrentAngle, mViewBitmap.getWidth() / 2F, mViewBitmap.getHeight() / 2F);
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    mViewBitmap,
                    0,
                    0,
                    mViewBitmap.getWidth(),
                    mViewBitmap.getHeight(),
                    tempMatrix,
                    true
            );
            if (mViewBitmap != rotatedBitmap) {
                mViewBitmap.recycle();
            }
            mViewBitmap = rotatedBitmap;
        }

        cropOffsetX = Math.round((mCropRect.left - mCurrentImageRect.left) / mCurrentScale);
        cropOffsetY = Math.round((mCropRect.top - mCurrentImageRect.top) / mCurrentScale);
        mCroppedImageWidth = Math.round(mCropRect.width() / mCurrentScale);
        mCroppedImageHeight = Math.round(mCropRect.height() / mCurrentScale);

        boolean shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight);
        Log.i(TAG, "Should crop: " + shouldCrop);

        if (!shouldCrop) {
            FileUtils.copyFile(context, mImageInputUri, mImageOutputUri);
            return;
        }
        saveImage(Bitmap.createBitmap(mViewBitmap, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight));
        if (mCompressFormat.equals(Bitmap.CompressFormat.JPEG)) {
            copyExifForOutputFile(context);
        }
    }

    private void copyExifForOutputFile(Context context) throws IOException {
        boolean hasImageInputUriContentSchema = BitmapLoadUtils.hasContentScheme(mImageInputUri);
        boolean hasImageOutputUriContentSchema = BitmapLoadUtils.hasContentScheme(mImageOutputUri);
        /*
         * ImageHeaderParser.copyExif with output uri as a parameter
         * uses ExifInterface constructor with FileDescriptor param for overriding output file exif info,
         * which doesn't support ExitInterface.saveAttributes call for SDK lower than 21.
         *
         * See documentation for ImageHeaderParser.copyExif and ExifInterface.saveAttributes implementation.
         */
        if (hasImageInputUriContentSchema && hasImageOutputUriContentSchema) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ImageHeaderParser.copyExif(context, mCroppedImageWidth, mCroppedImageHeight, mImageInputUri, mImageOutputUri);
            } else {
                Log.e(TAG, "It is not possible to write exif info into file represented by \"content\" Uri if Android < LOLLIPOP");
            }
        } else if (hasImageInputUriContentSchema) {
            ImageHeaderParser.copyExif(context, mCroppedImageWidth, mCroppedImageHeight, mImageInputUri, mImageOutputPath);
        } else if (hasImageOutputUriContentSchema) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ExifInterface originalExif = new ExifInterface(mImageInputPath);
                ImageHeaderParser.copyExif(context, originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputUri);
            } else {
                Log.e(TAG, "It is not possible to write exif info into file represented by \"content\" Uri if Android < LOLLIPOP");
            }
        } else {
            ExifInterface originalExif = new ExifInterface(mImageInputPath);
            ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath);
        }
    }

    private void saveImage(@NonNull Bitmap croppedBitmap) {
        Context context = mContext.get();
        if (context == null) {
            return;
        }

        OutputStream outputStream = null;
        ByteArrayOutputStream outStream = null;
        try {
            outputStream = context.getContentResolver().openOutputStream(mImageOutputUri);
            outStream = new ByteArrayOutputStream();
            croppedBitmap.compress(mCompressFormat, mCompressQuality, outStream);
            outputStream.write(outStream.toByteArray());
            croppedBitmap.recycle();
        } catch (IOException exc) {
            Log.e(TAG, exc.getLocalizedMessage());
        } finally {
            BitmapLoadUtils.close(outputStream);
            BitmapLoadUtils.close(outStream);
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private boolean shouldCrop(int width, int height) {
        int pixelError = 1;
        pixelError += Math.round(Math.max(width, height) / 1000f);
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0)
                || Math.abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || Math.abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || Math.abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || Math.abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0;
    }
}
