package com.yalantis.ucrop.sample;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.view.UCropView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */
public class ResultActivity extends BaseActivity {

    private static final String TAG = "ResultActivity";
    // private static final String CHANNEL_ID = "3000";
    // private static final int DOWNLOAD_NOTIFICATION_ID_DONE = 911;

    public static final String EXTRA_IS_LOCAL_IMAGE = "extra_is_local_image";
    private boolean isLocalImage;

    public static void startWithUri(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(context, ResultActivity.class);
        intent.setData(uri);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Uri uri = getIntent().getData();
        int width = 0;
        int height = 0;
        if (uri != null) {
            try {
                UCropView uCropView = findViewById(R.id.ucrop);
                uCropView.getCropImageView().setImageUri(uri, null);
                uCropView.getOverlayView().setShowCropFrame(false);
                uCropView.getOverlayView().setShowCropGrid(false);
                uCropView.getOverlayView().setDimmedColor(Color.TRANSPARENT);
            } catch (Exception e) {
                Log.e(TAG, "setImageUri", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            InputStream is = null;
            try {
                is = getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(is, null, options);
            } catch (FileNotFoundException e) {
                Log.d(TAG, e.getMessage(), e);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }

            isLocalImage = getIntent().getBooleanExtra(EXTRA_IS_LOCAL_IMAGE, false);

            width = options.outWidth;
            height = options.outHeight;
        }

        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getString(R.string.format_crop_result_d_d, width, height));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_result, menu);
        // azri92 - only show edit button if source image is from local
        menu.findItem(R.id.menu_edit).setVisible(isLocalImage);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_download) {
            saveCroppedImage();
        } else if (item.getItemId() == R.id.menu_edit) {
            setResult(SampleActivity.RESULT_EDIT);
            finish();
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveCroppedImage() {
        Uri imageUri = getIntent().getData();
        if (BitmapLoadUtils.hasContentScheme(imageUri)) {
            Toast.makeText(ResultActivity.this, getString(R.string.toast_already_saved), Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(ResultActivity.this, getString(R.string.toast_unexpected_error), Toast.LENGTH_SHORT).show();
        }
    }
}
