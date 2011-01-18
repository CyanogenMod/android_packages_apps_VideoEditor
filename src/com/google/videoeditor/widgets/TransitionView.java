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

package com.google.videoeditor.widgets;

import com.google.videoeditor.R;
import com.google.videoeditor.service.ApiService;
import com.google.videoeditor.service.MovieTransition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Paint.Style;
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

    // This value determines the incline of the thumbnail separator
    private static final int GAP_OFFSET = 4;
    // This value determines the thickness of the thumbnail separator
    private static final int GAP_THICKNESS = 8;

    // Instance variables
    private final GestureDetector mSimpleGestureDetector;
    private final ScrollViewListener mScrollListener;
    private final Rect mProgressDestRect;
    private final Path mLeftPath, mRightPath;
    private final Paint mLinePaint;

    private boolean mIsScrolling;
    private int mScrollX, mScrollingX;
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
                mScrollingX = scrollX;
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                mIsScrolling = false;
                mScrollX = scrollX;
                mScrollingX = scrollX;

                if (requestThumbnails()) {
                    invalidate();
                }
            }
        };

        // Prepare the bitmap rectangles
        final ProgressBar progressBar = ProgressBar.getProgressBar(context);
        final int layoutHeight = (int)(getResources().getDimension(R.dimen.media_layout_height) -
                getResources().getDimension(R.dimen.media_layout_padding));
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

        // Prepare the paths used for the clip regions
        mLeftPath = new Path();
        mRightPath = new Path();

        // Prepare the line paint
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);
        mLinePaint.setStyle(Style.STROKE);
        mLinePaint.setColor(getResources().getColor(R.color.timeline_view_padding_color));
        mLinePaint.setStrokeWidth(getResources().getDimension(R.dimen.timelime_item_padding));
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
        mScrollingX = mScrollX = scrollView.getScrollX();
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
            // Exclude the padding
            canvas.clipRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                    getHeight() - getPaddingBottom());

            // Draw the bitmaps
            // Draw the left side of the transition
            canvas.save();
            mLeftPath.reset();
            mLeftPath.moveTo(0, 0);
            mLeftPath.lineTo(halfWidth + GAP_OFFSET, 0);
            mLeftPath.lineTo(halfWidth - GAP_OFFSET - GAP_THICKNESS, getHeight());
            mLeftPath.lineTo(0, getHeight());
            mLeftPath.close();
            canvas.clipPath(mLeftPath);

            if (mBitmaps[0] != null) {
                canvas.drawBitmap(mBitmaps[0], getPaddingLeft(), getPaddingTop(), null);
            } else {
                canvas.drawColor(0xff000000);
            }
            canvas.restore();
            canvas.drawLine(halfWidth + GAP_OFFSET, getPaddingTop(),
                    halfWidth - GAP_OFFSET - GAP_THICKNESS, getHeight() - getPaddingBottom(),
                    mLinePaint);

            // Draw the right side of the transition
            canvas.save();
            mRightPath.reset();
            mRightPath.moveTo(halfWidth + GAP_OFFSET + GAP_THICKNESS, 0);
            mRightPath.lineTo(getWidth(), 0);
            mRightPath.lineTo(getWidth(), getHeight());
            mRightPath.lineTo(halfWidth - GAP_OFFSET, getHeight());
            mRightPath.close();
            canvas.clipPath(mRightPath);
            if (mBitmaps[1] != null) {
                canvas.drawBitmap(mBitmaps[1],
                        getWidth() - getPaddingRight() - mBitmaps[1].getWidth(), getPaddingTop(),
                        null);
            } else {
                canvas.drawColor(0xff000000);
            }
            canvas.restore();
            canvas.drawLine(halfWidth + GAP_OFFSET + GAP_THICKNESS, getPaddingTop(),
                    halfWidth - GAP_OFFSET, getHeight() - getPaddingBottom(), mLinePaint);
        } else if (mIsPlaying) { // Playing
            requestThumbnails();
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

        final MovieTransition transition = (MovieTransition)getTag();
        // Check if we already requested the thumbnails
        if (ApiService.isTransitionThumbnailsPending(mProjectPath, transition.getId())) {
            return false;
        }

        final int start;
        final int end;
        if (mIsPlaying) {
            start = getLeft() + getPaddingLeft() - mScrollingX;
            end = getRight() - getPaddingRight() - mScrollingX;
        } else {
            start = getLeft() + getPaddingLeft() - mScrollX;
            end = getRight() - getPaddingRight() - mScrollX;
        }

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
