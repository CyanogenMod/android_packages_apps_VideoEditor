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

package com.android.videoeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

/**
 * Adapter which displays a list of supported effects
 */
public class EffectsAdapter extends BaseAdapterWithImages<Integer> {
    // Instance variables
    private final EffectType[] mEffects;

    /**
     * Constructor
     *
     * @param context The context
     * @param listView The list view
     * @param category The category
     */
    public EffectsAdapter(Context context, AbsListView listView, int category) {
        super(context, listView);

        mEffects = EffectType.getEffects(context, category);
    }

    /**
     * @return The array of effects
     */
    public EffectType[] getEffects() {
        return mEffects;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mEffects.length;
    }

    /*
     * {@inheritDoc}
     */
    public Object getItem(int position) {
        return mEffects[position];
    }

    /*
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public View getView(int position, View convertView, ViewGroup parent) {
        final ImageTextViewHolder<Integer> viewHolder;
        final View rowView;
        if (convertView == null) {
            final LayoutInflater vi = (LayoutInflater)mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            rowView = vi.inflate(R.layout.image_with_text_row_view, null);
            viewHolder = new ImageTextViewHolder<Integer>(rowView);
            rowView.setTag(viewHolder);
        } else {
            rowView = convertView;
            viewHolder = (ImageTextViewHolder<Integer>)convertView.getTag();
        }

        final EffectType effect = mEffects[position];
        initiateLoad(effect.getType(), effect.getType(), viewHolder);

        // Set the data in the views
        viewHolder.mNameView.setText(effect.getName());

        return rowView;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected Bitmap loadImage(Object data) {
        return BitmapFactory.decodeResource(mContext.getResources(),
                EffectType.EFFECT_RESOURCE_IDS[(Integer)data]);
    }
}
