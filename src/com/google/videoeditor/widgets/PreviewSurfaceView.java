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

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;

/**
 * The preview surface view
 */
public class PreviewSurfaceView extends SurfaceView {
    private GestureDetector mSimpleGestureDetector;

    /*
     * {@inheritDoc}
     */
    public PreviewSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /*
     * {@inheritDoc}
     */
    public PreviewSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public PreviewSurfaceView(Context context) {
        this(context, null, 0);
    }

    /**
     * @param detector The gesture detector
     */
    public void setGestureListener(GestureDetector detector) {
        mSimpleGestureDetector = detector;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the gesture detector inspect all events.
        if (mSimpleGestureDetector != null) {
            mSimpleGestureDetector.onTouchEvent(ev);
        }

        super.onTouchEvent(ev);
        return true;
    }
}
