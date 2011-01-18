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

import com.android.ex.carousel.CarouselController;
import com.android.ex.carousel.CarouselView;
import com.android.ex.carousel.CarouselView.Info;
import com.google.videoeditor.R;

public class ProjectsCarouselView extends CarouselView {
    /*
     * {@inheritDoc}
     */
    public ProjectsCarouselView(Context context, CarouselController controller) {
        this(context, null, controller);
    }

    /*
     * {@inheritDoc}
     */
    public ProjectsCarouselView(Context context, AttributeSet attrs) {
        this(context, attrs, new CarouselController());
    }

    /*
     * {@inheritDoc}
     */
    public ProjectsCarouselView(Context context, AttributeSet attrs, CarouselController controller) {
        super(context, attrs, controller);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean interpretLongPressEvents() {
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public Info getRenderScriptInfo() {
        return new Info(R.raw.carousel);
    }
}
