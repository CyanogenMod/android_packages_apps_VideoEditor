/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Style;
import android.media.videoeditor.VideoEditor;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.ex.carousel.CarouselView;
import com.android.ex.carousel.CarouselView.DetailAlignment;
import com.google.videoeditor.R;
import com.google.videoeditor.service.VideoEditorProject;
import com.google.videoeditor.util.ImageUtils;
import com.google.videoeditor.util.StringUtils;

/**
 * The carousel helper
 */
public class ProjectsCarouselViewHelper extends com.android.ex.carousel.CarouselViewHelper {
    // Logging
    private static final String TAG = "ProjectsCarouselView";

    // Carousel behavior parameters
    private static final int CARD_SLOTS_LANDSCAPE = 12;
    private static final int CARD_SLOTS_PORTRAIT = 24;

    // Visible slots and details
    private static final int SLOTS_VISIBLE_LANDSCAPE = 3;
    private static final int SLOTS_VISIBLE_PORTRAIT = 3;

    // Instance variables
    private final Context mContext;
    private final int mCarouselPixelBorder;
    private final int mCarouselTextureWidth;
    private final int mCarouselTextureHeight;
    private final int mCarouselDetailTextureWidth;
    private final int mCarouselDetailTextureHeight;
    private final ProjectsCarouselView mView;
    private final Paint mPaint;
    private final Handler mSyncHandler;
    private final DetailTextureParameters mDetailTextureParameters;
    private final CarouselItemListener mCarouselItemListener;

    private List<VideoEditorProject> mProjects = new ArrayList<VideoEditorProject>();

    /**
     * The carousel item listener
     */
    public interface CarouselItemListener {
        /**
         * A carousel item was tapped
         *
         * @param projectPath The project path
         *      (null if the "New project" item was clicked)
         */
        public void onCarouselItemTap(String projectPath);

        /**
         * A carousel item was long pressed
         *
         * @param projectPath The project path
         *      (null if the "New project" item was clicked)
         * @param anchorView The view that anchors the popup menu
         */
        public void onCarouselItemLongPress(String projectPath, View anchorView);
    }

    /**
     * Constructor
     *
     * @param context The context
     * @param view The carousel view
     * @param listener The listener
     */
    public ProjectsCarouselViewHelper(Context context, ProjectsCarouselView view,
            CarouselItemListener listener) {
        super(context, view);

        mContext = context;
        mCarouselItemListener = listener;

        final Resources resources = context.getResources();
        mCarouselPixelBorder = (int)resources.getDimension(R.dimen.carousel_pixel_border);
        mCarouselTextureHeight = (int)resources.getDimension(R.dimen.carousel_texture_height);
        mCarouselDetailTextureHeight = (int)resources.getDimension(
                R.dimen.carousel_detail_texture_height);

        mView = view;
        mView.setCallback(this);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mDetailTextureParameters = new DetailTextureParameters(0.0f, 5.0f, 0.0f, 0.0f);

        final Display display =
            ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        final int orientation = display.getRotation();
        final boolean landscape = (orientation == Surface.ROTATION_0 ||
                orientation == Surface.ROTATION_180);

        // Set the aspect ration of the item to 4/3 (0.75f)
        mCarouselTextureWidth = (mCarouselTextureHeight * 4) / 3;
        final float[] matrix = new float[] {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.75f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f};
        mView.setDefaultCardMatrix(matrix);

        final Bitmap border = BitmapFactory.decodeResource(resources, R.drawable.border);
        mView.setDefaultBitmap(border);
        mView.setLoadingBitmap(border);

        mView.createCards(0);
        mView.setBackgroundColor(0.0f, 0.0f, 0.0f, 0.0f);
        mView.setRezInCardCount(0.0f);
        mView.setFadeInDuration(250);
        mView.setCardRotation(-(float) Math.PI / 2.0f);
        mView.setDragModel(CarouselView.DRAG_MODEL_CYLINDER_INSIDE);
        mView.setCardsFaceTangent(true);
        mView.setDrawRuler(false);
        mView.setDetailTextureAlignment(DetailAlignment.CENTER_HORIZONTAL | DetailAlignment.BELOW);

        final float[] eye;
        final float[] at;
        final float[] up;
        if (landscape) {
            mCarouselDetailTextureWidth = (int)resources.getDimension(
                    R.dimen.carousel_detail_texture_width_landscape);
            mView.setVisibleSlots(SLOTS_VISIBLE_LANDSCAPE);
            mView.setVisibleDetails(SLOTS_VISIBLE_LANDSCAPE);

            mView.setStartAngle((float) (2.0f * Math.PI * 4 / CARD_SLOTS_LANDSCAPE));
            mView.setSlotCount(CARD_SLOTS_LANDSCAPE);
            mView.setRadius(4.0f);

            eye = new float[] {0.0f, 0.0f, 4.0f};
            at = new float[] {0.0f, 0.0f, -15.0f};
            up = new float[] {0.0f, 1.0f, 0.0f};
        } else { // Portrait
            mCarouselDetailTextureWidth = (int)resources.getDimension(
                    R.dimen.carousel_detail_texture_width_portrait);
            mView.setVisibleSlots(SLOTS_VISIBLE_PORTRAIT);
            mView.setVisibleDetails(SLOTS_VISIBLE_PORTRAIT);

            mView.setStartAngle((float) (2.0f * Math.PI * 7 / CARD_SLOTS_PORTRAIT));
            mView.setSlotCount(CARD_SLOTS_PORTRAIT);
            mView.setRadius(8.0f);

            eye = new float[] {0.0f, 0.0f, 8.0f};
            at = new float[] {0.0f, 0.0f, -30.0f};
            up = new float[] {0.0f, 1.0f, 0.0f};
        }

        mView.setLookAt(eye, at, up);

        mView.getController().setFillDirection(CarouselView.FILL_DIRECTION_CW);

        mSyncHandler = new Handler();
    }

    /**
     * @param projects The projects
     */
    public void setProjects(List<VideoEditorProject> projects) {
        mView.createCards(0);
        // Add one item for the "New project" item
        mView.createCards(projects.size() + 1);
        mProjects = projects;
    }

    /**
     * Remove a project
     *
     * @param projectPath The project path
     */
    public void removeProject(String projectPath) {
        int id = 0;
        for (VideoEditorProject project : mProjects) {
            if (project.getPath().equals(projectPath)) {
                mProjects.remove(project);
                mView.createCards(id);
                mView.createCards(mProjects.size() + 1);
                break;
            } else {
                id++;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public DetailTextureParameters getDetailTextureParameters(int id) {
        return mDetailTextureParameters;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public Bitmap getTexture(int id) {
        final Bitmap bitmap = Bitmap.createBitmap(mCarouselTextureWidth, mCarouselTextureHeight,
                Bitmap.Config.ARGB_8888);

        final Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(0, 0, 0, 0);

        if (id < mProjects.size()) {
            // Clip the canvas so that anti-aliasing will occur
            canvas.clipRect(mCarouselPixelBorder, mCarouselPixelBorder,
                    mCarouselTextureWidth - mCarouselPixelBorder,
                    mCarouselTextureHeight - mCarouselPixelBorder);

            final File thumbnail = new File(mProjects.get(id).getPath(),
                    VideoEditor.THUMBNAIL_FILENAME);
            if (thumbnail.exists()) {
                try {
                    final Bitmap previewBitmap = ImageUtils.scaleImage(thumbnail.getAbsolutePath(),
                            mCarouselTextureWidth, mCarouselTextureHeight,
                            ImageUtils.MATCH_LARGER_DIMENSION);
                    if (previewBitmap != null) {
                        mPaint.setAlpha(255);
                        canvas.drawBitmap(previewBitmap,
                                (mCarouselTextureWidth - previewBitmap.getWidth()) / 2,
                                (mCarouselTextureHeight - previewBitmap.getHeight()) / 2, mPaint);
                        previewBitmap.recycle();
                    } else {
                        canvas.drawColor(0xff000000);
                    }
                } catch (IOException ex) {
                    Log.w(TAG, "Cannot load: " + thumbnail.getAbsolutePath());
                }
            } else {
                canvas.drawColor(0xff000000);
            }
        } else {
            final int halfBorderWidth = mCarouselPixelBorder / 2;
            // Clip the canvas so that anti-aliasing will occur
            canvas.clipRect(halfBorderWidth, halfBorderWidth,
                    mCarouselTextureWidth - halfBorderWidth,
                    mCarouselTextureHeight - halfBorderWidth);

            mPaint.setColor(0xffffffff);
            mPaint.setStyle(Style.STROKE);

            canvas.drawRect(mCarouselPixelBorder, mCarouselPixelBorder,
                    mCarouselTextureWidth - mCarouselPixelBorder,
                    mCarouselTextureHeight - mCarouselPixelBorder, mPaint);

            mPaint.setTextSize(18.0f);
            mPaint.setAlpha(255);

            final Bitmap newProjectBitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_menu_add_video);

            final int x = (mCarouselTextureWidth - newProjectBitmap.getWidth()) / 2;
            final int y = (mCarouselTextureHeight - newProjectBitmap.getHeight()) / 2;
            canvas.drawBitmap(newProjectBitmap, x - newProjectBitmap.getWidth() - 10, y, mPaint);
            newProjectBitmap.recycle();

            canvas.drawText(mContext.getString(R.string.projects_new_project), x, y + 18, mPaint);
        }

        return bitmap;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public Bitmap getDetailTexture(int id) {
        final Bitmap bitmap = Bitmap.createBitmap(mCarouselDetailTextureWidth,
                mCarouselDetailTextureHeight, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        if (id < mProjects.size()) {
            final VideoEditorProject project = mProjects.get(id);
            String projectName = project.getName();
            if (projectName == null) {
                projectName = mContext.getString(R.string.untitled);
            }

            mPaint.setColor(0xffffffff);
            mPaint.setTextSize(22.0f);
            mPaint.setTypeface(Typeface.DEFAULT_BOLD);

            final int chars = mPaint.breakText(projectName, true, mCarouselDetailTextureWidth - 12,
                    null);
            if (chars < projectName.length()) {
                projectName = projectName.substring(0, chars);
                projectName += "...";
            }

            float textWidth = mPaint.measureText(projectName);
            canvas.drawText(projectName, (mCarouselDetailTextureWidth - textWidth) / 2,
                    mCarouselDetailTextureHeight / 2, mPaint);

            mPaint.setTextSize(16.0f);
            mPaint.setTypeface(Typeface.DEFAULT);
            final String durationString = StringUtils.getDurationAsString(mContext,
                    project.getProjectDuration());
            textWidth = mPaint.measureText(durationString);
            canvas.drawText(durationString, (mCarouselDetailTextureWidth - textWidth) / 2,
                    (mCarouselDetailTextureHeight / 2) + mPaint.getTextSize() + 10, mPaint);
        }

        return bitmap;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCardSelected(final int id) {
        mSyncHandler.post(new Runnable(){
            /*
             * {@inheritDoc}
             */
            public void run() {
                handleTapAction(id);
            }
        });
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCardLongPress(final int id, final int touchPosition[], Rect detailCoordinates) {
        mSyncHandler.post(new Runnable(){
            /*
             * {@inheritDoc}
             */
            public void run() {
                handleLongPressAction(id, touchPosition);
            }
        });
    }

    /**
     * Handle the tap action
     *
     * @param id The item id
     */
    private void handleTapAction(int id) {
        if (id < mProjects.size()) {
            mCarouselItemListener.onCarouselItemTap(mProjects.get(id).getPath());
        } else {
            mCarouselItemListener.onCarouselItemTap(null);
        }
    }

    /**
     * Handle the long press action
     *
     * @param id The item id
     */
    private void handleLongPressAction(final int id, int touchPosition[]) {
        if (id >= mProjects.size()) {
            return;
        }

        // Move the anchor view at the touch position
        final View anchorView = ((View)mView.getParent()).findViewById(R.id.menu_anchor_view);
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)anchorView.getLayoutParams();
        lp.leftMargin = touchPosition[0];
        lp.topMargin = touchPosition[1];
        anchorView.setLayoutParams(lp);

        mCarouselItemListener.onCarouselItemLongPress(mProjects.get(id).getPath(), anchorView);
    }
}
