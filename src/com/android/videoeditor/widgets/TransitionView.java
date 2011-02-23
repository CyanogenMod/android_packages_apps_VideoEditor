/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.videoeditor.widgets;

import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.MovieTransition;
import com.android.videoeditor.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Transition view
 */
public class TransitionView extends ImageView {
    // Logging
    private static final String TAG = "TransitionView";

    // Instance variables
    private final GestureDetector mSimpleGestureDetector;
    private final ScrollViewListener mScrollListener;
    private final Rect mProgressDestRect;
    private final Paint mSeparatorPaint;
    private boolean mIsScrolling;
    private int mScrollX;
    private int mScreenWidth;
    private String mProjectPath;
    private Bitmap[] mBitmaps;
    private ItemSimpleGestureListener mGestureListener;
    private int mProgress;
    private boolean mIsPlaying;

    /*
     * {@inheritDoc}
     */
    public TransitionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Setup the gesture listener
        mSimpleGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (mGestureListener != null) {
                            return mGestureListener.onSingleTapConfirmed(TransitionView.this, -1,
                                    e);
                        } else {
                            return false;
                        }
                    }

                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (mGestureListener != null) {
                            mGestureListener.onLongPress(TransitionView.this, e);
                        }
                    }
                });

        mScrollListener = new ScrollViewListener() {
            /*
             * {@inheritDoc}
             */
            public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll) {
                mIsScrolling = true;
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll) {
                invalidate();
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                mIsScrolling = false;
                mScrollX = scrollX;

                if (requestThumbnails()) {
                    invalidate();
                }
            }
        };

        final Resources resources = getResources();
        // Prepare the bitmap rectangles
        final ProgressBar progressBar = ProgressBar.getProgressBar(context);
        final int layoutHeight = (int)(resources.getDimension(R.dimen.media_layout_height) -
                resources.getDimension(R.dimen.media_layout_padding) -
                (2 * resources.getDimension(R.dimen.timelime_transition_vertical_inset)));
        mProgressDestRect = new Rect(getPaddingLeft(),
                layoutHeight - progressBar.getHeight() - getPaddingBottom(), 0,
                layoutHeight - getPaddingBottom());

        // Initialize the progress value
        mProgress = -1;

        // Get the screen width
        final Display display = ((WindowManager) getContext().getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;

        // Prepare the separator paint
        mSeparatorPaint = new Paint();
        mSeparatorPaint.setColor(Color.BLACK);
        mSeparatorPaint.setStrokeWidth(2);
    }

    /*
     * {@inheritDoc}
     */
    public TransitionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public TransitionView(Context context) {
        this(context, null, 0);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onAttachedToWindow() {
        // Add the horizontal scroll view listener
        final TimelineHorizontalScrollView scrollView =
                (TimelineHorizontalScrollView)((View)((View)getParent()).getParent()).getParent();
        mScrollX = scrollView.getScrollX();
        scrollView.addScrollListener(mScrollListener);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        // Remove the horizontal scroll listener
        final TimelineHorizontalScrollView scrollView =
            (TimelineHorizontalScrollView)((View)((View)getParent()).getParent()).getParent();
        scrollView.removeScrollListener(mScrollListener);

        // Release the current set of bitmaps
        if (mBitmaps != null) {
            for (int i = 0; i < mBitmaps.length; i++) {
                if (mBitmaps[i] != null) {
                    mBitmaps[i].recycle();
                }
            }

            mBitmaps = null;
        }
    }

    /**
     * @param projectPath The project path
     */
    public void setProjectPath(String projectPath) {
        mProjectPath = projectPath;
    }

    /**
     * @param listener The gesture listener
     */
    public void setGestureListener(ItemSimpleGestureListener listener) {
        mGestureListener = listener;
    }

    /**
     * @param progress The progress
     */
    public void setProgress(int progress) {
        if (progress == 100) {
            // Request the preview bitmaps
            requestThumbnails();
            mProgress = -1;
        } else {
            mProgress = progress;
        }

        invalidate();
    }

    /**
     * @return true if generation is in progress
     */
    public boolean isInProgress() {
        return (mProgress >= 0);
    }

    /**
     * A view enters or exits the playback mode
     *
     * @param playback true if playback is in progress
     */
    public void setPlaybackMode(boolean playback) {
        mIsPlaying = playback;
    }

    /**
     * @param bitmaps The bitmaps array
     *
     * @return true if the bitmaps were used
     */
    public boolean setBitmaps(Bitmap[] bitmaps) {
        if (mProgress >= 0) {
            return false;
        }

        // Release the current set of bitmaps
        if (mBitmaps != null) {
            for (int i = 0; i < mBitmaps.length; i++) {
                if (mBitmaps[i] != null) {
                    mBitmaps[i].recycle();
                }
            }
        }

        mBitmaps = bitmaps;
        invalidate();

        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // If the view is too small don't draw anything
        if (getWidth() <= getPaddingLeft() + getPaddingRight()) {
            return;
        }

        if (mProgress >= 0) {
            ProgressBar.getProgressBar(getContext()).draw(canvas, mProgress,
                    mProgressDestRect, getPaddingLeft(), getWidth() - getPaddingRight());
        } else if (mBitmaps != null) {
            final int halfWidth = getWidth() / 2;
            // Draw the bitmaps
            // Draw the left side of the transition
            canvas.save();
            canvas.clipRect(getPaddingLeft(), getPaddingTop(), halfWidth,
                    getHeight() - getPaddingBottom());

            if (mBitmaps[0] != null) {
                canvas.drawBitmap(mBitmaps[0], getPaddingLeft(), getPaddingTop(), null);
            } else {
                canvas.drawColor(Color.BLACK);
            }
            canvas.restore();

            // Draw the right side of the transition
            canvas.save();
            canvas.clipRect(halfWidth, getPaddingTop(),
                    getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            if (mBitmaps[1] != null) {
                canvas.drawBitmap(mBitmaps[1],
                        getWidth() - getPaddingRight() - mBitmaps[1].getWidth(), getPaddingTop(),
                        null);
            } else {
                canvas.drawColor(Color.BLACK);
            }
            canvas.restore();

            canvas.drawLine(halfWidth, getPaddingTop(), halfWidth,
                    getHeight() - getPaddingBottom(), mSeparatorPaint);
        } else if (mIsPlaying) { // Playing
        } else if (mIsScrolling) { // Scrolling
        } else { // Not scrolling and not playing
            requestThumbnails();
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the gesture detector inspect all events.
        mSimpleGestureDetector.onTouchEvent(ev);
        return super.onTouchEvent(ev);
    }

    /**
     * Request thumbnails if necessary
     *
     * @return true if the bitmaps already exist
     */
    private boolean requestThumbnails() {
        // Check if we already have the bitmaps
        if (mBitmaps != null) {
            return true;
        }

        // Do not request thumbnails during playback
        if (mIsScrolling) {
            return false;
        }

        final MovieTransition transition = (MovieTransition)getTag();
        // Check if we already requested the thumbnails
        if (ApiService.isTransitionThumbnailsPending(mProjectPath, transition.getId())) {
            return false;
        }

        final int start = getLeft() + getPaddingLeft() - mScrollX;
        final int end = getRight() - getPaddingRight() - mScrollX;

        if (start >= mScreenWidth || end < 0 || start == end) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Transition view is off screen: " + transition.getId() +
                        ", from: " + start + " to " + end);
            }

            // Release the current set of bitmaps
            if (mBitmaps != null) {
                for (int i = 0; i < mBitmaps.length; i++) {
                    if (mBitmaps[i] != null) {
                        mBitmaps[i].recycle();
                    }
                }

                mBitmaps = null;
            }

            return false;
        }

        // Compute the thumbnail width
        final int thumbnailHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        // Request the thumbnails
        ApiService.getTransitionThumbnails(getContext(), mProjectPath, transition.getId(),
                thumbnailHeight);

        return false;
    }
}
