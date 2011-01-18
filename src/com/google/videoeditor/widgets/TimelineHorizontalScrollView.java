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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.HorizontalScrollView;

/**
 * The timeline scroll view
 */
public class TimelineHorizontalScrollView extends HorizontalScrollView {
    // Instance variables
    private final List<ScrollViewListener> mScrollListenerList;
    private final Handler mHandler;
    private ScaleGestureDetector mScaleDetector;
    private int mLastScrollX;
    private boolean mIsScrolling;
    private boolean mAppScroll;
    private boolean mEnableUserScrolling;

    // The runnable which executes when the scrolling ends
    private Runnable mScrollEndedRunnable = new Runnable() {
        /*
         * {@inheritDoc}
         */
        public void run() {
            mIsScrolling = false;

            for (ScrollViewListener listener : mScrollListenerList) {
                listener.onScrollEnd(TimelineHorizontalScrollView.this, getScrollX(),
                        getScrollY(), mAppScroll);
            }

            mAppScroll = false;
        }
    };

    /*
     * {@inheritDoc}
     */
    public TimelineHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mEnableUserScrolling = true;
        mScrollListenerList = new ArrayList<ScrollViewListener>();
        mHandler = new Handler();
    }

    /*
     * {@inheritDoc}
     */
    public TimelineHorizontalScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public TimelineHorizontalScrollView(Context context) {
        this(context, null, 0);
    }

    /**
     * Invoked to enable/disable user scrolling (as opposed to programmatic scrolling)
     * @param enable true to enable user scrolling
     */
    public void enableUserScrolling(boolean enable) {
        mEnableUserScrolling = enable;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mScaleDetector.onTouchEvent(ev);
        return mScaleDetector.isInProgress() || super.onInterceptTouchEvent(ev);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mEnableUserScrolling) {
            mScaleDetector.onTouchEvent(ev);

            if (!mScaleDetector.isInProgress()) {
                return super.onTouchEvent(ev);
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    /**
     * @param listener The scale listener
     */
    public void setScaleListener(ScaleGestureDetector.SimpleOnScaleGestureListener listener) {
        mScaleDetector = new ScaleGestureDetector(getContext(), listener);
    }

    /**
     * @param listener The listener
     */
    public void addScrollListener(ScrollViewListener listener) {
        mScrollListenerList.add(listener);
    }

    /**
     * @param listener The listener
     */
    public void removeScrollListener(ScrollViewListener listener) {
        mScrollListenerList.remove(listener);
    }

    /**
     * @return true if scrolling is in progress
     */
    public boolean isScrolling() {
        return mIsScrolling;
    }

    /**
     * The app wants to scroll (as opposed to the user)
     *
     * @param scrollX Horizontal scroll position
     * @param smooth true to scroll smoothly
     */
    public void appScrollTo(int scrollX, boolean smooth) {
        if (getScrollX() == scrollX) {
            return;
        }

        mAppScroll = true;

        if (smooth) {
            smoothScrollTo(scrollX, 0);
        } else {
            scrollTo(scrollX, 0);
        }
    }

    /**
     * The app wants to scroll (as opposed to the user)
     *
     * @param scrollX Horizontal scroll offset
     * @param smooth true to scroll smoothly
     */
    public void appScrollBy(int scrollX, boolean smooth) {
        mAppScroll = true;

        if (smooth) {
            smoothScrollBy(scrollX, 0);
        } else {
            scrollBy(scrollX, 0);
        }
    }

    /*
     * {@inheritDoc}
     */
    public void computeScroll() {
        super.computeScroll();

        final int scrollX = getScrollX();
        if (mLastScrollX != scrollX) {
            mLastScrollX = scrollX;

            // Cancel the previous event
            mHandler.removeCallbacks(mScrollEndedRunnable);

            // Post a new event
            mHandler.postDelayed(mScrollEndedRunnable, 300);

            final int scrollY = getScrollY();
            if (mIsScrolling) {
                for (ScrollViewListener listener : mScrollListenerList) {
                    listener.onScrollProgress(this, scrollX, scrollY, mAppScroll);
                }
            } else {
                mIsScrolling = true;

                for (ScrollViewListener listener : mScrollListenerList) {
                    listener.onScrollBegin(this, scrollX, scrollY, mAppScroll);
                }
            }
        }
    }
}
