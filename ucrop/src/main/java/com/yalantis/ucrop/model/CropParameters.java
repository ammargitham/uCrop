package com.yalantis.ucrop.model;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * Created by Oleksii Shliama [https://github.com/shliama] on 6/21/16.
 */
public class CropParameters {

    private int mMaxResultImageSizeX, mMaxResultImageSizeY;

    private Bitmap.CompressFormat mCompressFormat;
    private int mCompressQuality;
    private Uri mImageInputPath, mImageOutputPath;
    private ExifInfo mExifInfo;


    public CropParameters(int maxResultImageSizeX,
                          int maxResultImageSizeY,
                          Bitmap.CompressFormat compressFormat,
                          int compressQuality,
                          Uri imageInputPath,
                          Uri imageOutputPath,
                          ExifInfo exifInfo) {
        mMaxResultImageSizeX = maxResultImageSizeX;
        mMaxResultImageSizeY = maxResultImageSizeY;
        mCompressFormat = compressFormat;
        mCompressQuality = compressQuality;
        mImageInputPath = imageInputPath;
        mImageOutputPath = imageOutputPath;
        mExifInfo = exifInfo;
    }

    public int getMaxResultImageSizeX() {
        return mMaxResultImageSizeX;
    }

    public int getMaxResultImageSizeY() {
        return mMaxResultImageSizeY;
    }

    public Bitmap.CompressFormat getCompressFormat() {
        return mCompressFormat;
    }

    public int getCompressQuality() {
        return mCompressQuality;
    }

    public Uri getImageInputPath() {
        return mImageInputPath;
    }

    public Uri getImageOutputPath() {
        return mImageOutputPath;
    }

    public ExifInfo getExifInfo() {
        return mExifInfo;
    }

}
