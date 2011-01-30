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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.videoeditor.service.MovieOverlay;
import com.google.videoeditor.util.ImageUtils;

/**
 * This activity enables the user to enter the title and subtitle for an
 * overlay.
 */
public class OverlayTitleActivity extends Activity {
    // Parameter names
    public static final String PARAM_OVERLAY_ATTRIBUTES = "attributes";
    public static final String PARAM_OVERLAY_ID = "overlay_id";
    public static final String PARAM_MEDIA_ITEM_ID = "media_item_id";

    // Instance variables
    private int mOverlayType;
    private ImageView mOverlayImageView;
    private TextView mTitleView, mSubtitleView;
    private Bitmap mOverlayBitmap;
    private int mPreviewWidth, mPreviewHeight;

    private final TextWatcher mTextWatcher = new TextWatcher() {
        /*
         * {@inheritDoc}
         */
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        /*
         * {@inheritDoc}
         */
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        /*
         * {@inheritDoc}
         */
        public void afterTextChanged(Editable s) {
            mOverlayBitmap = ImageUtils.buildOverlayBitmap(mOverlayBitmap, mOverlayType,
                    mTitleView.getText().toString(), mSubtitleView.getText().toString(),
                    mPreviewWidth, mPreviewHeight);
            mOverlayImageView.setImageBitmap(mOverlayBitmap);

            invalidateOptionsMenu();
        }
    };

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overlay_title);
        setFinishOnTouchOutside(true);

        final Bundle attributes = getIntent().getBundleExtra(PARAM_OVERLAY_ATTRIBUTES);
        mOverlayType = MovieOverlay.getType(attributes);
        mOverlayImageView = (ImageView)findViewById(R.id.overlay_layer);

        // Determine the bitmap dimensions
        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.generic_image, dbo);
        mPreviewWidth = dbo.outWidth;
        mPreviewHeight = dbo.outHeight;

        mTitleView = (TextView)findViewById(R.id.overlay_title);
        mTitleView.addTextChangedListener(mTextWatcher);

        mSubtitleView = (TextView)findViewById(R.id.overlay_subtitle);
        mSubtitleView.addTextChangedListener(mTextWatcher);

        if (savedInstanceState == null) {
            mTitleView.setText(MovieOverlay.getTitle(attributes));
            mSubtitleView.setText(MovieOverlay.getSubtitle(attributes));
        }

        mOverlayBitmap = ImageUtils.buildOverlayBitmap(mOverlayBitmap, mOverlayType,
                mTitleView.getText().toString(), mSubtitleView.getText().toString(),
                mPreviewWidth, mPreviewHeight);
        mOverlayImageView.setImageBitmap(mOverlayBitmap);
    }

    /*
     * {@inheritDoc}
     */
    public void onClickHandler(View target) {
        switch (target.getId()) {
            case R.id.overlay_ok: {
                final Intent extras = new Intent();
                extras.putExtra(PARAM_MEDIA_ITEM_ID,
                        getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));

                final String overlayId = getIntent().getStringExtra(PARAM_OVERLAY_ID);
                if (overlayId != null) {
                    extras.putExtra(PARAM_OVERLAY_ID, overlayId);
                }

                final TextView titleView = (TextView)findViewById(R.id.overlay_title);
                final TextView subTitleView = (TextView)findViewById(R.id.overlay_subtitle);
                final Bundle attributes = MovieOverlay.buildUserAttributes(mOverlayType,
                        titleView.getText().toString(), subTitleView.getText().toString());

                extras.putExtra(PARAM_OVERLAY_ATTRIBUTES, attributes);
                setResult(RESULT_OK, extras);
                finish();
                break;
            }

            case R.id.overlay_cancel: {
                finish();
                break;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onSearchRequested() {
        return false;
    }
}
