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
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.media.videoeditor.VideoEditor;
import android.os.AsyncTask;
import android.text.format.DateUtils;
import android.util.LruCache;
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
import java.util.List;


public class ProjectPickerAdapter extends BaseAdapter {
    private Context mContext;
    private Resources mResources;
    private LayoutInflater mInflater;
    private List<VideoEditorProject> mProjects;
    private int mItemWidth;
    private int mItemHeight;
    private LruCache<String, Bitmap> mPreviewBitmapCache;

    public ProjectPickerAdapter(Context context, LayoutInflater inflater,
            List<VideoEditorProject> projects) {
        mContext = context;
        mResources = context.getResources();
        mInflater = inflater;
        mProjects = projects;
        mItemWidth = (int) mResources.getDimension(R.dimen.project_picker_item_width);
        mItemHeight = (int) mResources.getDimension(R.dimen.project_picker_item_height);

        // Limit the cache size to 15 thumbnails.
        mPreviewBitmapCache = new LruCache<String, Bitmap>(15);
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
        // Inflate a new view with project thumbnail and information.
        // We never reuse convertView because we load thumbnails asynchronously
        // and hook an async task with the new view. If the new view is reused
        // as a convertView, the async task might put a wrong thumbnail on it.
        View v = mInflater.inflate(R.layout.project_picker_item, null);
        ImageView iv = (ImageView) v.findViewById(R.id.thumbnail);
        Bitmap thumbnail;
        TextView titleView = (TextView) v.findViewById(R.id.title);
        TextView durationView = (TextView) v.findViewById(R.id.duration);
        if (position == mProjects.size()) {
            thumbnail = renderNewProjectThumbnail();
            titleView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            titleView.setText(mContext.getString(R.string.projects_new_project));
            durationView.setText("");
        } else {
            VideoEditorProject project = mProjects.get(position);
            thumbnail = getThumbnail(project.getPath(), iv);
            titleView.setText(project.getName());
            durationView.setText(millisecondsToTimeString(project.getProjectDuration()));
        }

        if (thumbnail != null) {
            iv.setImageBitmap(thumbnail);
        }

        return v;
    }

    private Bitmap getThumbnail(String projectPath, ImageView imageView) {
        Bitmap previewBitmap = mPreviewBitmapCache.get(projectPath);
        if (previewBitmap == null) {
            // Cache miss: asynchronously load bitmap to avoid scroll stuttering
            // in the project picker.
            new LoadPreviewBitmapTask(projectPath, imageView, mItemWidth, mItemHeight,
                    mPreviewBitmapCache).execute();
        } else {
            return previewBitmap;
        }

        return null;
    }

    private Bitmap renderNewProjectThumbnail() {
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
                R.drawable.add_video_project_big);
        final int x = (mItemWidth - newProjectIcon.getWidth()) / 2;
        final int y = (mItemHeight - newProjectIcon.getHeight()) / 2;
        canvas.drawBitmap(newProjectIcon, x, y, paint);
        newProjectIcon.recycle();

        return bitmap;
    }

    /**
     * Converts milliseconds into the string time format HH:mm:ss.
     */
    private String millisecondsToTimeString(long milliseconds) {
        return DateUtils.formatElapsedTime(milliseconds / 1000);
    }
}

/**
 * Worker that loads preview bitmap for a project,
 */
class LoadPreviewBitmapTask extends AsyncTask<Void, Void, Bitmap> {
    private String mProjectPath;
    // Handle to the image view we should update when the preview bitmap is loaded.
    private ImageView mImageView;
    private int mWidth;
    private int mHeight;
    private LruCache<String, Bitmap> mPreviewBitmapCache;

    public LoadPreviewBitmapTask(String projectPath, ImageView imageView,
            int width, int height, LruCache<String, Bitmap> previewBitmapCache) {
        mProjectPath = projectPath;
        mImageView = imageView;
        mWidth = width;
        mHeight = height;
        mPreviewBitmapCache = previewBitmapCache;
    }

    @Override
    protected Bitmap doInBackground(Void... param) {
        final File thumbnail = new File(mProjectPath, VideoEditor.THUMBNAIL_FILENAME);
        // Return early if thumbnail does not exist.
        if (!thumbnail.exists()) {
            return null;
        }

        try {
            final Bitmap previewBitmap = ImageUtils.scaleImage(
                    thumbnail.getAbsolutePath(),
                    mWidth - 10,
                    mHeight - 10,
                    ImageUtils.MATCH_SMALLER_DIMENSION);
            if (previewBitmap != null) {
                final Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight,
                        Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

                // Draw bitmap at the center of the canvas.
                canvas.drawBitmap(previewBitmap,
                        (mWidth - previewBitmap.getWidth()) / 2,
                        (mHeight - previewBitmap.getHeight()) / 2,
                        paint);
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        // If we successfully load the preview bitmap, update the image view.
        if (result != null) {
            mPreviewBitmapCache.put(mProjectPath, result);
            mImageView.setImageBitmap(result);
        }
    }
}
