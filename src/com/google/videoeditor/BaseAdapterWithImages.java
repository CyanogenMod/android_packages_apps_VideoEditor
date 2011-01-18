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

package com.google.videoeditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.view.View;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The base class for BaseAdapters which load images
 */
public abstract class BaseAdapterWithImages<T> extends BaseAdapter {
    // Instance variables
    protected final Context mContext;
    private final List<ImageViewHolder<T>> mViewHolders;
    private final Map<T, T> mLoadingImages;
    private final AbsListView mListView;

    /**
     * View holder class
     */
    protected static class ImageViewHolder<T> {
        // Instance variables
        private final ImageView mImageView;
        private T mKey;

        /**
         * Constructor
         *
         * @param rowView The row view
         */
        public ImageViewHolder(View rowView) {
            mImageView = (ImageView)rowView.findViewById(R.id.item_preview);
        }

        /**
         * @param key The key
         */
        public void setKey(T key) {
            mKey = key;
        }
    }

    /**
     * View holder class
     */
    protected static class ImageTextViewHolder<T> extends ImageViewHolder<T> {
        // Instance variables
        protected final TextView mNameView;

        /**
         * Constructor
         *
         * @param rowView The row view
         */
        public ImageTextViewHolder(View rowView) {
            super(rowView);
            mNameView = (TextView)rowView.findViewById(R.id.item_name);
        }
    }

    /**
     * Image loader class
     */
    protected class ImageLoaderAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        // Instance variables
        private final T mKey;
        private final Object mData;

        /**
         * Constructor
         *
         * @param key The bitmap key
         * @param data The data
         */
        public ImageLoaderAsyncTask(T key, Object data) {
            mKey = key;
            mData = data;
        }

        /*
         * {@inheritDoc}
         */
        @Override
        protected Bitmap doInBackground(Void... zzz) {
            return loadImage(mData);
        }

        /*
         * {@inheritDoc}
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mLoadingImages.remove(mKey);
            if (bitmap == null) {
                return;
            }

            for (ImageViewHolder<T> viewHolder : mViewHolders) {
                if (mKey.equals(viewHolder.mKey)) {
                    viewHolder.mImageView.setImageBitmap(bitmap);
                    return;
                }
            }

            bitmap.recycle();
        }
    };

    /**
     * Constructor
     *
     * @param context The context
     * @param listView The list view
     */
    public BaseAdapterWithImages(Context context, AbsListView listView) {
        mContext = context;
        mListView = listView;
        mLoadingImages = new HashMap<T, T>();
        mViewHolders = new ArrayList<ImageViewHolder<T>>();

        listView.setRecyclerListener(new AbsListView.RecyclerListener() {
            /*
             * {@inheritDoc}
             */
            @SuppressWarnings("unchecked")
            public void onMovedToScrapHeap(View view) {
                final ImageViewHolder<T> viewHolder = (ImageViewHolder<T>)view.getTag();

                mViewHolders.remove(viewHolder);
                viewHolder.setKey(null);

                final BitmapDrawable drawable
                        = (BitmapDrawable)viewHolder.mImageView.getDrawable();
                if (drawable != null && drawable.getBitmap() != null) {
                    viewHolder.mImageView.setImageDrawable(null);
                    drawable.getBitmap().recycle();
                }
            }
        });
    }

    /**
     * The activity is resumed
     */
    public void onResume() {
    }

    /**
     * The activity is paused
     */
    public void onPause() {
        mViewHolders.clear();
    }

    /**
     * The activity is destroyed
     */
    public void onDestroy() {
        final int count = mListView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View rowView = mListView.getChildAt(i);
            final ImageView imageView = (ImageView)rowView.findViewById(R.id.item_preview);
            final BitmapDrawable drawable = (BitmapDrawable)imageView.getDrawable();
            if (drawable != null && drawable.getBitmap() != null) {
                drawable.getBitmap().recycle();
            }
        }

        mListView.removeViews(0, count);
        System.gc();
    }

    /**
     * Start the AsyncTask which loads the bitmap
     *
     * @param key The bitmap key
     * @param data The data
     * @param viewHolder The view holder
     */
    protected void initiateLoad(T key, Object data, ImageViewHolder<T> viewHolder) {
        // The adapter may recycle a view and then reuse it
        if (!mViewHolders.contains(viewHolder)) {
            mViewHolders.add(viewHolder);
        }
        viewHolder.setKey(key);

        if (!mLoadingImages.containsKey(key)) {
            mLoadingImages.put(key, key);
            new ImageLoaderAsyncTask(key, data).execute();
        }
    }

    /*
     * {@inheritDoc}
     */
    public long getItemId(int position) {
        return position;
    }

    /*
     * {@inheritDoc}
     */
    public abstract int getCount();

    /**
     * Load an image based on its key
     *
     * @param data The data required to load the image
     *
     * @return The bitmap
     */
    protected abstract Bitmap loadImage(Object data);
}
