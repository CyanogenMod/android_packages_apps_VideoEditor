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
import com.google.videoeditor.service.VideoEditorProject;
import com.google.videoeditor.util.StringUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

/**
 * The view which displays the scroll position
 */
public class PlayheadView extends View {
    // Instance variables
    private final Paint mLinePaint;
    private final Paint mTextPaint;
    private final int mTicksHeight;
    private final int mScreenWidth;
    private final ScrollViewListener mScrollListener;
    private int mScrollX;
    private VideoEditorProject mProject;

    /*
     * {@inheritDoc}
     */
    public PlayheadView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = context.getResources();

        // Prepare the Paint used to draw the tick marks
        mLinePaint = new Paint();
        mLinePaint.setColor(resources.getColor(R.color.playhead_tick_color));
        mLinePaint.setStrokeWidth(2);
        mLinePaint.setStyle(Paint.Style.STROKE);

        // Prepare the Paint used to draw the text
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(resources.getColor(R.color.playhead_tick_color));
        mTextPaint.setTextSize(18);

        // The ticks height
        mTicksHeight = (int)resources.getDimension(R.dimen.playhead_tick_height);

        // Get the screen width
        final Display display = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;

        // Listen to scroll events and repaint this view as needed
        mScrollListener = new ScrollViewListener() {
            /*
             * {@inheritDoc}
             */
            public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll) {
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll) {
                mScrollX = scrollX;
                invalidate();
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                mScrollX = scrollX;
                invalidate();
            }
        };
    }

    /*
     * {@inheritDoc}
     */
    public PlayheadView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public PlayheadView(Context context) {
        this(context, null, 0);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onAttachedToWindow() {
        final TimelineHorizontalScrollView scrollView =
            (TimelineHorizontalScrollView)((View)getParent()).getParent();
        mScrollX = scrollView.getScrollX();
        scrollView.addScrollListener(mScrollListener);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        final TimelineHorizontalScrollView scrollView =
            (TimelineHorizontalScrollView)((View)getParent()).getParent();
        scrollView.removeScrollListener(mScrollListener);
    }

    /**
     * @param project The project
     */
    public void setProject(VideoEditorProject project) {
        mProject = project;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mProject == null) {
            return;
        }

        final long durationMs = mProject.computeDuration();
        final long durationSec = durationMs / 1000;
        if (durationMs == 0 || durationSec == 0) {
            final String timeText = StringUtils.getSimpleTimestampAsString(getContext(), 0);
            canvas.drawText(timeText, (getWidth() / 2) - 35, 28, mTextPaint);
            return;
        }

        final int width = getWidth() - mScreenWidth;
        // Compute the number of pixels per second
        final int pixelsPerSec = (int)(width / durationSec);

        // Compute the distance between ticks
        final long tickMs;
        if (pixelsPerSec < 4) {
            tickMs = 240000;
        } else if (pixelsPerSec < 6) {
            tickMs = 120000;
        } else if (pixelsPerSec < 10) {
            tickMs = 60000;
        } else if (pixelsPerSec < 50) {
            tickMs = 10000;
        } else if (pixelsPerSec < 200) {
            tickMs = 5000;
        } else {
            tickMs = 1000;
        }

        final float spacing = ((float)(width * tickMs) / (float)durationMs);
        final float startX = Math.max(mScrollX - (((mScrollX - (mScreenWidth / 2)) % spacing)),
                mScreenWidth / 2);
        float startMs = ((tickMs * (startX - (mScreenWidth / 2))) / spacing);
        startMs = Math.round(startMs);
        startMs -= (startMs % tickMs);

        final float endX = mScrollX + mScreenWidth;
        final Context context = getContext();
        for (float i = startX; i <= endX; i += spacing, startMs += tickMs) {
            final String timeText = StringUtils.getSimpleTimestampAsString(context, (long)startMs);
            canvas.drawText(timeText, i - 35, 28, mTextPaint);
            canvas.drawLine(i, 0, i, mTicksHeight, mLinePaint);
        }
    }
}
