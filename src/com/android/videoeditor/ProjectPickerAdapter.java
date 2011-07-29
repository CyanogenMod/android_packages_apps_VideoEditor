/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.videoeditor;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.media.videoeditor.VideoEditor;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.ImageUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;


public class ProjectPickerAdapter extends BaseAdapter {
    private Context mContext;
    private Resources mResources;
    private LayoutInflater mInflater;
    private List<VideoEditorProject> mProjects;
    private int mItemWidth;
    private int mItemHeight;

    public ProjectPickerAdapter(Context context, LayoutInflater inflater,
            List<VideoEditorProject> projects) {
        mContext = context;
        mResources = context.getResources();
        mInflater = inflater;
        mProjects = projects;
        mItemWidth = (int) mResources.getDimension(R.dimen.project_picker_item_width);
        mItemHeight = (int) mResources.getDimension(R.dimen.project_picker_item_height);
    }

    /**
     * Clears project list and update display.
     */
    public void clear() {
        mProjects.clear();
        notifyDataSetChanged();
    }

    /**
     * Removes the project with specified {@code projectPath} from the project list and updates the
     * display.
     *
     * @param projectPath The project path of the to-be-removed project
     * @return {code true} if the project is successfully removed,
     *      {code false} if no removal happened
     */
    public boolean remove(String projectPath) {
        for (VideoEditorProject project : mProjects) {
            if (project.getPath().equals(projectPath)) {
                if (mProjects.remove(project)) {
                    notifyDataSetChanged();
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public int getCount() {
        // Add one to represent an additional dummy project for "create new project" option.
        return mProjects.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == mProjects.size()) {
            return null;
        }
        return mProjects.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v;
        if (convertView != null) {
            v = convertView;
        } else {
            v = mInflater.inflate(R.layout.project_picker_item, null);
        }

        // Inflate the view with project thumbnail and information.
        ImageView iv = (ImageView) v.findViewById(R.id.thumbnail);
        Drawable thumbnail;
        TextView titleView = (TextView) v.findViewById(R.id.title);
        TextView durationView = (TextView) v.findViewById(R.id.duration);
        if (position == mProjects.size()) {
            thumbnail = renderNewProjectThumbnail();
            titleView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            titleView.setText(mContext.getString(R.string.projects_new_project));
            durationView.setText("");
        } else {
            VideoEditorProject project = mProjects.get(position);
            thumbnail = getThumbnail(project.getPath());
            titleView.setText(project.getName());
            durationView.setText(millisecondsToTimeString(project.getProjectDuration()));
        }
        iv.setImageDrawable(thumbnail);

        return v;
    }

    private Drawable getThumbnail(String projectPath) {
        final File thumbnail = new File(projectPath, VideoEditor.THUMBNAIL_FILENAME);
        final Bitmap bitmap = Bitmap.createBitmap(mItemWidth, mItemHeight,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();
        paint.setAntiAlias(true);

        if (thumbnail.exists()) {
            try {
                final Bitmap previewBitmap = ImageUtils.scaleImage(
                        thumbnail.getAbsolutePath(),
                        mItemWidth - 10,
                        mItemHeight - 10,
                        ImageUtils.MATCH_SMALLER_DIMENSION);
                if (previewBitmap != null) {
                    paint.setAlpha(255);
                    // Draw the thumbnail at the center of the canvas in case scaled preview
                    // bitmap is smaller than the container.
                    canvas.drawBitmap(previewBitmap,
                            (mItemWidth - previewBitmap.getWidth()) / 2,
                            (mItemHeight - previewBitmap.getHeight()) / 2,
                            paint);
                    previewBitmap.recycle();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new BitmapDrawable(bitmap);
    }

    private Drawable renderNewProjectThumbnail() {
        final Bitmap bitmap = Bitmap.createBitmap(mItemWidth, mItemHeight,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();

        paint.setColor(Color.WHITE);
        paint.setStyle(Style.STROKE);
        // Hairline mode, 1 pixel wide.
        paint.setStrokeWidth(0);

        canvas.drawRect(0, 0, mItemWidth-1, mItemHeight-1, paint);

        paint.setTextSize(18.0f);
        paint.setAlpha(255);
        final Bitmap newProjectIcon = BitmapFactory.decodeResource(mResources,
                R.drawable.ic_menu_add_video);
        final int x = (mItemWidth - newProjectIcon.getWidth()) / 2;
        final int y = (mItemHeight - newProjectIcon.getHeight()) / 2;
        canvas.drawBitmap(newProjectIcon, x, y, paint);
        newProjectIcon.recycle();

        return new BitmapDrawable(bitmap);
    }

    /**
     * Converts milliseconds into the string time format HH:mm:ss.
     */
    private String millisecondsToTimeString(long milliseconds) {
        return DateUtils.formatElapsedTime(milliseconds / 1000);
    }
}
