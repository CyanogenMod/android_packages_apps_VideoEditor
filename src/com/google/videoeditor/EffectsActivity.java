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

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

/**
 * The effects activity
 */
public class EffectsActivity extends ListActivity {
    // Input effect category
    public static final String PARAM_CATEGORY = "category";
    public static final String PARAM_MEDIA_ITEM_ID = "media_item_id";
    // Output effect type
    public static final String PARAM_EFFECT_TYPE = "effect";
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_WIDTH = "width";
    public static final String PARAM_HEIGHT = "height";
    public static final String PARAM_START_RECT = "start_rect";
    public static final String PARAM_END_RECT = "end_rect";

    // Request codes
    public static final int REQUEST_CODE_KEN_BURNS = 11;

    // Instance variables
    private EffectsAdapter mAdapter;

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_view);
        setFinishOnTouchOutside(true);

        // Create the list adapter
        mAdapter = new EffectsAdapter(this, getListView(), getIntent().getIntExtra(PARAM_CATEGORY,
                EffectType.CATEGORY_VIDEO));
        setListAdapter(mAdapter);

        final int effectType = getIntent().getIntExtra(PARAM_EFFECT_TYPE, -1);
        if (effectType >= 0) {
            // Select the current effect
            final EffectType[] effects = mAdapter.getEffects();
            for (int i = 0; i < effects.length; i++) {
                if (effects[i].getType() == effectType) {
                    setSelection(i);
                    break;
                }
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

        mAdapter.onResume();
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter != null) {
            mAdapter.onPause();
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAdapter != null) {
            mAdapter.onDestroy();
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final int effectType = ((EffectType)mAdapter.getItem(position)).getType();
        if (effectType == EffectType.EFFECT_KEN_BURNS) {
            final Intent intent = new Intent(this, KenBurnsActivity.class);
            intent.putExtra(KenBurnsActivity.PARAM_MEDIA_ITEM_ID, getIntent().getStringExtra(
                    PARAM_MEDIA_ITEM_ID));
            intent.putExtra(KenBurnsActivity.PARAM_FILENAME, getIntent().getStringExtra(
                    PARAM_FILENAME));
            intent.putExtra(KenBurnsActivity.PARAM_WIDTH, getIntent().getIntExtra(PARAM_WIDTH, 0));
            intent.putExtra(KenBurnsActivity.PARAM_HEIGHT, getIntent().getIntExtra(
                    PARAM_HEIGHT, 0));
            intent.putExtra(KenBurnsActivity.PARAM_START_RECT, getIntent().getParcelableExtra(
                    PARAM_START_RECT));
            intent.putExtra(KenBurnsActivity.PARAM_END_RECT, getIntent().getParcelableExtra(
                    PARAM_END_RECT));

            startActivityForResult(intent, REQUEST_CODE_KEN_BURNS);
        } else {
            final Intent extras = new Intent();
            extras.putExtra(PARAM_EFFECT_TYPE, ((EffectType)mAdapter.getItem(position)).getType());
            extras.putExtra(PARAM_MEDIA_ITEM_ID, getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));

            // Release the adapter now
            mAdapter.onDestroy();
            mAdapter = null;

            setResult(RESULT_OK, extras);
            finish();
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent extras) {
        super.onActivityResult(requestCode, resultCode, extras);
        if (resultCode == RESULT_CANCELED) {
            return;
        }

        switch( requestCode) {
            case REQUEST_CODE_KEN_BURNS: {
                final Intent intent = new Intent();
                intent.putExtra(PARAM_EFFECT_TYPE, EffectType.EFFECT_KEN_BURNS);
                intent.putExtra(PARAM_MEDIA_ITEM_ID,
                        getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));
                intent.putExtra(PARAM_EFFECT_TYPE, EffectType.EFFECT_KEN_BURNS);
                intent.putExtra(PARAM_START_RECT, extras.getParcelableExtra(
                        KenBurnsActivity.PARAM_START_RECT));
                intent.putExtra(PARAM_END_RECT, extras.getParcelableExtra(
                        KenBurnsActivity.PARAM_END_RECT));

                setResult(RESULT_OK, intent);
                finish();
                break;
            }

            default: {
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
