package com.yalantis.ucrop;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;

import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.model.AspectRatio;
import com.yalantis.ucrop.view.CropImageView;
import com.yalantis.ucrop.view.GestureCropImageView;
import com.yalantis.ucrop.view.OverlayView;
import com.yalantis.ucrop.view.TransformImageView;
import com.yalantis.ucrop.view.UCropView;
import com.yalantis.ucrop.view.widget.AspectRatioTextView;
import com.yalantis.ucrop.view.widget.HorizontalProgressWheelView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;

public class UCropFragment extends Fragment {

    public static final int DEFAULT_COMPRESS_QUALITY = 90;
    public static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;

    public static final int NONE = 0;
    public static final int SCALE = 1;
    public static final int ROTATE = 2;
    public static final int ALL = 3;

    @IntDef({NONE, SCALE, ROTATE, ALL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface GestureTypes {
    }

    public static final String TAG = "UCropFragment";

    private static final long CONTROLS_ANIMATION_DURATION = 50;
    private static final int TABS_COUNT = 3;
    private static final int SCALE_WIDGET_SENSITIVITY_COEFFICIENT = 15000;
    private static final int ROTATE_WIDGET_SENSITIVITY_COEFFICIENT = 42;
    private UCropFragmentCallback callback;
    private float defaultAspectRatio;

    private int mActiveControlsWidgetColor;

    @ColorInt
    private int mRootViewBackgroundColor;
    private int mLogoColor;

    private boolean mShowBottomControls;

    private UCropView mUCropView;
    private GestureCropImageView mGestureCropImageView;
    private OverlayView mOverlayView;
    // private ViewGroup mWrapperStateAspectRatio, mWrapperStateRotate/*, mWrapperStateScale*/;
    private ViewGroup mLayoutAspectRatio;
    private final List<ViewGroup> mCropAspectRatioViews = new ArrayList<>();
    private TextView mTextViewRotateAngle, mTextViewScalePercent;
    private View mBlockingView;

    private Bitmap.CompressFormat mCompressFormat = DEFAULT_COMPRESS_FORMAT;
    private int mCompressQuality = DEFAULT_COMPRESS_QUALITY;
    private int[] mAllowedGestures = new int[]{SCALE, ROTATE, ALL};

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    public static UCropFragment newInstance(Bundle uCrop) {
        UCropFragment fragment = new UCropFragment();
        fragment.setArguments(uCrop);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (callback != null) return;
        if (getParentFragment() instanceof UCropFragmentCallback)
            callback = (UCropFragmentCallback) getParentFragment();
        else if (context instanceof UCropFragmentCallback)
            callback = (UCropFragmentCallback) context;
        else
            throw new IllegalArgumentException(context.toString() + " must implement UCropFragmentCallback");
    }

    public void setCallback(UCropFragmentCallback callback) {
        this.callback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.ucrop_fragment_photobox, container, false);

        Bundle args = getArguments();

        setupViews(rootView, args);
        setImageData(args);
        setInitialState();
        addBlockingView(rootView);

        return rootView;
    }


    public void setupViews(View view, Bundle args) {
        mActiveControlsWidgetColor = args.getInt(UCrop.Options.EXTRA_UCROP_COLOR_CONTROLS_WIDGET_ACTIVE,
                                                 ContextCompat.getColor(getContext(), R.color.ucrop_color_active_controls_color));
        mLogoColor = args.getInt(UCrop.Options.EXTRA_UCROP_LOGO_COLOR, ContextCompat.getColor(getContext(), R.color.ucrop_color_default_logo));
        mShowBottomControls = !args.getBoolean(UCrop.Options.EXTRA_HIDE_BOTTOM_CONTROLS, false);
        mRootViewBackgroundColor = args.getInt(UCrop.Options.EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR,
                                               ContextCompat.getColor(getContext(), R.color.ucrop_color_crop_background));

        initiateRootViews(view);
        callback.loadingProgress(true);

        if (mShowBottomControls) {

            final ViewGroup wrapper = view.findViewById(R.id.controls_wrapper);
            wrapper.setVisibility(View.VISIBLE);
            final View controls = LayoutInflater.from(getContext()).inflate(R.layout.ucrop_controls, wrapper, true);
            controls.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
            wrapper.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
            wrapper.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    wrapper.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    final int wrapperHeight = wrapper.getHeight();
                    mOverlayView.setPadding(
                            mOverlayView.getPaddingLeft(),
                            mOverlayView.getPaddingTop(),
                            mOverlayView.getPaddingRight(),
                            mOverlayView.getPaddingBottom() + wrapperHeight
                    );
                }
            });

            final Transition mControlsTransition = new AutoTransition();
            mControlsTransition.setDuration(CONTROLS_ANIMATION_DURATION);

            // mWrapperStateAspectRatio = view.findViewById(R.id.state_aspect_ratio);
            // mWrapperStateAspectRatio.setOnClickListener(mStateClickListener);
            // mWrapperStateRotate = view.findViewById(R.id.state_rotate);
            // mWrapperStateRotate.setOnClickListener(mStateClickListener);
            // mWrapperStateScale = view.findViewById(R.id.state_scale);
            // mWrapperStateScale.setOnClickListener(mStateClickListener);

            mLayoutAspectRatio = view.findViewById(R.id.layout_aspect_ratio);
            /*, mLayoutScale*/
            // final ViewGroup mLayoutRotate = view.findViewById(R.id.layout_rotate_wheel);
            // mLayoutScale = view.findViewById(R.id.layout_scale_wheel);

            setupAspectRatioWidget(args, view);
            setupRotateWidget(view);
            // setupScaleWidget(view);
            setupStatesWrapper(view);
        } else {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.findViewById(R.id.ucrop_frame).getLayoutParams();
            params.bottomMargin = 0;
            view.findViewById(R.id.ucrop_frame).requestLayout();
        }
    }

    private void setImageData(@NonNull Bundle bundle) {
        Uri inputUri = bundle.getParcelable(UCrop.EXTRA_INPUT_URI);
        Uri outputUri = bundle.getParcelable(UCrop.EXTRA_OUTPUT_URI);
        processOptions(bundle);

        // azri92 - set saved image state if it exists
        float[] savedImageMatrixValues = bundle.getFloatArray(UCrop.EXTRA_IMAGE_MATRIX_VALUES);
        if (savedImageMatrixValues != null) {
            RectF savedCropRect = bundle.getParcelable(UCrop.EXTRA_CROP_RECT);
            mUCropView.setSavedState(savedCropRect);
            mGestureCropImageView.setSavedState(savedImageMatrixValues, savedCropRect);
        }

        if (inputUri != null && outputUri != null) {
            try {
                mGestureCropImageView.setImageUri(inputUri, outputUri);
            } catch (Exception e) {
                callback.onCropFinish(getError(e));
            }
        } else {
            callback.onCropFinish(getError(new NullPointerException(getString(R.string.ucrop_error_input_data_is_absent))));
        }
    }

    /**
     * This method extracts {@link com.yalantis.ucrop.UCrop.Options #optionsBundle} from incoming bundle
     * and setups fragment, {@link OverlayView} and {@link CropImageView} properly.
     */
    @SuppressWarnings("deprecation")
    private void processOptions(@NonNull Bundle bundle) {
        // Bitmap compression options
        String compressionFormatName = bundle.getString(UCrop.Options.EXTRA_COMPRESSION_FORMAT_NAME);
        Bitmap.CompressFormat compressFormat = null;
        if (!TextUtils.isEmpty(compressionFormatName)) {
            compressFormat = Bitmap.CompressFormat.valueOf(compressionFormatName);
        }
        mCompressFormat = (compressFormat == null) ? DEFAULT_COMPRESS_FORMAT : compressFormat;

        mCompressQuality = bundle.getInt(UCrop.Options.EXTRA_COMPRESSION_QUALITY, UCropActivity.DEFAULT_COMPRESS_QUALITY);

        // Gestures options
        int[] allowedGestures = bundle.getIntArray(UCrop.Options.EXTRA_ALLOWED_GESTURES);
        if (allowedGestures != null && allowedGestures.length == TABS_COUNT) {
            mAllowedGestures = allowedGestures;
        }

        // Crop image view options
        mGestureCropImageView.setMaxBitmapSize(bundle.getInt(UCrop.Options.EXTRA_MAX_BITMAP_SIZE, CropImageView.DEFAULT_MAX_BITMAP_SIZE));
        mGestureCropImageView
                .setMaxScaleMultiplier(bundle.getFloat(UCrop.Options.EXTRA_MAX_SCALE_MULTIPLIER, CropImageView.DEFAULT_MAX_SCALE_MULTIPLIER));
        mGestureCropImageView.setImageToWrapCropBoundsAnimDuration(
                bundle.getInt(UCrop.Options.EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION, CropImageView.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION));

        // Overlay view options
        mOverlayView.setFreestyleCropEnabled(bundle.getBoolean(UCrop.Options.EXTRA_FREE_STYLE_CROP,
                                                               OverlayView.DEFAULT_FREESTYLE_CROP_MODE != OverlayView.FREESTYLE_CROP_MODE_DISABLE));
        mOverlayView.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_ENABLE_WITH_PASS_THROUGH);

        mOverlayView
                .setDimmedColor(bundle.getInt(UCrop.Options.EXTRA_DIMMED_LAYER_COLOR, getResources().getColor(R.color.ucrop_color_default_dimmed)));
        mOverlayView.setCircleDimmedLayer(bundle.getBoolean(UCrop.Options.EXTRA_CIRCLE_DIMMED_LAYER, OverlayView.DEFAULT_CIRCLE_DIMMED_LAYER));

        mOverlayView.setShowCropFrame(bundle.getBoolean(UCrop.Options.EXTRA_SHOW_CROP_FRAME, OverlayView.DEFAULT_SHOW_CROP_FRAME));
        mOverlayView.setCropFrameColor(
                bundle.getInt(UCrop.Options.EXTRA_CROP_FRAME_COLOR, getResources().getColor(R.color.ucrop_color_default_crop_frame)));
        mOverlayView.setCropFrameStrokeWidth(bundle.getInt(UCrop.Options.EXTRA_CROP_FRAME_STROKE_WIDTH,
                                                           getResources().getDimensionPixelSize(R.dimen.ucrop_default_crop_frame_stoke_width)));

        mOverlayView.setShowCropGrid(bundle.getBoolean(UCrop.Options.EXTRA_SHOW_CROP_GRID, OverlayView.DEFAULT_SHOW_CROP_GRID));
        mOverlayView.setCropGridRowCount(bundle.getInt(UCrop.Options.EXTRA_CROP_GRID_ROW_COUNT, OverlayView.DEFAULT_CROP_GRID_ROW_COUNT));
        mOverlayView.setCropGridColumnCount(bundle.getInt(UCrop.Options.EXTRA_CROP_GRID_COLUMN_COUNT, OverlayView.DEFAULT_CROP_GRID_COLUMN_COUNT));
        mOverlayView
                .setCropGridColor(bundle.getInt(UCrop.Options.EXTRA_CROP_GRID_COLOR, getResources().getColor(R.color.ucrop_color_default_crop_grid)));
        mOverlayView.setCropGridCornerColor(
                bundle.getInt(UCrop.Options.EXTRA_CROP_GRID_CORNER_COLOR, getResources().getColor(R.color.ucrop_color_default_crop_grid)));
        mOverlayView.setCropGridStrokeWidth(bundle.getInt(UCrop.Options.EXTRA_CROP_GRID_STROKE_WIDTH,
                                                          getResources().getDimensionPixelSize(R.dimen.ucrop_default_crop_grid_stoke_width)));

        // Aspect ratio options
        float aspectRatioX = bundle.getFloat(UCrop.EXTRA_ASPECT_RATIO_X, 0);
        float aspectRatioY = bundle.getFloat(UCrop.EXTRA_ASPECT_RATIO_Y, 0);

        int aspectRationSelectedByDefault = bundle.getInt(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0);
        ArrayList<AspectRatio> aspectRatioList = bundle.getParcelableArrayList(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS);

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            defaultAspectRatio = aspectRatioX / aspectRatioY;
        } else if (aspectRatioList != null && aspectRationSelectedByDefault < aspectRatioList.size()) {
            defaultAspectRatio = aspectRatioList.get(aspectRationSelectedByDefault).getAspectRatioX() / aspectRatioList
                    .get(aspectRationSelectedByDefault).getAspectRatioY();
        } else {
            defaultAspectRatio = CropImageView.SOURCE_IMAGE_ASPECT_RATIO;
        }
        mGestureCropImageView.setTargetAspectRatio(defaultAspectRatio);

        // Result bitmap max size options
        int maxSizeX = bundle.getInt(UCrop.EXTRA_MAX_SIZE_X, 0);
        int maxSizeY = bundle.getInt(UCrop.EXTRA_MAX_SIZE_Y, 0);

        if (maxSizeX > 0 && maxSizeY > 0) {
            mGestureCropImageView.setMaxResultImageSizeX(maxSizeX);
            mGestureCropImageView.setMaxResultImageSizeY(maxSizeY);
        }
    }

    private void initiateRootViews(View view) {
        mUCropView = view.findViewById(R.id.ucrop);
        mGestureCropImageView = mUCropView.getCropImageView();
        mOverlayView = mUCropView.getOverlayView();

        mGestureCropImageView.setTransformImageListener(mImageListener);

        ((ImageView) view.findViewById(R.id.image_view_logo)).setColorFilter(mLogoColor, PorterDuff.Mode.SRC_ATOP);

        view.findViewById(R.id.ucrop_frame).setBackgroundColor(mRootViewBackgroundColor);
    }

    private final TransformImageView.TransformImageListener mImageListener = new TransformImageView.TransformImageListener() {
        @Override
        public void onRotate(float currentAngle) {
            setAngleText(currentAngle);
        }

        @Override
        public void onScale(float currentScale) {
            setScaleText(currentScale);
        }

        @Override
        public void onLoadComplete() {
            mUCropView.animate().alpha(1).setDuration(300).setInterpolator(new AccelerateInterpolator());
            mBlockingView.setClickable(false);
            callback.loadingProgress(false);
        }

        @Override
        public void onLoadFailure(@NonNull Exception e) {
            callback.onCropFinish(getError(e));
        }

        @Override
        public void onStartCropResize() {
            for (ViewGroup cropAspectRatioView : mCropAspectRatioViews) {
                cropAspectRatioView.setSelected(false);
            }
        }
    };

    /**
     * Use {@link #mActiveControlsWidgetColor} for color filter
     */
    private void setupStatesWrapper(View view) {
        // ImageView stateScaleImageView = view.findViewById(R.id.image_view_state_scale);
        // ImageView stateRotateImageView = view.findViewById(R.id.image_view_state_rotate);
        // ImageView stateAspectRatioImageView = view.findViewById(R.id.image_view_state_aspect_ratio);

        // stateScaleImageView.setImageDrawable(new SelectedStateListDrawable(stateScaleImageView.getDrawable(), mActiveControlsWidgetColor));
        // stateRotateImageView.setImageDrawable(new SelectedStateListDrawable(stateRotateImageView.getDrawable(), mActiveControlsWidgetColor));
        // stateAspectRatioImageView.setImageDrawable(new SelectedStateListDrawable(stateAspectRatioImageView.getDrawable(), mActiveControlsWidgetColor));
    }

    private void setupAspectRatioWidget(@NonNull Bundle bundle, View view) {
        int aspectRationSelectedByDefault = bundle.getInt(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0);
        ArrayList<AspectRatio> aspectRatioList = bundle.getParcelableArrayList(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS);

        if (aspectRatioList == null || aspectRatioList.isEmpty()) {
            aspectRationSelectedByDefault = 2;

            aspectRatioList = new ArrayList<>();
            aspectRatioList.add(new AspectRatio(null, 1, 1));
            aspectRatioList.add(new AspectRatio(null, 3, 4));
            aspectRatioList.add(new AspectRatio(getString(R.string.ucrop_label_original).toUpperCase(), CropImageView.SOURCE_IMAGE_ASPECT_RATIO,
                                                CropImageView.SOURCE_IMAGE_ASPECT_RATIO));
            aspectRatioList.add(new AspectRatio(null, 3, 2));
            aspectRatioList.add(new AspectRatio(null, 16, 9));
        }

        LinearLayout wrapperAspectRatioList = view.findViewById(R.id.layout_aspect_ratio);

        FrameLayout wrapperAspectRatio;
        AspectRatioTextView aspectRatioTextView;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.weight = 1;
        for (AspectRatio aspectRatio : aspectRatioList) {
            wrapperAspectRatio = (FrameLayout) getLayoutInflater().inflate(R.layout.ucrop_aspect_ratio, null);
            wrapperAspectRatio.setLayoutParams(lp);
            aspectRatioTextView = ((AspectRatioTextView) wrapperAspectRatio.getChildAt(0));
            aspectRatioTextView.setActiveColor(mActiveControlsWidgetColor);
            aspectRatioTextView.setAspectRatio(aspectRatio);

            wrapperAspectRatioList.addView(wrapperAspectRatio);
            mCropAspectRatioViews.add(wrapperAspectRatio);
        }

        final ViewGroup defaultAspectRatioWrapper = mCropAspectRatioViews.get(aspectRationSelectedByDefault);
        defaultAspectRatioWrapper.setSelected(true);

        for (ViewGroup cropAspectRatioView : mCropAspectRatioViews) {
            cropAspectRatioView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final AspectRatioTextView textView = (AspectRatioTextView) ((ViewGroup) v).getChildAt(0);
                    mGestureCropImageView.setTargetAspectRatio(textView.getAspectRatio(v.isSelected()));
                    mGestureCropImageView.setImageToWrapCropBounds();
                    if (!v.isSelected()) {
                        for (ViewGroup cropAspectRatioView : mCropAspectRatioViews) {
                            cropAspectRatioView.setSelected(cropAspectRatioView == v);
                        }
                    }
                    mLayoutAspectRatio.setVisibility(View.GONE);
                }
            });
        }
        view.findViewById(R.id.aspect_ratio_wrapper).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                mLayoutAspectRatio.setVisibility(mLayoutAspectRatio.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void setupRotateWidget(View view) {
        mTextViewRotateAngle = view.findViewById(R.id.text_view_rotate);
        ((HorizontalProgressWheelView) view.findViewById(R.id.rotate_scroll_wheel))
                .setScrollingListener(new HorizontalProgressWheelView.ScrollingListener() {
                    @Override
                    public void onScroll(float delta, float totalDistance) {
                        mGestureCropImageView.postRotate(delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT);
                    }

                    @Override
                    public void onScrollEnd() {
                        mGestureCropImageView.setImageToWrapCropBounds();
                    }

                    @Override
                    public void onScrollStart() {
                        mGestureCropImageView.cancelAllAnimations();
                    }
                });

        ((HorizontalProgressWheelView) view.findViewById(R.id.rotate_scroll_wheel)).setMiddleLineColor(mActiveControlsWidgetColor);

        final View rotateWrapper = view.findViewById(R.id.wrapper_rotate_by_angle);
        rotateWrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotateByAngle(90);
            }
        });
        rotateWrapper.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                resetRotation();
                return true;
            }
        });
        setAngleTextColor(mActiveControlsWidgetColor);
    }

    private void setupScaleWidget(View view) {
        mTextViewScalePercent = view.findViewById(R.id.text_view_scale);
        ((HorizontalProgressWheelView) view.findViewById(R.id.scale_scroll_wheel))
                .setScrollingListener(new HorizontalProgressWheelView.ScrollingListener() {
                    @Override
                    public void onScroll(float delta, float totalDistance) {
                        if (delta > 0) {
                            mGestureCropImageView.zoomInImage(mGestureCropImageView.getCurrentScale()
                                                                      + delta * ((mGestureCropImageView.getMaxScale() - mGestureCropImageView
                                    .getMinScale()) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT));
                        } else {
                            mGestureCropImageView.zoomOutImage(mGestureCropImageView.getCurrentScale()
                                                                       + delta * ((mGestureCropImageView.getMaxScale() - mGestureCropImageView
                                    .getMinScale()) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT));
                        }
                    }

                    @Override
                    public void onScrollEnd() {
                        mGestureCropImageView.setImageToWrapCropBounds();
                    }

                    @Override
                    public void onScrollStart() {
                        mGestureCropImageView.cancelAllAnimations();
                    }
                });
        ((HorizontalProgressWheelView) view.findViewById(R.id.scale_scroll_wheel)).setMiddleLineColor(mActiveControlsWidgetColor);

        setScaleTextColor(mActiveControlsWidgetColor);
    }

    private void setAngleText(float angle) {
        if (mTextViewRotateAngle != null) {
            String text = String.format(Locale.getDefault(), "%.1f", angle);
            text = text.replaceAll("^-(?=0(\\.0*)?$)", "");
            mTextViewRotateAngle.setText(String.format("%s°", text));
        }
    }

    private void setAngleTextColor(int textColor) {
        if (mTextViewRotateAngle != null) {
            mTextViewRotateAngle.setTextColor(textColor);
        }
    }

    private void setScaleText(float scale) {
        if (mTextViewScalePercent != null) {
            mTextViewScalePercent.setText(String.format(Locale.getDefault(), "%d%%", (int) (scale * 100)));
        }
    }

    private void setScaleTextColor(int textColor) {
        if (mTextViewScalePercent != null) {
            mTextViewScalePercent.setTextColor(textColor);
        }
    }

    private void resetRotation() {
        mGestureCropImageView.postRotate(-mGestureCropImageView.getCurrentAngle());
        mGestureCropImageView.setImageToWrapCropBounds();
    }

    private void rotateByAngle(int angle) {
        mGestureCropImageView.postRotate(angle);
        mGestureCropImageView.setImageToWrapCropBounds();
    }

    private final View.OnClickListener mStateClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!v.isSelected()) {
                setWidgetState(v.getId());
            }
        }
    };

    private void setInitialState() {
        if (mShowBottomControls) {
            setAllowedGestures(2);
            // if (mWrapperStateAspectRatio.getVisibility() == View.VISIBLE) {
            // setWidgetState(R.id.state_aspect_ratio);
            // }
            // else {
            //     setWidgetState(R.id.state_scale);
            // }
        } else {
            setAllowedGestures(0);
        }
    }

    private void setWidgetState(@IdRes int stateViewId) {
        if (!mShowBottomControls) return;

        // mWrapperStateAspectRatio.setSelected(stateViewId == R.id.state_aspect_ratio);
        // mWrapperStateRotate.setSelected(stateViewId == R.id.state_rotate);
        // mWrapperStateScale.setSelected(stateViewId == R.id.state_scale);

        // mLayoutAspectRatio.setVisibility(stateViewId == R.id.state_aspect_ratio ? View.VISIBLE : View.GONE);
        mLayoutAspectRatio.setVisibility(View.GONE);
        // mLayoutScale.setVisibility(stateViewId == R.id.state_scale ? View.VISIBLE : View.GONE);

        // changeSelectedTab(stateViewId);
    }

    private void changeSelectedTab(int stateViewId) {
        // if (getView() != null) {
        //     TransitionManager.beginDelayedTransition((ViewGroup) getView().findViewById(R.id.ucrop_photobox), mControlsTransition);
        // }
        // mWrapperStateScale.findViewById(R.id.text_view_scale).setVisibility(stateViewId == R.id.state_scale ? View.VISIBLE : View.GONE);
        // mWrapperStateAspectRatio.findViewById(R.id.text_view_crop).setVisibility(stateViewId == R.id.state_aspect_ratio ? View.VISIBLE : View.GONE);
        // mWrapperStateRotate.findViewById(R.id.text_view_rotate).setVisibility(stateViewId == R.id.state_rotate ? View.VISIBLE : View.GONE);
    }

    private void setAllowedGestures(int tab) {
        mGestureCropImageView.setScaleEnabled(mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == SCALE);
        mGestureCropImageView.setRotateEnabled(mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == ROTATE);
    }

    /**
     * Adds view that covers everything below the Toolbar.
     * When it's clickable - user won't be able to click/touch anything below the Toolbar.
     * Need to block user input while loading and cropping an image.
     */
    private void addBlockingView(View view) {
        if (mBlockingView == null) {
            mBlockingView = new View(getContext());
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                             ViewGroup.LayoutParams.MATCH_PARENT);
            mBlockingView.setLayoutParams(lp);
            mBlockingView.setClickable(true);
        }

        ((RelativeLayout) view.findViewById(R.id.ucrop_photobox)).addView(mBlockingView);
    }

    public void reset() {
        mGestureCropImageView.resetCropView(defaultAspectRatio);
    }

    public void cropAndSaveImage() {
        mBlockingView.setClickable(true);
        callback.loadingProgress(true);

        mGestureCropImageView.cropAndSaveImage(mCompressFormat, mCompressQuality, new BitmapCropCallback() {

            @Override
            public void onBitmapCropped(
                    @NonNull Uri resultUri,
                    int offsetX,
                    int offsetY,
                    int imageWidth,
                    int imageHeight,
                    float[] imageMatrixValues,
                    RectF cropRect
            ) {
                callback.onCropFinish(
                        getResult(resultUri,
                                  mGestureCropImageView.getTargetAspectRatio(),
                                  offsetX,
                                  offsetY,
                                  imageWidth,
                                  imageHeight,
                                  imageMatrixValues,
                                  cropRect)
                );
                callback.loadingProgress(false);
            }

            @Override
            public void onCropFailure(@NonNull Throwable t) {
                callback.onCropFinish(getError(t));
            }
        });
    }

    protected UCropResult getResult(
            Uri uri,
            float resultAspectRatio,
            int offsetX,
            int offsetY,
            int imageWidth,
            int imageHeight,
            float[] imageMatrixValues,
            RectF cropRect
    ) {
        return new UCropResult(RESULT_OK, new Intent()
                .putExtra(UCrop.EXTRA_OUTPUT_URI, uri)
                .putExtra(UCrop.EXTRA_OUTPUT_CROP_ASPECT_RATIO, resultAspectRatio)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, imageWidth)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, imageHeight)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, offsetX)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
                .putExtra(UCrop.EXTRA_IMAGE_MATRIX_VALUES, imageMatrixValues)
                .putExtra(UCrop.EXTRA_CROP_RECT, cropRect)
        );
    }

    protected UCropResult getError(Throwable throwable) {
        return new UCropResult(UCrop.RESULT_ERROR, new Intent().putExtra(UCrop.EXTRA_ERROR, throwable));
    }

    public static class UCropResult {

        public int mResultCode;
        public Intent mResultData;

        public UCropResult(int resultCode, Intent data) {
            mResultCode = resultCode;
            mResultData = data;
        }

    }

}

