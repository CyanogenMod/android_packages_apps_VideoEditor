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

import com.google.videoeditor.service.MovieOverlay;

/**
 * The overlays activity
 */
public class OverlaysActivity extends ListActivity {
    // Input overlay category
    public static final String PARAM_MEDIA_ITEM_ID = "media_item_id";

    // Request code ids
    private static final int REQUEST_CODE_SET_TITLE = 1;

    // Instance variables
    private OverlaysAdapter mAdapter;

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_view);
        setFinishOnTouchOutside(true);

        // Create the list adapter
        mAdapter = new OverlaysAdapter(this, getListView());
        setListAdapter(mAdapter);
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
        final Intent intent = new Intent(this, OverlayTitleActivity.class);
        intent.putExtra(OverlayTitleActivity.PARAM_OVERLAY_ATTRIBUTES,
                MovieOverlay.buildUserAttributes(
                        ((OverlayType)mAdapter.getItem(position)).getType(), null, null));
        intent.putExtra(OverlayTitleActivity.PARAM_MEDIA_ITEM_ID,
                getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));
        startActivityForResult(intent, REQUEST_CODE_SET_TITLE);
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

        switch (requestCode) {
            case REQUEST_CODE_SET_TITLE: {
                // Release the adapter now
                mAdapter.onDestroy();
                mAdapter = null;

                setResult(RESULT_OK, extras);
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
