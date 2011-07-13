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


package com.android.videoeditor.widgets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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

import com.google.android.opengl.carousel.CarouselView;
import com.google.android.opengl.carousel.CarouselView.DetailAlignment;
import com.google.android.opengl.carousel.CarouselViewHelper;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.ImageUtils;
import com.android.videoeditor.util.StringUtils;
import com.android.videoeditor.R;

/**
 * Helper class for manipulating projects carousel view.
 */
public class ProjectsCarouselViewHelper extends CarouselViewHelper {
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
    private final ProjectsCarouselView mProjectsCarouselView;
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
     * @param projectsCarouselView The carousel view manipulated by this helper
     * @param listener The listener that responds to user actions on the carousel view
     */
    public ProjectsCarouselViewHelper(Context context, ProjectsCarouselView projectsCarouselView,
            CarouselItemListener listener) {
        super(context, projectsCarouselView);

        mContext = context;
        mCarouselItemListener = listener;

        final Resources resources = context.getResources();
        mCarouselPixelBorder = (int)resources.getDimension(R.dimen.carousel_pixel_border);
        mCarouselTextureHeight = (int)resources.getDimension(R.dimen.carousel_texture_height);
        mCarouselDetailTextureHeight = (int)resources.getDimension(
                R.dimen.carousel_detail_texture_height);

        mProjectsCarouselView = projectsCarouselView;
        mProjectsCarouselView.setCallback(this);

        // Set carousel transparent, so we can use background from other activity.
        mProjectsCarouselView.setFormat(PixelFormat.TRANSPARENT);
        mProjectsCarouselView.setZOrderOnTop(true);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mDetailTextureParameters = new DetailTextureParameters(0.0f, 5.0f, 0.0f, 0.0f);

        // Get the correct measure of whether the device is on landscape mode, irrelevant whether
        // it's a phone of tablet.
        final boolean landscape = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        // Set the aspect ration of the item to 4/3 (0.75f)
        mCarouselTextureWidth = (mCarouselTextureHeight * 4) / 3;
        final float[] matrix = new float[] {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.75f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f};
        mProjectsCarouselView.setDefaultCardMatrix(matrix);

        final Bitmap border = BitmapFactory.decodeResource(resources, R.drawable.border);
        mProjectsCarouselView.setDefaultBitmap(border);
        mProjectsCarouselView.setLoadingBitmap(border);

        mProjectsCarouselView.createCards(0);
        mProjectsCarouselView.setBackgroundColor(0.0f, 0.0f, 0.0f, 0.0f);
        mProjectsCarouselView.setRezInCardCount(0.0f);
        mProjectsCarouselView.setFadeInDuration(250);
        mProjectsCarouselView.setCardRotation(-(float) Math.PI / 2.0f);
        mProjectsCarouselView.setDragModel(CarouselView.DRAG_MODEL_CYLINDER_INSIDE);
        mProjectsCarouselView.setCardsFaceTangent(true);
        mProjectsCarouselView.setDrawRuler(false);
        mProjectsCarouselView.setDetailTextureAlignment(DetailAlignment.CENTER_HORIZONTAL | DetailAlignment.BELOW);
        // TO have smoother dragging, increase the prefetch card count.
        mProjectsCarouselView.setPrefetchCardCount(4);
        mProjectsCarouselView.setVelocityUpLimit(4 * (float) Math.PI);


        final float[] eye;
        final float[] at;
        final float[] up;
        if (landscape) {
            mCarouselDetailTextureWidth = (int)resources.getDimension(
                    R.dimen.carousel_detail_texture_width_landscape);
            mProjectsCarouselView.setVisibleSlots(SLOTS_VISIBLE_LANDSCAPE);
            mProjectsCarouselView.setVisibleDetails(SLOTS_VISIBLE_LANDSCAPE);
            mProjectsCarouselView.setStartAngle((float) (2.0f * Math.PI * 4 / CARD_SLOTS_LANDSCAPE));
            mProjectsCarouselView.setSlotCount(CARD_SLOTS_LANDSCAPE);
            mProjectsCarouselView.setRadius(4.0f);

            eye = new float[] {0.0f, 0.0f, 4.0f};
            at = new float[] {0.0f, 0.0f, -15.0f};
            up = new float[] {0.0f, 1.0f, 0.0f};
        } else {  // Portrait
            mCarouselDetailTextureWidth = (int) resources.getDimension(
                    R.dimen.carousel_detail_texture_width_portrait);
            mProjectsCarouselView.setVisibleSlots(SLOTS_VISIBLE_PORTRAIT);
            mProjectsCarouselView.setVisibleDetails(SLOTS_VISIBLE_PORTRAIT);

            mProjectsCarouselView.setStartAngle((float) (2.0f * Math.PI * 7 / CARD_SLOTS_PORTRAIT));
            mProjectsCarouselView.setSlotCount(CARD_SLOTS_PORTRAIT);
            mProjectsCarouselView.setRadius(8.0f);

            // For better look, use 2 rows in portrait mode.
            mProjectsCarouselView.setRowCount(2);
            mProjectsCarouselView.setRowSpacing(2.0f);

            eye = new float[] {0.0f, 0.0f, 8.0f};
            at = new float[] {0.0f, 0.0f, -30.0f};
            up = new float[] {0.0f, 1.0f, 0.0f};
        }

        mProjectsCarouselView.setLookAt(eye, at, up);
        mProjectsCarouselView.setFillDirection(CarouselView.FILL_DIRECTION_CW);

        mSyncHandler = new Handler();
        createBitmapPool(CarouselView.TYPE_CARD_TEXTURE, mCarouselTextureWidth,
                mCarouselTextureHeight, Bitmap.Config.ARGB_8888);
        createBitmapPool(CarouselView.TYPE_DETAIL_TEXTURE, mCarouselDetailTextureWidth,
                mCarouselDetailTextureHeight, Bitmap.Config.ARGB_4444);
    }

    /**
     * @param projects The projects
     */
    public void setProjects(List<VideoEditorProject> projects) {
        // Add one item for the "New project" item
        mProjectsCarouselView.createCards(projects.size() + 1);
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
                mProjectsCarouselView.createCards(mProjects.size() + 1);
                break;
            } else {
                id++;
            }
        }
    }

    @Override
    public DetailTextureParameters getDetailTextureParameters(int id) {
        return mDetailTextureParameters;
    }

    @Override
    public Bitmap getTexture(int id) {
        final int OPAQUE_BLACK = 0xff000000;

        final Bitmap bitmap = getBitmap(CarouselView.TYPE_CARD_TEXTURE);
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
                        // Draw the thumbnail at the center of the texture in case scaled preview
                        // bitmap is smaller than the texture.
                        canvas.drawBitmap(previewBitmap,
                                (mCarouselTextureWidth - previewBitmap.getWidth()) / 2,
                                (mCarouselTextureHeight - previewBitmap.getHeight()) / 2, mPaint);
                    } else {
                        canvas.drawColor(OPAQUE_BLACK);
                    }
                } catch (IOException ex) {
                    Log.w(TAG, "Cannot load: " + thumbnail.getAbsolutePath());
                }
            } else {
                canvas.drawColor(OPAQUE_BLACK);
            }
        } else {  // Draw new project card.
            final int halfBorderWidth = mCarouselPixelBorder / 2;
            // Clip the canvas so that anti-aliasing will occur
            canvas.clipRect(halfBorderWidth, halfBorderWidth,
                    mCarouselTextureWidth - halfBorderWidth,
                    mCarouselTextureHeight - halfBorderWidth);
            mPaint.setColor(Color.WHITE);
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

    @Override
    public Bitmap getDetailTexture(int id) {
        final Bitmap bitmap = getBitmap(CarouselView.TYPE_DETAIL_TEXTURE);
        final Canvas canvas = new Canvas(bitmap);
        if (id < mProjects.size()) {
            final VideoEditorProject project = mProjects.get(id);
            String projectName = project.getName();
            if (projectName == null) {
                projectName = mContext.getString(R.string.untitled);
            }

            mPaint.setColor(Color.WHITE);
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

    @Override
    public void onCardSelected(final int id) {
        mSyncHandler.post(new Runnable(){
            @Override
            public void run() {
                handleTapAction(id);
            }
        });
    }

    @Override
    public void onCardLongPress(final int id, final int touchPosition[], Rect detailCoordinates) {
        mSyncHandler.post(new Runnable(){
            @Override
            public void run() {
                handleLongPressAction(id, touchPosition);
            }
        });
    }

    /**
     * Handles the tap action.
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
     * Handles the long press action.
     *
     * @param id The item id
     */
    private void handleLongPressAction(final int id, int touchPosition[]) {
        if (id >= mProjects.size()) {
            // no-op if user long presses on the new project card.
            return;
        }

        // Move the anchor view to the touched position.
        final View anchorView = ((View) mProjectsCarouselView.getParent()).findViewById(R.id.menu_anchor_view);
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)anchorView.getLayoutParams();
        lp.leftMargin = touchPosition[0];
        lp.topMargin = touchPosition[1];
        anchorView.setLayoutParams(lp);

        mCarouselItemListener.onCarouselItemLongPress(mProjects.get(id).getPath(), anchorView);
    }
}
