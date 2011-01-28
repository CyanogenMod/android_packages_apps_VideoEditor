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
import com.google.videoeditor.service.MovieMediaItem;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Media item preview view
 */
public class MediaItemView extends View {
    // Logging
    private static final String TAG = "MediaItemView";

    // Reasons for requesting thumbnails
    private static final int REASON_SCROLL_END = 1;
    private static final int REASON_REFRESH = 2;
    private static final int REASON_PROGRESS_END = 3;
    private static final int REASON_NEED_THUMBNAILS = 4;
    private static final int REASON_PLAYBACK = 5;

    private static Drawable mAddTransitionDrawable;

    // Instance variables
    private final GestureDetector mGestureDetector;
    private final ScrollViewListener mScrollListener;
    private final Rect mProgressDestRect;

    private boolean mIsScrolling;
    private boolean mWaitForThumbnailsAfterScroll;
    private boolean mScrollingRight;
    private int mScrollX, mScrollingX;
    private int mStart, mEnd;
    private int mStartOffset, mEndOffset;
    private int mRequestedStartOffset, mRequestedEndOffset;
    private long mRequestedStartMs, mRequestedEndMs;
    private long mPlaybackRequestTime;
    private int mRequestedCount;
    private int mScreenWidth;
    private String mProjectPath;
    private Bitmap[] mBitmaps;
    private ItemSimpleGestureListener mGestureListener;
    private int mProgress;
    private int[] mLeftState, mRightState;
    private boolean mIsTrimming;
    private boolean mIsPlaying;

    /**
     * Shadow builder for the media item
     */
    private class MediaItemShadowBuilder extends DragShadowBuilder {
        // Instance variables
        private final Drawable mFrame;

        /*
         * {@inheritDoc}
         */
        public MediaItemShadowBuilder(View view) {
            super(view);

            mFrame = view.getContext().getResources().getDrawable(R.drawable.timeline_item_pressed);
        }

        /*
         * {@inheritDoc}
         */
        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            shadowSize.set(getShadowWidth(), getShadowHeight());
            shadowTouchPoint.set(shadowSize.x / 2, shadowSize.y);
        }

        /*
         * {@inheritDoc}
         */
        @Override
        public void onDrawShadow(Canvas canvas) {
            //super.onDrawShadow(canvas);
            mFrame.setBounds(0, 0, getShadowWidth(), getShadowHeight());
            mFrame.draw(canvas);

            if (mBitmaps != null && mBitmaps.length > 0) {
                final View view = getView();
                canvas.drawBitmap(mBitmaps[0], view.getPaddingLeft(), view.getPaddingTop(), null);
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    public MediaItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Setup the gesture listener
        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (mGestureListener == null) {
                            return false;
                        }

                        if (hasAddTransition()) {
                            final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
                            if (mediaItem.getBeginTransition() == null &&
                                    e.getX() < mAddTransitionDrawable.getIntrinsicWidth() +
                                    getPaddingLeft()) {
                                return mGestureListener.onSingleTapConfirmed(MediaItemView.this,
                                        ItemSimpleGestureListener.LEFT_AREA, e);
                            } else if (mediaItem.getEndTransition() == null &&
                                    e.getX() >= getWidth() - getPaddingRight() -
                                    mAddTransitionDrawable.getIntrinsicWidth()) {
                                return mGestureListener.onSingleTapConfirmed(MediaItemView.this,
                                        ItemSimpleGestureListener.RIGHT_AREA, e);
                            } else {
                                return mGestureListener.onSingleTapConfirmed(MediaItemView.this,
                                        ItemSimpleGestureListener.CENTER_AREA, e);
                            }
                        } else {
                            return mGestureListener.onSingleTapConfirmed(MediaItemView.this,
                                    ItemSimpleGestureListener.CENTER_AREA, e);
                        }
                    }

                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public void onLongPress (MotionEvent e) {
                        if (mGestureListener != null) {
                            mGestureListener.onLongPress(MediaItemView.this, e);
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
                mScrollingRight = scrollX > mScrollingX;
                mScrollingX = scrollX;
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                mIsScrolling = false;
                mScrollX = scrollX;
                mScrollingX = scrollX;
                if (requestThumbnails(REASON_SCROLL_END)) {
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

        if (mAddTransitionDrawable == null) {
            mAddTransitionDrawable = getResources().getDrawable(
                    R.drawable.add_transition_selector);
        }

        // Get the screen width
        final Display display = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;

        mRequestedStartMs = -1;
        mRequestedEndMs = -1;

        mPlaybackRequestTime = 0;

        mLeftState = View.EMPTY_STATE_SET;
        mRightState = View.EMPTY_STATE_SET;
    }

    /*
     * {@inheritDoc}
     */
    public MediaItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public MediaItemView(Context context) {
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
        final TimelineHorizontalScrollView scrollView =
            (TimelineHorizontalScrollView)((View)((View)getParent()).getParent()).getParent();
        scrollView.removeScrollListener(mScrollListener);

        // Release the current set of bitmaps
        releaseBitmaps();
    }

    /**
     * @return The shadow builder
     */
    public DragShadowBuilder getShadowBuilder() {
        return new MediaItemShadowBuilder(this);
    }

    /**
     * @return The shadow width
     */
    public int getShadowWidth() {
        final int thumbnailHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
        final int thumbnailWidth = (thumbnailHeight * mediaItem.getWidth()) /
            mediaItem.getHeight();
        return thumbnailWidth + getPaddingLeft() + getPaddingRight();
    }

    /**
     * @return The shadow height
     */
    public int getShadowHeight() {
        return getHeight();
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
     * The view has been layout out
     *
     * @param oldLeft The old left position
     * @param oldRight The old right position
     */
    public void onPositionChanged(int oldLeft, int oldRight) {
        releaseBitmapsAndClear();

        if (!mIsScrolling && !mIsTrimming && mProgress < 0) {
            requestThumbnails(REASON_REFRESH);
        }
    }

    /**
     * @param progress The progress
     */
    public void setProgress(int progress) {
        if (progress == 0) {
            mProgress = progress;
            // Release the current set of bitmaps. New content is being generated.
            releaseBitmapsAndClear();
        } else if (progress == 100) {
            // Request the preview bitmaps
            requestThumbnails(REASON_PROGRESS_END);
            mProgress = -1;
        } else {
            mProgress = progress;
        }

        invalidate();
    }

    /**
     * @return True if generation is in progress
     */
    public boolean isInProgress() {
        return (mProgress >= 0);
    }

    /**
     * A view enters or exits the trimming mode
     *
     * @param trimmingView The view which is being trimmed
     * @param trimming true if trimming
     */
    public void setTrimMode(View trimmingView, boolean trimming) {
        mIsTrimming = trimming;

        if (trimmingView == this) {
            // Redraw the control to hide the "Add transition" areas
            invalidate();
        }
    }

    /**
     * A view enters or exits the playback mode
     *
     * @param playback true if playback is in progress
     */
    public void setPlaybackMode(boolean playback) {
        mIsPlaying = playback;

        mPlaybackRequestTime = 0;
    }

    /**
     * Set the bitmaps
     *
     * @param bitmaps The array of bitmaps
     * @param startMs The start time
     * @param endMs The end time
     *
     * @return true if the bitmap array is used
     */
    public boolean setBitmaps(Bitmap[] bitmaps, long startMs, long endMs) {
        if (mProgress >= 0) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Ignore thumbnails: progress is displayed");
            }

            mWaitForThumbnailsAfterScroll = false;
            return false;
        } else if (startMs != mRequestedStartMs || endMs != mRequestedEndMs) {
            // Old request
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Ignore thumbnails: " + startMs + "ms to: " + endMs + "ms, have: " +
                        mRequestedStartMs + "ms to: " + mRequestedEndMs + "ms");
            }
            // Do not use this set of bitmaps
            return false;
        }

        // Release the current set of bitmaps
        releaseBitmaps();

        mWaitForThumbnailsAfterScroll = false;

        mStartOffset = mRequestedStartOffset;
        mEndOffset = mRequestedEndOffset;
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
            Log.v(TAG, "Using thumbnails: " + bitmaps.length + ", from: "+ startMs +
                    "ms to: " + endMs + "ms, between: " + mStartOffset + " and " + mEndOffset +
                    ", thumbnail width: " + bitmaps[0].getWidth() + ", id: " + mediaItem.getId());
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

        if (mProgress >= 0) {
            ProgressBar.getProgressBar(getContext()).draw(canvas, mProgress,
                    mProgressDestRect, getPaddingLeft(), getWidth() - getPaddingRight());
        } else if (mIsPlaying || mIsTrimming || mIsScrolling || mWaitForThumbnailsAfterScroll) {
            drawWhileScrolling(canvas);
        } else if (mBitmaps != null) {
            final int paddingTop = getPaddingTop();
            // Do not draw in the padding area
            canvas.clipRect(getPaddingLeft(), paddingTop, getWidth() - getPaddingRight(),
                    getHeight() - getPaddingBottom());

            // Draw the bitmaps
            int offsetX = mStartOffset;
            for (int i = 0; i < mBitmaps.length; i++) {
                canvas.drawBitmap(mBitmaps[i], offsetX, paddingTop, null);
                offsetX += mBitmaps[i].getWidth();
            }

            if (isSelected()) {
                drawAddTransitions(canvas);
            }
        } else { // Not scrolling
            requestThumbnails(REASON_NEED_THUMBNAILS);

            if (isSelected()) {
                drawAddTransitions(canvas);
            }
        }
    }

    /**
     * Draw while scrolling
     *
     * @param canvas The canvas
     */
    private void drawWhileScrolling(Canvas canvas) {
        if (mIsPlaying) {
            // During playback request thumbnails at a predefined time interval
            final long now = System.currentTimeMillis();
            if (now - mPlaybackRequestTime > 10000) {
                requestThumbnails(REASON_PLAYBACK);
                mPlaybackRequestTime = now;
            }
        }

        if (mBitmaps == null || mBitmaps.length == 0) {
            return;
        }

        int start = getLeft() + getPaddingLeft() - mScrollingX;
        int end = getRight() - getPaddingRight() - mScrollingX;

        if (start >= mScreenWidth || end < 0 || start == end) {
            return;
        }

        // Clip it to the screen
        if (end > mScreenWidth) {
            end = mScreenWidth;
        }

        // Convert to local coordinates
        end -= start - getPaddingLeft();
        if (start < 0) {
            start = -start;
            final int off = start % mBitmaps[0].getWidth();
            if (off > 0) {
                start = start - off + getPaddingLeft();
            } else {
            }

            if (start < getPaddingLeft()) {
                start = getPaddingLeft();
            }
        } else {
            start = getPaddingLeft();
        }

        final int paddingTop = getPaddingTop();
        // Do not draw in the padding area
        canvas.clipRect(getPaddingLeft(), paddingTop, getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        final int bitmapWidth = mBitmaps[0].getWidth();
        final Bitmap repeatBitmap = mScrollingRight ? mBitmaps[mBitmaps.length - 1] : mBitmaps[0];
        int offsetX = start;
        while (offsetX < end) {
            final Bitmap bitmap;
            if (offsetX >= mStartOffset && offsetX < mEndOffset) {
                bitmap = mBitmaps[Math.min((offsetX - mStartOffset) / bitmapWidth,
                        mBitmaps.length - 1)];
            } else {
                bitmap = repeatBitmap;
            }

            canvas.drawBitmap(bitmap, offsetX, paddingTop, null);
            offsetX += bitmapWidth;
        }

        if (isSelected()) {
            drawAddTransitions(canvas);
        }
    }

    /**
     * Draw the "Add transition" area at the beginning and end of the media item"
     *
     * @param canvas Draw on this canvas
     */
    private void drawAddTransitions(Canvas canvas) {
        if (hasAddTransition()) {
            final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
            if (mediaItem.getBeginTransition() == null) {
                mAddTransitionDrawable.setState(mLeftState);
                mAddTransitionDrawable.setBounds(getPaddingLeft(), getPaddingTop(),
                        mAddTransitionDrawable.getIntrinsicWidth() + getPaddingLeft(),
                        getPaddingTop() + mAddTransitionDrawable.getIntrinsicHeight());
                mAddTransitionDrawable.draw(canvas);
            }

            if (mediaItem.getEndTransition() == null) {
                mAddTransitionDrawable.setState(mRightState);
                mAddTransitionDrawable.setBounds(
                        getWidth() - getPaddingRight() -
                        mAddTransitionDrawable.getIntrinsicWidth(),
                        getPaddingTop(), getWidth() - getPaddingRight(),
                        getPaddingTop() + mAddTransitionDrawable.getIntrinsicHeight());
                mAddTransitionDrawable.draw(canvas);
            }
        }
    }

    /**
     * @return true if the "Add transition" areas exist
     */
    private boolean hasAddTransition() {
        if (mIsTrimming) {
            return false;
        }

        return (getWidth() - getPaddingLeft() - getPaddingRight() >=
                2 * mAddTransitionDrawable.getIntrinsicWidth());
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the gesture detector inspect all events.
        mGestureDetector.onTouchEvent(ev);
        super.onTouchEvent(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (isSelected() && hasAddTransition()) {
                    final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
                    if (ev.getX() < mAddTransitionDrawable.getIntrinsicWidth() +
                            getPaddingLeft()) {
                        if (mediaItem.getBeginTransition() == null) {
                            mLeftState = View.PRESSED_WINDOW_FOCUSED_STATE_SET;
                            mRightState = View.EMPTY_STATE_SET;
                        } else {
                            mRightState = View.EMPTY_STATE_SET;
                            mLeftState = View.EMPTY_STATE_SET;
                        }
                    } else if (ev.getX() >= getWidth() - getPaddingRight() -
                            mAddTransitionDrawable.getIntrinsicWidth()) {
                        if (mediaItem.getEndTransition() == null) {
                            mRightState = View.PRESSED_WINDOW_FOCUSED_STATE_SET;
                            mLeftState = View.EMPTY_STATE_SET;
                        } else {
                            mRightState = View.EMPTY_STATE_SET;
                            mLeftState = View.EMPTY_STATE_SET;
                        }
                    } else {
                        mRightState = View.EMPTY_STATE_SET;
                        mLeftState = View.EMPTY_STATE_SET;
                    }
                } else {
                    mRightState = View.EMPTY_STATE_SET;
                    mLeftState = View.EMPTY_STATE_SET;
                }

                invalidate();
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mRightState = View.EMPTY_STATE_SET;
                mLeftState = View.EMPTY_STATE_SET;

                invalidate();
                break;
            }

            default: {
                break;
            }
        }

        return true;
    }

    /**
     * Request thumbnails if necessary
     *
     * @param reason The reason for the request
     *
     * @return true if the bitmaps are already available
     */
    private boolean requestThumbnails(int reason) {
        int start, end;
        if (reason == REASON_PLAYBACK) {
            start = getLeft() + getPaddingLeft() - mScrollingX;
            end = getRight() - getPaddingRight() - mScrollingX;
        } else {
            start = getLeft() + getPaddingLeft() - mScrollX;
            end = getRight() - getPaddingRight() - mScrollX;
        }

        if (start >= mScreenWidth || end < 0 || start == end) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
                Log.v(TAG, "MediaItem view is off screen: " + mediaItem.getId() +
                        " " + start + " to " + end);
            }

            releaseBitmapsAndClear();
            return false;
        }

        // Clip it to the screen
        if (end > mScreenWidth) {
            end = mScreenWidth;
        }

        // Convert to local coordinates
        end -= start - getPaddingLeft();
        if (start < 0) {
            start = -start;
        } else {
            start = getPaddingLeft();
        }

        boolean result = false;
        if (start != mStart || mEnd != end) {
            // Compute the thumbnail width
            final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
            final int thumbnailHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            final int thumbnailWidth = (thumbnailHeight * mediaItem.getWidth()) /
                    mediaItem.getHeight();
            final long durationMs = mediaItem.getAppTimelineDuration();
            final int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();

            // Compute the start offset
            final long startMs;
            // Adjust the start to be a thumbnail boundary
            final int off = (start % thumbnailWidth);
            int startOffset;
            if (off > 0) {
                startOffset = start - off + getPaddingLeft();
            } else {
                startOffset = start;
            }
            if (startOffset <= getPaddingLeft()) {
                startOffset = getPaddingLeft();
                startMs = mediaItem.getAppBoundaryBeginTime();
            } else {
                startMs = mediaItem.getAppBoundaryBeginTime()
                        + ((startOffset * durationMs) / viewWidth);
            }

            // Compute the end offset
            final long endMs;
            int endOffset = end;
            if (endOffset > getWidth() - getPaddingRight()) {
                endOffset = getWidth() - getPaddingRight();
                endMs = mediaItem.getAppBoundaryEndTime();
            } else {
                endMs = Math.min(mediaItem.getAppBoundaryBeginTime()
                        + ((endOffset * durationMs) / viewWidth),
                        mediaItem.getAppBoundaryEndTime());
            }

            if (startOffset < endOffset) {
                // Compute the thumbnail count
                final int count = ((endOffset - startOffset) / thumbnailWidth) + 1;
                if (startMs >= mRequestedStartMs && endMs <= mRequestedEndMs &&
                        count <= mRequestedCount && startOffset >= mRequestedStartOffset &&
                        endOffset <= mRequestedEndOffset) {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Inclusive new from: " + startMs + "ms to: " + endMs +
                                "ms, count: " + count + ", rendered between: " +
                                    startOffset + " and " + endOffset + ", old: " +
                                mRequestedStartMs + "ms to " + mRequestedEndMs +
                                "ms, count: " + mRequestedCount + ", rendered between: " +
                                mRequestedStartOffset + " and " + mRequestedEndOffset +
                                ", id: " + mediaItem.getId());
                    }

                    // The new interval is included in the one we already requested
                    if (mBitmaps != null) {
                        result = true;
                    }
                } else {
                    mWaitForThumbnailsAfterScroll = reason == REASON_SCROLL_END;
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Request thumbnails: " + count + " thumbnails from: " +
                                startMs + "ms to: " + endMs + "ms, rendered between: " +
                                startOffset + " and " + endOffset + ", wait for scroll: " +
                                mWaitForThumbnailsAfterScroll + ", id: " + mediaItem.getId());
                    }

                    // Request the thumbnails
                    ApiService.getMediaItemThumbnails(getContext(), mProjectPath,
                            mediaItem.getId(), thumbnailWidth, thumbnailHeight, startMs, endMs,
                            count);

                    mRequestedStartMs = startMs;
                    mRequestedEndMs = endMs;
                    mRequestedCount = count;
                    mRequestedStartOffset = startOffset;
                    mRequestedEndOffset = endOffset;
                }
            } else {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "View is too small for thumbnails: " +
                            startOffset + " and " + endOffset + ", view width: " + viewWidth);
                }
            }

            mStart = start;
            mEnd = end;
        } else if (mBitmaps != null) {
            result = true;
        }

        return result;
    }

    /**
     * Recycle all the bitmaps
     */
    private void releaseBitmaps() {
        if (mBitmaps != null) {
            for (int i = 0; i < mBitmaps.length; i++) {
                mBitmaps[i].recycle();
            }

            mStartOffset = -1;
            mEndOffset = -1;
        }
    }

    /**
     * Recycle the bitmaps and reset the pending request. This method
     * invalidates any pending request by reseting the pending request
     * parameters.
     */
    private void releaseBitmapsAndClear() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            final MovieMediaItem mediaItem = (MovieMediaItem)getTag();
            Log.v(TAG, "releaseBitmapsAndClear: " + mediaItem.getId() + " " +
                    mRequestedStartMs + "ms to: " +
                    mRequestedEndMs + "ms, between: " + mStartOffset + " and " + mEndOffset);
        }

        // Release the current set of bitmaps
        if (mBitmaps != null) {
            for (int i = 0; i < mBitmaps.length; i++) {
                mBitmaps[i].recycle();
            }

            mBitmaps = null;

            mStartOffset = -1;
            mEndOffset = -1;
        }

        mRequestedStartMs = -1;
        mRequestedEndMs = -1;
        mRequestedCount = 0;
        mRequestedStartOffset = -1;
        mRequestedEndOffset = -1;

        mStart = 0;
        mEnd = 0;

        mWaitForThumbnailsAfterScroll = false;
    }
}
