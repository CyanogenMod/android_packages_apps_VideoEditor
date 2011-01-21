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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;

import com.google.videoeditor.R;

/**
 * The RelativeLayout which is the container for the timeline layout
 */
public class TimelineRelativeLayout extends RelativeLayout {
    // Instance variables
    private final int mHalfParentWidth;
    private final int mPlayheadMarginTop, mPlayheadMarginBottom;
    private final Drawable mPlayheadDrawable;
    private LayoutCallback mLayoutCallback;

    /**
     * Layout complete callback interface
     */
    public interface LayoutCallback {
        /**
         * This method is invoked when the layout completes after a call
         * to requestLayout(LayoutCallback)
         */
        public void onLayoutComplete();
    }

    /*
     * {@inheritDoc}
     */
    public TimelineRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Compute half the width of the screen (and therefore the parent view)
        final Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        // This value is shared by all children. It represents the width of
        // the left empty view.
        setTag(R.id.left_view_width, mHalfParentWidth);
        setTag(R.id.playhead_offset, -1);

        final Resources resources = context.getResources();

        // Get the playhead margins
        mPlayheadMarginTop = (int)resources.getDimension(R.dimen.playhead_margin_top);
        mPlayheadMarginBottom = (int)resources.getDimension(R.dimen.playhead_margin_bottom);

        // Prepare the playhead drawable
        mPlayheadDrawable = resources.getDrawable(R.drawable.playhead);

        setMotionEventSplittingEnabled(false);
    }

    /*
     * {@inheritDoc}
     */
    public TimelineRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public TimelineRelativeLayout(Context context) {
        this(context, null, 0);
    }

    /**
     * Request a layout and get a callback when the layout completes
     *
     * @param callback The layout callback
     */
    public void requestLayout(LayoutCallback callback) {
        mLayoutCallback = callback;

        requestLayout();
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mLayoutCallback != null) {
            mLayoutCallback.onLayoutComplete();
            mLayoutCallback = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        final int playheadOffset = (Integer)getTag(R.id.playhead_offset);
        final int startX;
        if (playheadOffset < 0) {
            // Draw the playhead in the middle of the screen
            startX = (((HorizontalScrollView)getParent()).getScrollX() + mHalfParentWidth);
        } else {
            // Draw the playhead at the specified position (during trimming)
            startX = playheadOffset;
        }

        // Draw the playhead
        mPlayheadDrawable.setBounds(startX - (mPlayheadDrawable.getIntrinsicWidth() / 2),
                mPlayheadMarginTop, startX + (mPlayheadDrawable.getIntrinsicWidth() / 2),
                getHeight() - mPlayheadMarginBottom);
        mPlayheadDrawable.draw(canvas);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void setSelected(boolean selected) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            if (childView instanceof ViewGroup) {
                childView.setSelected(selected);
            }
        }
    }
}
