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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The zoom control
 */
public class ZoomControl extends View {
    // The thumb radius
    private static final float RADIUS = 129;
    private static final float INTERNAL_RADIUS = 72;

    private static final double MAX_ANGLE = Math.PI / 3;

    // Instance variables
    private final Drawable mThumb;
    private int mMaxProgress, mProgress;
    private OnZoomChangeListener mListener;
    private int mThumbX, mThumbY;
    private double mInterval;

    /**
     * The zoom change listener
     */
    public interface OnZoomChangeListener {
        /**
         * The progress value has changed
         *
         * @param progress The progress value
         * @param fromUser true if the user is changing the zoom
         */
        public void onProgressChanged(int progress, boolean fromUser);
    }

    /*
     * {@inheritDoc}
     */
    public ZoomControl(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Set the default maximum progress
        mMaxProgress = 100;
        computeInterval();

        // Load the thumb selector
        mThumb = context.getResources().getDrawable(R.drawable.zoom_thumb_selector);
    }

    /*
     * {@inheritDoc}
     */
    public ZoomControl(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public ZoomControl(Context context) {
        this(context, null, 0);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void refreshDrawableState() {
        mThumb.setState(isPressed() ? PRESSED_WINDOW_FOCUSED_STATE_SET : ENABLED_STATE_SET);
        invalidate();
    }

    /**
     * @param max The maximum value
     */
    public void setMax(int max) {
        mMaxProgress = max;
        computeInterval();
    }

    /**
     * @param progress The progress
     */
    public void setProgress(int progress) {
        mProgress = progress;

        progressToPosition();
        invalidate();
    }

    /**
     * @param listener The listener
     */
    public void setOnZoomChangeListener(OnZoomChangeListener listener) {
        mListener = listener;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mThumbX == 0 && mThumbY == 0) {
            progressToPosition();
        }

        final int halfWidth = mThumb.getIntrinsicWidth() / 2;
        final int halfHeight = mThumb.getIntrinsicHeight() / 2;
        mThumb.setBounds(mThumbX - halfWidth, mThumbY - halfHeight, mThumbX + halfWidth,
                mThumbY + halfHeight);
        mThumb.setAlpha(isEnabled() ? 255 : 100);
        mThumb.draw(canvas);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (isEnabled()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isEnabled()) {
                    final float x = ev.getX() - (getWidth() / 2);
                    final float y = -(ev.getY() - (getHeight() / 2));
                    final double alpha = Math.atan((double)y / (double)x);

                    if (!checkHit(x, y, alpha)) {
                        return true;
                    }

                    final int progress;
                    if (x >= 0 && y >= 0) {
                        mThumbX = (int)((RADIUS * Math.cos(alpha)) + (getWidth() / 2));
                        mThumbY = (int)((getHeight() / 2) - (RADIUS * Math.sin(alpha)));
                        progress = (int)((mMaxProgress / 2) - (alpha / mInterval));
                    } else if (x >= 0 && y <= 0) {
                        mThumbX = (int)((RADIUS * Math.cos(alpha)) + (getWidth() / 2));
                        mThumbY = (int)((getHeight() / 2) - (RADIUS * Math.sin(alpha)));
                        progress = (int)((mMaxProgress / 2) - (alpha / mInterval));
                    } else if (x <= 0 && y >= 0) {
                        mThumbX = (int)((getWidth() / 2) - (RADIUS * Math.cos(alpha)));
                        mThumbY = (int)((getHeight() / 2) + (RADIUS * Math.sin(alpha)));
                        progress = -(int)(((alpha + MAX_ANGLE) / mInterval));
                    } else {
                        mThumbX = (int)((getWidth() / 2) - (RADIUS * Math.cos(alpha)));
                        mThumbY = (int)((getHeight() / 2) + (RADIUS * Math.sin(alpha)));
                        progress = (int)(mMaxProgress - ((alpha - MAX_ANGLE) / mInterval));
                    }

                    invalidate();

                    if (mListener != null) {
                        if (progress != mProgress) {
                            mProgress = progress;
                            mListener.onProgressChanged(mProgress, true);
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                break;
            }

            default: {
                break;
            }
        }

        return true;
    }

    /**
     * Check if the user is touching the correct area
     *
     * @param x The horizontal coordinate
     * @param y The vertical coordinate
     * @param alpha The angle
     * @return true if there is a hit in the allowed area
     */
    private boolean checkHit(float x, float y, double alpha) {
        final double radius = Math.sqrt((x * x) + (y * y));
        if (radius < INTERNAL_RADIUS) {
            return false;
        }

        if (x >= 0) {
            return true;
        } else if (y >= 0) {
            if ((alpha >= -(Math.PI / 2)) && (alpha <= -MAX_ANGLE)) {
                return true;
            }
        } else {
            if ((alpha >= MAX_ANGLE) && (alpha <= (Math.PI / 2))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Compute the position of the thumb based on the progress values
     */
    private void progressToPosition() {
        if (getWidth() == 0) { // Layout is not yet complete
            return;
        }

        final double beta;
        if (mProgress <= mMaxProgress / 2) {
            beta = ((mMaxProgress / 2) - mProgress) * mInterval;
        } else {
            beta = ((mMaxProgress - mProgress) * mInterval) + Math.PI + MAX_ANGLE;
        }

        final double alpha;
        if (beta >= 0 && beta <= Math.PI / 2) {
            alpha = beta;
            mThumbX = (int)((RADIUS * Math.cos(alpha)) + (getWidth() / 2));
            mThumbY = (int)((getHeight() / 2) - (RADIUS * Math.sin(alpha)));
        } else if (beta > Math.PI / 2 && beta < (Math.PI / 2) + MAX_ANGLE) {
            alpha = beta - Math.PI;
            mThumbX = (int)((getWidth() / 2) - (RADIUS * Math.cos(alpha)));
            mThumbY = (int)((getHeight() / 2) + (RADIUS * Math.sin(alpha)));
        } else if (beta <= 2 * Math.PI && beta > (3 * Math.PI) / 2) {
            alpha = beta - (2 * Math.PI);
            mThumbX = (int)((RADIUS * Math.cos(alpha)) + (getWidth() / 2));
            mThumbY = (int)((getHeight() / 2) - (RADIUS * Math.sin(alpha)));
        } else {
            alpha = beta - Math.PI;
            mThumbX = (int)((getWidth() / 2) - (RADIUS * Math.cos(alpha)));
            mThumbY = (int)((getHeight() / 2) + (RADIUS * Math.sin(alpha)));
        }
    }

    /**
     * Compute the radians interval between progress values
     */
    private void computeInterval() {
        mInterval = (Math.PI - MAX_ANGLE) / (mMaxProgress / 2);
    }
}
