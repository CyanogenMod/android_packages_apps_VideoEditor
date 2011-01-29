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

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.videoeditor.EffectColor;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.Transition;
import android.media.videoeditor.TransitionSliding;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.videoeditor.AlertDialogs;
import com.google.videoeditor.EffectType;
import com.google.videoeditor.EffectsActivity;
import com.google.videoeditor.KenBurnsActivity;
import com.google.videoeditor.OverlaysActivity;
import com.google.videoeditor.R;
import com.google.videoeditor.TransitionType;
import com.google.videoeditor.TransitionsActivity;
import com.google.videoeditor.VideoEditorActivity;
import com.google.videoeditor.service.ApiService;
import com.google.videoeditor.service.MovieEffect;
import com.google.videoeditor.service.MovieMediaItem;
import com.google.videoeditor.service.MovieTransition;
import com.google.videoeditor.service.VideoEditorProject;
import com.google.videoeditor.util.FileUtils;
import com.google.videoeditor.util.MediaItemUtils;

/**
 * The LinearLayout which holds media items and transitions
 */
public class MediaLinearLayout extends LinearLayout {
    // Logging
    private static final String TAG = "MediaLinearLayout";

    // Dialog parameter ids
    private static final String PARAM_DIALOG_MEDIA_ITEM_ID = "media_item_id";
    private static final String PARAM_DIALOG_CURRENT_RENDERING_MODE = "rendering_mode";
    private static final String PARAM_DIALOG_TRANSITION_ID = "transition_id";

    // Transition duration limits
    private static final long MAXIMUM_IMAGE_DURATION = 6000;
    private static final long MAXIMUM_TRANSITION_DURATION = 3000;
    private static final long MINIMUM_TRANSITION_DURATION = 500;

    private static final long TIME_TOLERANCE = 30;

    // Instance variables
    private final ItemSimpleGestureListener mMediaItemGestureListener;
    private final ItemSimpleGestureListener mTransitionGestureListener;
    private final Handler mHandler;
    private final int mHalfParentWidth;
    private final int mHandleWidth;
    private MediaLayoutListener mListener;
    private ActionMode mMediaItemActionMode;
    private ActionMode mTransitionActionMode;
    private VideoEditorProject mProject;
    private boolean mPlaybackInProgress;
    private HandleView mLeftHandle, mRightHandle;
    private boolean mMoveLayoutPending;
    private View mSelectedView;
    private String mDragMediaItemId;
    private float mPrevDragPosition;
    private long mPrevDragScrollTime;
    private MovieMediaItem mDropAfterMediaItem;
    private int mDropIndex;

    /**
     * Activity listener
     */
    public interface MediaLayoutListener {
        /**
         * Request scrolling by an offset amount
         *
         * @param scrollBy The amount to scroll
         * @param smooth true to scroll smoothly
         */
        public void onRequestScrollBy(int scrollBy, boolean smooth);

        /**
         * Request scrolling to a specified time position
         *
         * @param scrollToTime The scroll position
         * @param smooth true to scroll smoothly
         */
        public void onRequestMovePlayhead(long scrollToTime, boolean smooth);

        /**
         * Add a new media item
         *
         * @param afterMediaItemId Add media item after this media item id
         */
        public void onAddMediaItem(String afterMediaItemId);

        /**
         * A media item enters trimming mode
         *
         * @param mediaItem The media item
         */
        public void onTrimMediaItemBegin(MovieMediaItem mediaItem);

        /**
         * A media item is being trimmed
         *
         * @param mediaItem The media item
         * @param timeMs The time where the trim occurs
         */
        public void onTrimMediaItem(MovieMediaItem mediaItem, long timeMs);

        /**
         * A media has been trimmed
         *
         * @param mediaItem The media item
         * @param timeMs The time where the trim occurs
         */
        public void onTrimMediaItemEnd(MovieMediaItem mediaItem, long timeMs);
    };

    /**
     * The media item action mode handler
     */
    private class MediaItemActionModeCallback implements ActionMode.Callback {
        // Instance variables
        private final MovieMediaItem mMediaItem;

        /**
         * Constructor
         *
         * @param mediaItem The media item
         */
        public MediaItemActionModeCallback(MovieMediaItem mediaItem) {
            mMediaItem = mediaItem;
        }

        /*
         * {@inheritDoc}
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mMediaItemActionMode = mode;

            final Activity activity = (Activity)getContext();
            activity.getMenuInflater().inflate(R.menu.media_item_mode_menu, menu);
            mode.setTitle(FileUtils.getSimpleName(mMediaItem.getFilename()));

            return true;
        }

        /*
         * {@inheritDoc}
         */
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            menu.findItem(R.id.action_add_effect).setVisible(mMediaItem.getEffect() == null &&
                    !mPlaybackInProgress);
            menu.findItem(R.id.action_change_effect).setVisible(mMediaItem.getEffect() != null &&
                    !mPlaybackInProgress);
            menu.findItem(R.id.action_remove_effect).setVisible(mMediaItem.getEffect() != null &&
                    !mPlaybackInProgress);
            menu.findItem(R.id.action_add_overlay).setVisible(mMediaItem.getOverlay() == null &&
                    !mPlaybackInProgress);
            menu.findItem(R.id.action_add_begin_transition).setVisible(
                    mMediaItem.getBeginTransition() == null && !mPlaybackInProgress);
            menu.findItem(R.id.action_add_end_transition).setVisible(
                    mMediaItem.getEndTransition() == null && !mPlaybackInProgress);
            menu.findItem(R.id.action_rendering_mode).setVisible(mProject.hasMultipleAspectRatios()
                    && !mPlaybackInProgress);
            if (mMediaItem.isVideoClip()) {
                menu.findItem(R.id.action_mute_media_item).setVisible(!mMediaItem.isAppMuted());
                menu.findItem(R.id.action_unmute_media_item).setVisible(mMediaItem.isAppMuted());
            } else {
                menu.findItem(R.id.action_mute_media_item).setVisible(false);
                menu.findItem(R.id.action_unmute_media_item).setVisible(false);
            }

            return true;
        }

        /*
         * {@inheritDoc}
         */
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add_effect: {
                    if (mMediaItem.isImage()) {
                        pickEffect(EffectType.CATEGORY_IMAGE, mMediaItem.getId());
                    } else {
                        pickEffect(EffectType.CATEGORY_VIDEO, mMediaItem.getId());
                    }
                    break;
                }

                case R.id.action_change_effect: {
                    if (mMediaItem.isImage()) {
                        editEffect(EffectType.CATEGORY_IMAGE, mMediaItem.getId(),
                                mMediaItem.getEffect().getType());
                    } else {
                        editEffect(EffectType.CATEGORY_VIDEO, mMediaItem.getId(),
                                mMediaItem.getEffect().getType());
                    }
                    break;
                }

                case R.id.action_remove_effect: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                    ((Activity)getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID, bundle);
                    break;
                }

                case R.id.action_add_overlay: {
                    pickOverlay(mMediaItem.getId());
                    break;
                }

                case R.id.action_add_begin_transition: {
                    final MovieMediaItem prevMediaItem = mProject.getPreviousMediaItem(
                            mMediaItem.getId());
                    pickTransition(prevMediaItem);
                    break;
                }

                case R.id.action_add_end_transition: {
                    pickTransition(mMediaItem);
                    break;
                }

                case R.id.action_rendering_mode: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                    bundle.putInt(PARAM_DIALOG_CURRENT_RENDERING_MODE,
                            mMediaItem.getAppRenderingMode());
                    ((Activity)getContext()).showDialog(
                            VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID, bundle);
                    break;
                }

                case R.id.action_mute_media_item: {
                    mMediaItem.setAppMute(true);
                    mode.invalidate();
                    ApiService.setMediaItemMute(getContext(), mProject.getPath(),
                            mMediaItem.getId(), true);
                    break;
                }

                case R.id.action_unmute_media_item: {
                    mMediaItem.setAppMute(false);
                    mode.invalidate();
                    ApiService.setMediaItemMute(getContext(), mProject.getPath(),
                            mMediaItem.getId(), false);
                    break;
                }

                case R.id.action_delete_media_item: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_MEDIA_ITEM_ID, mMediaItem.getId());
                    ((Activity)getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID, bundle);
                    break;
                }

                default: {
                    break;
                }
            }

            return true;
        }

        /*
         * {@inheritDoc}
         */
        public void onDestroyActionMode(ActionMode mode) {
            final View mediaItemView = getMediaItemView(mMediaItem.getId());
            if (mSelectedView != null) {
                mLeftHandle.endMove();
                mRightHandle.endMove();
            }

            if (mediaItemView != null) {
                selectView(mediaItemView, false);
            }

            mMediaItemActionMode = null;
        }
    }

    /**
     * The transition action mode handler
     */
    private class TransitionActionModeCallback implements ActionMode.Callback {
        // Instance variables
        private final MovieTransition mTransition;

        /**
         * Constructor
         *
         * @param transition The transition
         */
        public TransitionActionModeCallback(MovieTransition transition) {
            mTransition = transition;
        }

        /*
         * {@inheritDoc}
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mTransitionActionMode = mode;

            final Activity activity = (Activity)getContext();
            activity.getMenuInflater().inflate(R.menu.transition_mode_menu, menu);

            return true;
        }

        /*
         * {@inheritDoc}
         */
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        /*
         * {@inheritDoc}
         */
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_remove_transition: {
                    final Bundle bundle = new Bundle();
                    bundle.putString(PARAM_DIALOG_TRANSITION_ID, mTransition.getId());
                    ((Activity)getContext()).showDialog(
                            VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID, bundle);
                    break;
                }

                case R.id.action_change_transition: {
                    editTransition(mTransition);
                    break;
                }

                default: {
                    break;
                }
            }

            return true;
        }

        /*
         * {@inheritDoc}
         */
        public void onDestroyActionMode(ActionMode mode) {
            final View transitionView = getTransitionView(mTransition.getId());
            if (transitionView != null) {
                selectView(transitionView, false);
            }

            mTransitionActionMode = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    public MediaLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mMediaItemGestureListener = new ItemSimpleGestureListener() {
            /*
             * {@inheritDoc}
             */
            public boolean onSingleTapConfirmed(View view, int area, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }

                switch (area) {
                    case ItemSimpleGestureListener.LEFT_AREA: {
                        if (view.isSelected()) {
                            final MovieMediaItem mediaItem = (MovieMediaItem)view.getTag();
                            final MovieMediaItem prevMediaItem = mProject.getPreviousMediaItem(
                                    mediaItem.getId());
                            pickTransition(prevMediaItem);
                        }
                        break;
                    }

                    case ItemSimpleGestureListener.CENTER_AREA: {
                        break;
                    }

                    case ItemSimpleGestureListener.RIGHT_AREA: {
                        if (view.isSelected()) {
                            pickTransition((MovieMediaItem)view.getTag());
                        }
                        break;
                    }
                }

                if (!view.isSelected()) {
                    selectView(view, true);
                }

                return true;
            }

            /*
             * {@inheritDoc}
             */
            public void onLongPress(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return;
                }

                final MovieMediaItem mediaItem = (MovieMediaItem)view.getTag();
                if (mProject.getMediaItemCount() > 1) {
                    if (view.isSelected()) {
                        view.startDrag(ClipData.newPlainText("File", mediaItem.getFilename()),
                                ((MediaItemView)view).getShadowBuilder(), mediaItem.getId(), 0);
                    }
                }

                if (!view.isSelected()) {
                    selectView(view, true);
                }

                if (mMediaItemActionMode == null) {
                    startActionMode(new MediaItemActionModeCallback(mediaItem));
                }
            }
        };

        mTransitionGestureListener = new ItemSimpleGestureListener() {
            /*
             * {@inheritDoc}
             */
            public boolean onSingleTapConfirmed(View view, int area, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return false;
                }

                if (!view.isSelected()) {
                    selectView(view, true);
                }

                return true;
            }

            /*
             * {@inheritDoc}
             */
            public void onLongPress(View view, MotionEvent e) {
                if (mPlaybackInProgress) {
                    return;
                }

                if (!view.isSelected()) {
                    selectView(view, true);
                }

                if (mTransitionActionMode == null) {
                    startActionMode(new TransitionActionModeCallback(
                            (MovieTransition)view.getTag()));
                }
            }
        };

        // Add the beginning timeline item
        final View beginView = inflate(getContext(), R.layout.empty_left_timeline_item, null);
        beginView.setOnClickListener(new View.OnClickListener() {
            /*
             * {@inheritDoc}
             */
            public void onClick(View view) {
                unselectAllViews();
            }
        });

        final ImageButton leftAddClipButton = (ImageButton)beginView.findViewById(
                R.id.add_left_media_item_button);
        leftAddClipButton.setVisibility(View.GONE);
        leftAddClipButton.setOnClickListener(new View.OnClickListener() {
            /*
             * {@inheritDoc}
             */
            public void onClick(View view) {
                if (mProject != null && mProject.getMediaItemCount() > 0) {
                    unselectAllViews();
                    // Add a clip at the beginning of the movie
                    mListener.onAddMediaItem(null);
                }
            }
        });

        addView(beginView);

        // Add the end timeline item
        final View endView = inflate(getContext(), R.layout.empty_right_timeline_item, null);
        endView.setOnClickListener(new View.OnClickListener() {
            /*
             * {@inheritDoc}
             */
            public void onClick(View view) {
                unselectAllViews();
            }
        });

        final ImageButton rightAddClipButton = (ImageButton)endView.findViewById(
                R.id.add_right_media_item_button);
        rightAddClipButton.setOnClickListener(new View.OnClickListener() {
            /*
             * {@inheritDoc}
             */
            public void onClick(View view) {
                if (mProject != null) {
                    unselectAllViews();
                    // Add a clip at the end of the movie
                    final MovieMediaItem lastMediaItem = mProject.getLastMediaItem();
                    if (lastMediaItem != null) {
                        mListener.onAddMediaItem(lastMediaItem.getId());
                    } else {
                        mListener.onAddMediaItem(null);
                    }
                }
            }
        });
        addView(endView);

        mLeftHandle = (HandleView)inflate(getContext(), R.layout.left_handle_view, null);
        addView(mLeftHandle);

        mRightHandle = (HandleView)inflate(getContext(), R.layout.right_handle_view, null);
        addView(mRightHandle);

        mHandleWidth = (int)context.getResources().getDimension(R.dimen.handle_width);
        // Compute half the width of the screen (and therefore the parent view)
        final Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        mHalfParentWidth = display.getWidth() / 2;

        mHandler = new Handler();

        setMotionEventSplittingEnabled(false);
    }

    /*
     * {@inheritDoc}
     */
    public MediaLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     * {@inheritDoc}
     */
    public MediaLinearLayout(Context context) {
        this(context, null, 0);
    }

    /**
     * @param listener The listener
     */
    public void setListener(MediaLayoutListener listener) {
        mListener = listener;
    }

    /**
     * @param project The project
     */
    public void setProject(VideoEditorProject project) {
        // Close the contextual action bar
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.finish();
            mMediaItemActionMode = null;
        }

        if (mTransitionActionMode != null) {
            mTransitionActionMode.finish();
            mTransitionActionMode = null;
        }

        mLeftHandle.setVisibility(View.GONE);
        mLeftHandle.setListener(null);
        mRightHandle.setVisibility(View.GONE);
        mRightHandle.setListener(null);

        removeViews();

        mProject = project;
    }

    /**
     * @param inProgress true if playback is in progress
     */
    public void setPlaybackInProgress(boolean inProgress) {
        mPlaybackInProgress = inProgress;

        setPlaybackState(inProgress);
        // Don't allow the user to interact with media items or
        // transitions while is in progress
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.finish();
            mMediaItemActionMode = null;
        }

        if (mTransitionActionMode != null) {
            mTransitionActionMode.finish();
            mTransitionActionMode = null;
        }
    }

    /**
     * Add all the media items
     *
     * @param mediaItems The list of media items
     */
    public void addMediaItems(List<MovieMediaItem> mediaItems) {
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.finish();
            mMediaItemActionMode = null;
        }

        if (mTransitionActionMode != null) {
            mTransitionActionMode.finish();
            mTransitionActionMode = null;
        }

        removeViews();

        for (MovieMediaItem mediaItem : mediaItems) {
            addMediaItem(mediaItem);
        }
    }

    /**
     * Add a new media item at the end of the timeline
     *
     * @param mediaItem The media item
     */
    private void addMediaItem(MovieMediaItem mediaItem) {
        final View mediaItemView = inflate(getContext(), R.layout.media_item, null);
        ((MediaItemView)mediaItemView).setGestureListener(mMediaItemGestureListener);
        ((MediaItemView)mediaItemView).setProjectPath(mProject.getPath());

        mediaItemView.setTag(mediaItem);

        // Add the new view
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        // Add the view before the end, handle left and handle right views
        addView(mediaItemView, getChildCount() - 3, lp);

        // If the new media item has beginning and end transitions add them
        final MovieTransition beginTransition = mediaItem.getBeginTransition();
        if (beginTransition != null) {
            final int cc = getChildCount();
            // Account for the beginning and end views and the trim handles
            if (cc > 5) { // There is a previous view (transition or media item)
                final View view = getChildAt(cc - 5);
                final Object tag = view.getTag();
                // Do not add transition if it already exists
                if (tag != null && tag instanceof MovieMediaItem) {
                    final MovieMediaItem prevMediaItem = (MovieMediaItem)tag;
                    addTransition(beginTransition, prevMediaItem.getId());
                }
            } else { // This is the first media item
                addTransition(beginTransition, null);
            }
        }

        final MovieTransition endTransition = mediaItem.getEndTransition();
        if (endTransition != null) {
            addTransition(endTransition, mediaItem.getId());
        }

        // Adjust the size of all the views
        requestLayout();

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }

        // Now we can add clips by tapping the beginning view
        final View beginView = getChildAt(0);
        beginView.findViewById(R.id.add_left_media_item_button).setVisibility(View.VISIBLE);
    }

    /**
     * Insert a new media item after the specified media item id
     *
     * @param mediaItem The media item
     * @param afterMediaItemId The id of the media item preceding the media item
     */
    public void insertMediaItem(MovieMediaItem mediaItem, String afterMediaItemId) {
        final View mediaItemView = inflate(getContext(), R.layout.media_item, null);
        ((MediaItemView)mediaItemView).setGestureListener(mMediaItemGestureListener);
        ((MediaItemView)mediaItemView).setProjectPath(mProject.getPath());

        mediaItemView.setTag(mediaItem);

        int insertViewIndex;
        if (afterMediaItemId != null) {
            if ((insertViewIndex = getMediaItemViewIndex(afterMediaItemId)) == -1) {
                Log.e(TAG, "Media item not found: " + afterMediaItemId);
                return;
            }

            insertViewIndex++;

            if (insertViewIndex < getChildCount()) {
                final Object tag = getChildAt(insertViewIndex).getTag();
                if (tag != null && tag instanceof MovieTransition) {
                    // Remove the transition following the media item
                    removeViewAt(insertViewIndex);
                }
            }
        } else { // Insert at the beginning
            // If we have a transition at the beginning remove it
            final Object tag = getChildAt(1).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                removeViewAt(1);
            }

            insertViewIndex = 1;
        }

        // Add the new view
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(mediaItemView, insertViewIndex, lp);

        // If the new media item has beginning and end transitions add them
        final MovieTransition beginTransition = mediaItem.getBeginTransition();
        if (beginTransition != null) {
            if (insertViewIndex > 1) { // There is a previous view (transition or media item)
                final View view = getChildAt(insertViewIndex - 1);
                final Object tag = view.getTag();
                // Do not add transition if it already exists
                if (tag != null && tag instanceof MovieMediaItem) {
                    final MovieMediaItem prevMediaItem = (MovieMediaItem)tag;
                    addTransition(beginTransition, prevMediaItem.getId());
                }
            } else { // This is the first media item
                addTransition(beginTransition, null);
            }
        }

        final MovieTransition endTransition = mediaItem.getEndTransition();
        if (endTransition != null) {
            addTransition(endTransition, mediaItem.getId());
        }

        // Adjust the size of all the views
        requestLayout();

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }

        // Now we can add clips by tapping the beginning view
        final View beginView = getChildAt(0);
        beginView.findViewById(R.id.add_left_media_item_button).setVisibility(View.VISIBLE);
    }

    /**
     * Update media item
     *
     * @param mediaItem The media item
     */
    public void updateMediaItem(MovieMediaItem mediaItem) {
        final String mediaItemId = mediaItem.getId();
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mi = (MovieMediaItem)tag;
                if (mediaItemId.equals(mi.getId())) {
                    if (mediaItem != mi) {
                        // The media item is a new instance of the media item
                        childView.setTag(mediaItem);
                        if (mediaItem.getBeginTransition() != null) {
                            if (i > 0) {
                                final View tView = getChildAt(i - 1);
                                final Object tagT = tView.getTag();
                                if (tagT != null && tagT instanceof MovieTransition) {
                                    tView.setTag(mediaItem.getBeginTransition());
                                }
                            }
                        }

                        if (mediaItem.getEndTransition() != null) {
                            if (i < childrenCount - 1) {
                                final View tView = getChildAt(i + 1);
                                final Object tagT = tView.getTag();
                                if (tagT != null && tagT instanceof MovieTransition) {
                                    tView.setTag(mediaItem.getEndTransition());
                                }
                            }
                        }
                    }

                    if (childView.isSelected()) {
                        mLeftHandle.setEnabled(true);
                        mRightHandle.setEnabled(true);
                    }

                    break;
                }
            }
        }

        requestLayout();

        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }
    }

    /**
     * Remove a media item view
     *
     * @param mediaItemId The media item id
     * @param transition The transition inserted at the removal position
     *          if a theme is in use.
     *
     * @return The view which was removed
     */
    public View removeMediaItem(String mediaItemId, MovieTransition transition) {
        final int childrenCount = getChildCount();
        MovieMediaItem prevMediaItem = null;
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mi = (MovieMediaItem)tag;
                if (mediaItemId.equals(mi.getId())) {
                    int mediaItemViewIndex = i;

                    // Remove the before transition
                    if (mediaItemViewIndex > 0) {
                        final Object beforeTag = getChildAt(mediaItemViewIndex - 1).getTag();
                        if (beforeTag != null && beforeTag instanceof MovieTransition) {
                            // Remove the transition view
                            removeViewAt(mediaItemViewIndex - 1);
                            mediaItemViewIndex--;
                        }
                    }

                    // Remove the after transition view
                    if (mediaItemViewIndex < getChildCount() - 1) {
                        final Object afterTag = getChildAt(mediaItemViewIndex + 1).getTag();
                        if (afterTag != null && afterTag instanceof MovieTransition) {
                            // Remove the transition view
                            removeViewAt(mediaItemViewIndex + 1);
                        }
                    }

                    // Remove the media item view
                    removeViewAt(mediaItemViewIndex);

                    if (transition != null) {
                        addTransition(transition,
                                prevMediaItem != null ? prevMediaItem.getId() : null);
                    }

                    if (mMediaItemActionMode != null) {
                        mMediaItemActionMode.invalidate();
                    }

                    if (mProject.getMediaItemCount() == 0) {
                        // We cannot add clips by tapping the beginning view
                        final View beginView = getChildAt(0);
                        beginView.findViewById(R.id.add_left_media_item_button).setVisibility(
                                View.GONE);
                    }
                    return childView;
                }

                prevMediaItem = mi;
            }
        }

        return null;
    }

    /**
     * Create a new transition
     *
     * @param afterMediaItemId Insert the transition after this media item id
     * @param transitionType The transition type
     * @param transitionDurationMs The transition duration in ms
     */
    public void addTransition(String afterMediaItemId, int transitionType,
            long transitionDurationMs) {
        // Unselect the media item view
        unselectAllViews();

        final MovieMediaItem afterMediaItem;
        if (afterMediaItemId != null) {
            afterMediaItem = mProject.getMediaItem(afterMediaItemId);
            if (afterMediaItem == null) {
                return;
            }
        } else {
            afterMediaItem = null;
        }

        final String id = ApiService.generateId();
        switch (transitionType) {
            case TransitionType.TRANSITION_TYPE_ALPHA_CONTOUR: {
                ApiService.insertAlphaTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        R.raw.mask_contour, 50, false);
                break;
            }

            case TransitionType.TRANSITION_TYPE_ALPHA_DIAGONAL: {
                ApiService.insertAlphaTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        R.raw.mask_diagonal, 50, false);
                break;
            }

            case TransitionType.TRANSITION_TYPE_CROSSFADE: {
                ApiService.insertCrossfadeTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR);
                break;
            }

            case TransitionType.TRANSITION_TYPE_FADE_BLACK: {
                ApiService.insertFadeBlackTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_RIGHT_OUT_LEFT_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_RIGHT_OUT_LEFT_IN);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_LEFT_OUT_RIGHT_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_LEFT_OUT_RIGHT_IN);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_TOP_OUT_BOTTOM_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_TOP_OUT_BOTTOM_IN);
                break;
            }

            case TransitionType.TRANSITION_TYPE_SLIDING_BOTTOM_OUT_TOP_IN: {
                ApiService.insertSlidingTransition(getContext(), mProject.getPath(),
                        afterMediaItemId, id, transitionDurationMs, Transition.BEHAVIOR_LINEAR,
                        TransitionSliding.DIRECTION_BOTTOM_OUT_TOP_IN);
                break;
            }

            default: {
                break;
            }
        }
    }

    /**
     * Edit a transition
     *
     * @param afterMediaItemId Insert the transition after this media item id
     * @param transitionId The transition id
     * @param transitionType The transition type
     * @param transitionDurationMs The transition duration in ms
     */
    public void editTransition(String afterMediaItemId, String transitionId, int transitionType,
            long transitionDurationMs) {
        final MovieTransition transition = mProject.getTransition(transitionId);
        if (transition == null) {
            return;
        }

        // Check if the type or duration had changed
        if (transition.getType() != transitionType) {
            // Remove the transition and add it again
            ApiService.removeTransition(getContext(), mProject.getPath(), transitionId);
            addTransition(afterMediaItemId, transitionType, transitionDurationMs);
        } else if (transition.getAppDuration() != transitionDurationMs) {
            transition.setAppDuration(transitionDurationMs);
            ApiService.setTransitionDuration(getContext(), mProject.getPath(), transitionId,
                    transitionDurationMs);
        }
    }

    /**
     * Add a new transition after the specified media id.
     *
     * This method assumes that a transition does not exist at the insertion
     *      point.
     *
     * @param transition The transition
     * @param afterMediaItemId After the specified media item id
     *
     * @return The view that was added
     */
    public View addTransition(MovieTransition transition, String afterMediaItemId) {
        // Determine the insert position
        int index;
        if (afterMediaItemId != null) {
            index = -1;
            final int childrenCount = getChildCount();
            for (int i = 0; i < childrenCount; i++) {
                final Object tag = getChildAt(i).getTag();
                if (tag != null && tag instanceof MovieMediaItem) {
                    final MovieMediaItem mi = (MovieMediaItem)tag;
                    if (afterMediaItemId.equals(mi.getId())) {
                        index = i + 1;
                        break;
                    }
                }
            }

            if (index < 0) {
                Log.e(TAG, "addTransition media item not found: " + afterMediaItemId);
                return null;
            }
        } else {
            index = 1;
        }

        final View transitionView = inflate(getContext(), R.layout.transition_view, null);
        ((TransitionView)transitionView).setGestureListener(mTransitionGestureListener);
        ((TransitionView)transitionView).setProjectPath(mProject.getPath());

        transitionView.setTag(transition);

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.FILL_PARENT);
        addView(transitionView, index, lp);

        // Adjust the size of all the views
        requestLayout();

        // If this transition was added by the user invalidate the menu item
        if (mMediaItemActionMode != null) {
            mMediaItemActionMode.invalidate();
        }

        return transitionView;
    }

    /**
     * Update a  transition
     *
     * @param transitionId The transition id
     */
    public void updateTransition(String transitionId) {
        requestLayout();
        invalidate();
    }

    /**
     * Remove a transition
     *
     * @param transitionId The transition id
     */
    public void removeTransition(String transitionId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    // Remove the view
                    removeViewAt(i);

                    // Adjust the size of all the views
                    requestLayout();

                    // If this transition was removed by the user invalidate the menu item
                    if (mMediaItemActionMode != null) {
                        mMediaItemActionMode.invalidate();
                    }

                    return;
                }
            }
        }
    }

    /**
     * A Ken Burns movie is encoded for an MediaImageItem
     *
     * @param mediaItemId The media item id
     * @param action The action
     * @param progress Progress value (between 0..100)
     */
    public void onGeneratePreviewMediaItemProgress(String mediaItemId, int action, int progress) {
        // Display the progress while generating the Ken Burns video clip
        final MediaItemView view = (MediaItemView)getMediaItemView(mediaItemId);
        if (view != null) {
            view.setProgress(progress);

            if (view.isSelected()) {
                if (progress == 0) {
                    mLeftHandle.setEnabled(false);
                    mRightHandle.setEnabled(false);
                } else if (progress == 100) {
                    mLeftHandle.setEnabled(true);
                    mRightHandle.setEnabled(true);
                }
            }
        }
    }

    /**
     * A transition is being encoded
     *
     * @param transitionId The transition id
     * @param action The action
     * @param progress The progress
     */
    public void onGeneratePreviewTransitionProgress(String transitionId, int action,
            int progress) {
        // Display the progress while generating the transition
        final TransitionView view = (TransitionView)getTransitionView(transitionId);
        if (view != null) {
            view.setProgress(progress);

            if (view.isSelected()) {
                if (progress == 0) {
                    mLeftHandle.setEnabled(false);
                    mRightHandle.setEnabled(false);
                } else if (progress == 100) {
                    mLeftHandle.setEnabled(true);
                    mRightHandle.setEnabled(true);
                }
            }
        }
    }

    /**
     * Create a new effect
     *
     * @param effectType The effect type
     * @param mediaItemId Add the effect for this media item id
     */
    public void addEffect(int effectType, String mediaItemId) {
        final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
        if (mediaItem == null) {
            Log.e(TAG, "addEffect media item not found: " + mediaItemId);
            return;
        }

        final String id = ApiService.generateId();
        switch (effectType) {
            case EffectType.EFFECT_KEN_BURNS: {
                final Activity activity = (Activity)getContext();
                final Intent intent = new Intent(activity, KenBurnsActivity.class);
                intent.putExtra(KenBurnsActivity.PARAM_MEDIA_ITEM_ID, mediaItem.getId());
                intent.putExtra(KenBurnsActivity.PARAM_FILENAME, mediaItem.getFilename());
                intent.putExtra(KenBurnsActivity.PARAM_WIDTH, mediaItem.getWidth());
                intent.putExtra(KenBurnsActivity.PARAM_HEIGHT, mediaItem.getHeight());
                activity.startActivityForResult(intent,
                        VideoEditorActivity.REQUEST_CODE_KEN_BURNS);
                break;
            }

            case EffectType.EFFECT_COLOR_GRADIENT: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_GRADIENT,
                        EffectColor.GRAY);
                break;
            }

            case EffectType.EFFECT_COLOR_SEPIA: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_SEPIA, 0);
                break;
            }

            case EffectType.EFFECT_COLOR_NEGATIVE: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_NEGATIVE, 0);
                break;
            }

            case EffectType.EFFECT_COLOR_FIFTIES: {
                ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                        mediaItem.getDuration(), EffectColor.TYPE_FIFTIES, 0);
                break;
            }

            default: {
                break;
            }
        }
    }

    /**
     * Create a new effect
     *
     * @param effectType The effect type
     * @param mediaItemId Add the effect for this media item id
     */
    public void editEffect(int effectType, String mediaItemId) {
        final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
        if (mediaItem == null) {
            Log.e(TAG, "editEffect media item not found: " + mediaItemId);
            return;
        }

        final MovieEffect effect = mediaItem.getEffect();

        final String id = ApiService.generateId();
        switch (effectType) {
            case EffectType.EFFECT_KEN_BURNS: {
                // Note that we remove the old effect only once the user
                // clicks Done in the Ken Burns activity.
                // See addKenBurnsEffect below.
                final Activity activity = (Activity)getContext();
                final Intent intent = new Intent(activity, KenBurnsActivity.class);
                intent.putExtra(KenBurnsActivity.PARAM_MEDIA_ITEM_ID, mediaItem.getId());
                intent.putExtra(KenBurnsActivity.PARAM_FILENAME, mediaItem.getFilename());
                intent.putExtra(KenBurnsActivity.PARAM_WIDTH, mediaItem.getWidth());
                intent.putExtra(KenBurnsActivity.PARAM_HEIGHT, mediaItem.getHeight());
                if (effect.getType() == EffectType.EFFECT_KEN_BURNS) {
                    intent.putExtra(KenBurnsActivity.PARAM_START_RECT, effect.getStartRect());
                    intent.putExtra(KenBurnsActivity.PARAM_END_RECT, effect.getEndRect());
                }

                activity.startActivityForResult(intent,
                        VideoEditorActivity.REQUEST_CODE_KEN_BURNS);
                break;
            }

            case EffectType.EFFECT_COLOR_GRADIENT: {
                // Check if the type has changed
                if (effect.getType() != effectType) {
                    // Remove the old effect
                    ApiService.removeEffect(getContext(), mProject.getPath(), mediaItemId,
                            effect.getId());

                    ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                            mediaItem.getDuration(), EffectColor.TYPE_GRADIENT,
                            EffectColor.GRAY);
                }
                break;
            }

            case EffectType.EFFECT_COLOR_SEPIA: {
                // Check if the type has changed
                if (effect.getType() != effectType) {
                    // Remove the old effect
                    ApiService.removeEffect(getContext(), mProject.getPath(), mediaItemId,
                            effect.getId());

                    ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                            mediaItem.getDuration(), EffectColor.TYPE_SEPIA, 0);
                }
                break;
            }

            case EffectType.EFFECT_COLOR_NEGATIVE: {
                // Check if the type has changed
                if (effect.getType() != effectType) {
                    // Remove the old effect
                    ApiService.removeEffect(getContext(), mProject.getPath(), mediaItemId,
                            effect.getId());

                    ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                            mediaItem.getDuration(), EffectColor.TYPE_NEGATIVE, 0);
                }
                break;
            }

            case EffectType.EFFECT_COLOR_FIFTIES: {
                // Check if the type has changed
                if (effect.getType() != effectType) {
                    // Remove the old effect
                    ApiService.removeEffect(getContext(), mProject.getPath(), mediaItemId,
                            effect.getId());

                    ApiService.addEffectColor(getContext(), mProject.getPath(), mediaItemId, id, 0,
                            mediaItem.getDuration(), EffectColor.TYPE_FIFTIES, 0);
                }
                break;
            }

            default: {
                break;
            }
        }
    }

    /**
     * Create a Ken Burns effect
     *
     * @param mediaItemId Add the effect for this media item id
     * @param startRect The start rectangle
     * @param endRect The end rectangle
     */
    public void addKenBurnsEffect(String mediaItemId, Rect startRect, Rect endRect) {
        final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
        if (mediaItem == null) {
            Log.e(TAG, "addKenBurnsEffect media item not found: " + mediaItemId);
            return;
        }

        final MovieEffect effect = mediaItem.getEffect();
        if (effect != null) {
            // Remove the old effect in case this method is called
            // because the effect is changed
            ApiService.removeEffect(getContext(), mProject.getPath(), mediaItemId,
                    effect.getId());
        }

        ApiService.addEffectKenBurns(getContext(), mProject.getPath(), mediaItemId,
                ApiService.generateId(), 0, mediaItem.getDuration(), startRect, endRect);
    }

    /**
     * Set the media item thumbnails.
     *
     * @param mediaItemId The media item id
     * @param bitmaps The bitmaps array
     * @param startMs The start time position
     * @param endMs The end time position
     *
     * @return true if the bitmaps were used
     */
    public boolean setMediaItemThumbnails(String mediaItemId, Bitmap[] bitmaps, long startMs,
            long endMs) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mi = (MovieMediaItem)tag;
                if (mediaItemId.equals(mi.getId())) {
                    return ((MediaItemView)getChildAt(i)).setBitmaps(bitmaps, startMs, endMs);
                }
            }
        }

        return false;
    }

    /**
     * Set the transition thumbnails.
     *
     * @param transitionId The transition id
     * @param bitmaps The bitmaps array
     *
     * @return true if the bitmaps were used
     */
    public boolean setTransitionThumbnails(String transitionId, Bitmap[] bitmaps) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    return ((TransitionView)getChildAt(i)).setBitmaps(bitmaps);
                }
            }
        }

        return false;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Compute the total duration
        final long totalDurationMs = mProject.computeDuration();
        final int viewWidth = getWidth() - (2 * mHalfParentWidth);

        long startMs = 0;
        final int childrenCount = getChildCount();
        final int paddingTop = getPaddingTop();
        int left = 0;
        if (mDragMediaItemId != null) {
            for (int i = 0; i < childrenCount; i++) {
                final View view = getChildAt(i);
                final Object tag = view.getTag();
                if (tag != null) {
                    final long durationMs = computeViewDuration(view);

                    final int right = (int)((float)((startMs + durationMs) * viewWidth) /
                            (float)totalDurationMs) + mHalfParentWidth;

                    if (tag instanceof MovieMediaItem) {
                        if (left != view.getLeft() || right != view.getRight()) {
                            final int oldLeft = view.getLeft();
                            final int oldRight = view.getRight();
                            view.layout(left, paddingTop, right, b - t);
                            ((MediaItemView)view).onPositionChanged(oldLeft, oldRight);
                        } else {
                            view.layout(left, paddingTop, right, b - t);
                        }
                    } else {
                        view.layout(left, paddingTop, right, b - t);
                    }

                    startMs += durationMs;
                    left = right;
                } else if (view == mLeftHandle) {
                } else if (view == mRightHandle) {
                } else if (i == 0) { // Begin view
                    view.layout(0, paddingTop, mHalfParentWidth, b - t);
                    left += mHalfParentWidth;
                } else { // End view
                    view.layout(left, paddingTop, getWidth(), b - t);
                }
            }
        } else if (mSelectedView != null) { // Trimming mode
            final int leftViewWidth = (Integer)((View)getParent().getParent()).getTag(
                    R.id.left_view_width);

            for (int i = 0; i < childrenCount; i++) {
                final View view = getChildAt(i);
                final Object tag = view.getTag();
                if (tag != null) {
                    final long durationMs = computeViewDuration(view);

                    final int right = (int)((float)((startMs + durationMs) * viewWidth) /
                            (float)totalDurationMs) + leftViewWidth;

                    if (tag instanceof MovieMediaItem) {
                        if (left != view.getLeft() || right != view.getRight()) {
                            final int oldLeft = view.getLeft();
                            final int oldRight = view.getRight();
                            view.layout(left, paddingTop, right, b - t);
                            ((MediaItemView)view).onPositionChanged(oldLeft, oldRight);
                        } else {
                            view.layout(left, paddingTop, right, b - t);
                        }
                    } else {
                        view.layout(left, paddingTop, right, b - t);
                    }

                    startMs += durationMs;
                    left = right;
                } else if (view == mLeftHandle) {
                    view.layout(mSelectedView.getLeft() - mHandleWidth,
                            paddingTop + mSelectedView.getPaddingTop(),
                            mSelectedView.getLeft(), b - t - mSelectedView.getPaddingBottom());
                } else if (view == mRightHandle) {
                    view.layout(mSelectedView.getRight(),
                            paddingTop + mSelectedView.getPaddingTop(),
                            mSelectedView.getRight() + mHandleWidth,
                            b - t - mSelectedView.getPaddingBottom());
                } else if (i == 0) { // Begin view
                    view.layout(0, paddingTop, leftViewWidth, b - t);
                    left += leftViewWidth;
                } else { // End view
                    view.layout(getWidth() - mHalfParentWidth - (mHalfParentWidth - leftViewWidth),
                            paddingTop, getWidth(), b - t);
                }
            }
        } else {
            for (int i = 0; i < childrenCount; i++) {
                final View view = getChildAt(i);
                final Object tag = view.getTag();
                if (tag != null) {
                    final long durationMs = computeViewDuration(view);

                    final int right = (int)((float)((startMs + durationMs) * viewWidth) /
                            (float)totalDurationMs) + mHalfParentWidth;

                    if (tag instanceof MovieMediaItem) {
                        if (left != view.getLeft() || right != view.getRight()) {
                            final int oldLeft = view.getLeft();
                            final int oldRight = view.getRight();
                            view.layout(left, paddingTop, right, b - t);
                            ((MediaItemView)view).onPositionChanged(oldLeft, oldRight);
                        } else {
                            view.layout(left, paddingTop, right, b - t);
                        }
                    } else {
                        view.layout(left, paddingTop, right, b - t);
                    }

                    startMs += durationMs;
                    left = right;
                } else if (view == mLeftHandle) {
                } else if (view == mRightHandle) {
                } else if (i == 0) { // Begin view
                    view.layout(0, paddingTop, mHalfParentWidth, b - t);
                    left += mHalfParentWidth;
                } else { // End view
                    view.layout(getWidth() - mHalfParentWidth, paddingTop, getWidth(), b - t);
                }
            }
        }

        mMoveLayoutPending = false;
    }

    /**
     * Compute the view width
     *
     * @param view The view
     *
     * @return The duration
     */
    private long computeViewDuration(View view) {
        long durationMs;
        final Object tag = view.getTag();
        if (tag != null) {
            if (tag instanceof MovieMediaItem) {
                final MovieMediaItem mediaItem = (MovieMediaItem)view.getTag();
                durationMs = mediaItem.getAppTimelineDuration();
                if (mediaItem.getBeginTransition() != null) {
                    durationMs -= mediaItem.getBeginTransition().getAppDuration();
                }

                if (mediaItem.getEndTransition() != null) {
                    durationMs -= mediaItem.getEndTransition().getAppDuration();
                }
            } else { // Transition
                final MovieTransition transition = (MovieTransition)tag;
                durationMs = transition.getAppDuration();
            }
        } else {
            durationMs = 0;
        }

        return durationMs;
    }

    /**
     * Create a new dialog
     *
     * @param id The dialog id
     * @param bundle The dialog bundle
     *
     * @return The dialog
     */
    public Dialog onCreateDialog(int id, final Bundle bundle) {
        // If the project is not yet loaded do nothing.
        if (mProject == null) {
            return null;
        }

        switch (id) {
            case VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity)getContext();
                return AlertDialogs.createAlert(activity,
                        FileUtils.getSimpleName(mediaItem.getFilename()),
                        0, mediaItem.isVideoClip() ?
                                activity.getString(R.string.editor_remove_video_question) :
                                    activity.getString(R.string.editor_remove_image_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        mMediaItemActionMode.finish();
                        unselectAllViews();

                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID);

                        ApiService.removeMediaItem(activity, mProject.getPath(), mediaItem.getId(),
                                mProject.getTheme());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_MEDIA_ITEM_ID);
                    }
                }, true);
            }

            case VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity)getContext();
                final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(activity.getString(R.string.editor_change_rendering_mode));
                final CharSequence[] renderingModeStrings = new CharSequence[3];
                renderingModeStrings[0] = getContext().getString(R.string.rendering_mode_black_borders);
                renderingModeStrings[1] = getContext().getString(R.string.rendering_mode_stretch);
                renderingModeStrings[2] = getContext().getString(R.string.rendering_mode_crop);

                final int currentRenderingMode = bundle.getInt(PARAM_DIALOG_CURRENT_RENDERING_MODE);
                final int currentRenderingModeIndex;
                switch (currentRenderingMode) {
                    case MediaItem.RENDERING_MODE_CROPPING: {
                        currentRenderingModeIndex = 2;
                        break;
                    }

                    case MediaItem.RENDERING_MODE_STRETCH: {
                        currentRenderingModeIndex = 1;
                        break;
                    }

                    case MediaItem.RENDERING_MODE_BLACK_BORDER:
                    default: {
                        currentRenderingModeIndex = 0;
                        break;
                    }
                }

                builder.setSingleChoiceItems(renderingModeStrings, currentRenderingModeIndex,
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: {
                                mediaItem.setAppRenderingMode(MediaItem.RENDERING_MODE_BLACK_BORDER);
                                ApiService.setMediaItemRenderingMode(getContext(),
                                        mProject.getPath(), mediaItem.getId(),
                                        MediaItem.RENDERING_MODE_BLACK_BORDER);
                                break;
                            }

                            case 1: {
                                mediaItem.setAppRenderingMode(MediaItem.RENDERING_MODE_STRETCH);
                                ApiService.setMediaItemRenderingMode(getContext(),
                                        mProject.getPath(),
                                        mediaItem.getId(), MediaItem.RENDERING_MODE_STRETCH);
                                break;
                            }

                            case 2: {
                                mediaItem.setAppRenderingMode(MediaItem.RENDERING_MODE_CROPPING);
                                ApiService.setMediaItemRenderingMode(getContext(),
                                        mProject.getPath(),
                                        mediaItem.getId(), MediaItem.RENDERING_MODE_CROPPING);
                                break;
                            }

                            default: {
                                break;
                            }
                        }
                        activity.removeDialog(VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID);
                    }
                });
                builder.setCancelable(true);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_CHANGE_RENDERING_MODE_ID);
                    }
                });
                return builder.create();
            }

            case VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID: {
                final MovieTransition transition = mProject.getTransition(
                        bundle.getString(PARAM_DIALOG_TRANSITION_ID));
                if (transition == null) {
                    return null;
                }

                final Activity activity = (Activity)getContext();
                return AlertDialogs.createAlert(activity,
                        activity.getString(R.string.remove),
                        0, activity.getString(R.string.editor_remove_transition_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        mTransitionActionMode.finish();
                        unselectAllViews();
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID);

                        ApiService.removeTransition(activity, mProject.getPath(),
                                transition.getId());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_TRANSITION_ID);
                    }
                }, true);
            }

            case VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID: {
                final MovieMediaItem mediaItem = mProject.getMediaItem(
                        bundle.getString(PARAM_DIALOG_MEDIA_ITEM_ID));
                if (mediaItem == null) {
                    return null;
                }

                final Activity activity = (Activity)getContext();
                return AlertDialogs.createAlert(activity,
                        FileUtils.getSimpleName(mediaItem.getFilename()),
                        0, activity.getString(R.string.editor_remove_effect_question),
                        activity.getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        mMediaItemActionMode.finish();
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID);

                        ApiService.removeEffect(activity, mProject.getPath(),
                                mediaItem.getId(), mediaItem.getEffect().getId());
                    }
                }, activity.getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        activity.removeDialog(VideoEditorActivity.DIALOG_REMOVE_EFFECT_ID);
                    }
                }, true);
            }

            default: {
                return null;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onDragEvent(DragEvent event) {
        boolean result = false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                // Claim to accept any dragged content
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_STARTED: " + event);
                }

                mDragMediaItemId = (String)event.getLocalState();

                // Hide the handles while dragging
                mLeftHandle.setVisibility(View.GONE);
                mRightHandle.setVisibility(View.GONE);

                mDropAfterMediaItem = null;
                mDropIndex = -1;

                // This view accepts drag
                result = true;
                break;
            }

            case DragEvent.ACTION_DRAG_ENTERED: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_ENTERED: " + event);
                }
                break;
            }

            case DragEvent.ACTION_DRAG_EXITED: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_EXITED: " + event);
                }

                // Redraw the "normal playhead"
                final View scrollView = (View)getParent().getParent();
                scrollView.setTag(R.id.playhead_type, TimelineHorizontalScrollView.PLAYHEAD_NORMAL);
                scrollView.invalidate();
                break;
            }

            case DragEvent.ACTION_DRAG_ENDED: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_ENDED: " + event);
                }

                mDragMediaItemId = null;
                mDropIndex = -1;

                // Hide the handles while dragging
                mLeftHandle.setVisibility(View.VISIBLE);
                mRightHandle.setVisibility(View.VISIBLE);

                // Redraw the "normal playhead"
                final View scrollView = (View)getParent().getParent();
                scrollView.setTag(R.id.playhead_type, TimelineHorizontalScrollView.PLAYHEAD_NORMAL);
                scrollView.invalidate();

                requestLayout();
                break;
            }

            case DragEvent.ACTION_DRAG_LOCATION: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DRAG_LOCATION: " + event);
                }

                moveToPosition(event.getX());
                // We returned true to DRAG_STARTED, so return true here
                result = true;
                break;
            }

            case DragEvent.ACTION_DROP: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "ACTION_DROP: " + event);
                }

                if (mDropIndex >= 0) {
                    final String afterMediaItemId =
                        mDropAfterMediaItem != null ? mDropAfterMediaItem.getId() : null;
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "ACTION_DROP: Index: " + mDropIndex + " | " + afterMediaItemId);
                    }
                    ApiService.moveMediaItem(getContext(), mProject.getPath(), mDragMediaItemId,
                            afterMediaItemId, null);
                }
                result = true;
                break;
            }


            default: {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Other drag event: " + event);
                }
                result = true;
                break;
            }
        }

        return result;
    }

    /**
     * Move the playhead during a move operation
     *
     * @param eventX The event horizontal position
     */
    private void moveToPosition(float eventX) {
        final View scrollView = (View)getParent().getParent();
        final int x = (int)eventX - scrollView.getScrollX();
        final long now = System.currentTimeMillis();
        if (now - mPrevDragScrollTime > 300) {
            if (x < mPrevDragPosition - 35) { // Backwards
                final long positionMs = getLeftDropPosition();
                if (mDropIndex >= 0) {
                    // Redraw the "move ok playhead"
                    scrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_OK);
                } else {
                    // Redraw the "move not ok playhead"
                    scrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_NOT_OK);
                }

                mListener.onRequestMovePlayhead(positionMs, true);
                scrollView.invalidate();

                mPrevDragPosition = x;
                mPrevDragScrollTime = now;
            } else if (x > mPrevDragPosition + 35) { // Forward
                final long positionMs = getRightDropPosition();
                if (mDropIndex >= 0) {
                    // Redraw the "move ok playhead"
                    scrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_OK);
                } else {
                    // Redraw the "move not ok playhead"
                    scrollView.setTag(R.id.playhead_type,
                            TimelineHorizontalScrollView.PLAYHEAD_MOVE_NOT_OK);
                }

                mListener.onRequestMovePlayhead(positionMs, true);
                scrollView.invalidate();

                mPrevDragPosition = x;
                mPrevDragScrollTime = now;
            }
        } else {
            mPrevDragPosition = x;
        }
    }

    /**
     * @return The valid time location of the drop (-1 if none)
     */
    private long getLeftDropPosition() {
        final List<MovieMediaItem> mediaItems = mProject.getMediaItems();
        long beginMs = 0;
        long endMs = 0;
        long timeMs = mProject.getPlayheadPos();

        final int mediaItemsCount = mediaItems.size();
        for (int i = 0; i < mediaItemsCount; i++) {
            final MovieMediaItem mediaItem = mediaItems.get(i);

            endMs = beginMs + mediaItem.getAppTimelineDuration();

            if (mediaItem.getEndTransition() != null) {
                if (i < mediaItemsCount - 1) {
                    endMs -= mediaItem.getEndTransition().getAppDuration();
                }
            }

            if (timeMs > beginMs && timeMs <= endMs) {
                if (mediaItem.getBeginTransition() != null) {
                    beginMs += mediaItem.getBeginTransition().getAppDuration();
                }

                if (!mDragMediaItemId.equals(mediaItem.getId())) {
                    if (i > 0) {
                        // Check if the previous item is the drag item
                        final MovieMediaItem prevMediaItem = mediaItems.get(i - 1);
                        if (!mDragMediaItemId.equals(prevMediaItem.getId())) {
                            mDropAfterMediaItem = prevMediaItem;
                            mDropIndex = i;
                            return beginMs;
                        } else {
                            mDropAfterMediaItem = null;
                            mDropIndex = -1;
                            return beginMs;
                        }
                    } else {
                        mDropAfterMediaItem = null;
                        mDropIndex = 0;
                        return 0;
                    }
                } else {
                    mDropAfterMediaItem = null;
                    mDropIndex = -1;
                    return beginMs;
                }
            }

            beginMs = endMs;
        }

        return timeMs;
    }

    /**
     * @return The valid time location of the drop (-1 if none)
     */
    private long getRightDropPosition() {
        final List<MovieMediaItem> mediaItems = mProject.getMediaItems();
        long beginMs = 0;
        long endMs = 0;
        long timeMs = mProject.getPlayheadPos();

        final int mediaItemsCount = mediaItems.size();
        for (int i = 0; i < mediaItemsCount; i++) {
            final MovieMediaItem mediaItem = mediaItems.get(i);

            endMs = beginMs + mediaItem.getAppTimelineDuration();

            if (mediaItem.getEndTransition() != null) {
                if (i < mediaItemsCount - 1) {
                    endMs -= mediaItem.getEndTransition().getAppDuration();
                }
            }

            if (timeMs >= beginMs && timeMs < endMs) {
                if (!mDragMediaItemId.equals(mediaItem.getId())) {
                    if (i < mediaItemsCount - 1) {
                        // Check if the next item is the drag item
                        final MovieMediaItem nextMediaItem = mediaItems.get(i + 1);
                        if (!mDragMediaItemId.equals(nextMediaItem.getId())) {
                            mDropAfterMediaItem = mediaItem;
                            mDropIndex = i;
                            return endMs;
                        } else {
                            mDropAfterMediaItem = null;
                            mDropIndex = -1;
                            return endMs;
                        }
                    } else {
                        mDropAfterMediaItem = mediaItem;
                        mDropIndex = i;
                        return endMs;
                    }
                } else {
                    mDropAfterMediaItem = null;
                    mDropIndex = -1;
                    return endMs;
                }
            }

            beginMs = endMs;
        }

        return timeMs;
    }

    /**
     * Pick an overlay
     *
     * @param mediaItemId Media item id
     */
    private void pickOverlay(String mediaItemId) {
        final Intent intent = new Intent(getContext(), OverlaysActivity.class);
        intent.putExtra(OverlaysActivity.PARAM_MEDIA_ITEM_ID, mediaItemId);
        ((Activity)getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_PICK_OVERLAY);
    }

    /**
     * Pick an effect of the specified category
     *
     * @param category The category
     * @param mediaItemId Media item id
     */
    private void pickEffect(int category, String mediaItemId) {
        final Intent intent = new Intent(getContext(), EffectsActivity.class);
        intent.putExtra(EffectsActivity.PARAM_CATEGORY, category);
        intent.putExtra(EffectsActivity.PARAM_MEDIA_ITEM_ID, mediaItemId);
        ((Activity)getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_PICK_EFFECT);
    }

    /**
     * Edit an effect of the specified category
     *
     * @param category The category
     * @param mediaItemId Media item id
     * @param type The effect type
     */
    private void editEffect(int category, String mediaItemId, int type) {
        final Intent intent = new Intent(getContext(), EffectsActivity.class);
        intent.putExtra(EffectsActivity.PARAM_CATEGORY, category);
        intent.putExtra(EffectsActivity.PARAM_MEDIA_ITEM_ID, mediaItemId);
        intent.putExtra(EffectsActivity.PARAM_EFFECT_TYPE, type);
        ((Activity)getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_EDIT_EFFECT);
    }

    /**
     * Pick a transition
     *
     * @param afterMediaItem After the media item
     *
     * @return true if the transition can be inserted
     */
    private boolean pickTransition(MovieMediaItem afterMediaItem) {
        // Check if the transition would be too short
        final long transitionDurationMs = getTransitionDuration(afterMediaItem);
        if (transitionDurationMs < MINIMUM_TRANSITION_DURATION) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.editor_transition_too_short),
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        final String afterMediaId = afterMediaItem != null ? afterMediaItem.getId() : null;
        final Intent intent = new Intent(getContext(), TransitionsActivity.class);
        intent.putExtra(TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID, afterMediaId);
        intent.putExtra(TransitionsActivity.PARAM_MINIMUM_DURATION, MINIMUM_TRANSITION_DURATION);
        intent.putExtra(TransitionsActivity.PARAM_DEFAULT_DURATION, transitionDurationMs);
        intent.putExtra(TransitionsActivity.PARAM_MAXIMUM_DURATION,
                getMaxTransitionDuration(afterMediaItem));
        ((Activity)getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_PICK_TRANSITION);
        return true;
    }

    /**
     * Edit a transition
     *
     * @param transition The transition
     */
    private void editTransition(MovieTransition transition) {
        final MovieMediaItem afterMediaItem = mProject.getPreviousMediaItem(transition);
        final String afterMediaItemId = afterMediaItem != null ? afterMediaItem.getId() : null;

        final Intent intent = new Intent(getContext(), TransitionsActivity.class);
        intent.putExtra(TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID, afterMediaItemId);
        intent.putExtra(TransitionsActivity.PARAM_TRANSITION_ID, transition.getId());
        intent.putExtra(TransitionsActivity.PARAM_TRANSITION_TYPE, transition.getType());
        intent.putExtra(TransitionsActivity.PARAM_MINIMUM_DURATION, MINIMUM_TRANSITION_DURATION);
        intent.putExtra(TransitionsActivity.PARAM_DEFAULT_DURATION, transition.getAppDuration());
        intent.putExtra(TransitionsActivity.PARAM_MAXIMUM_DURATION,
                getMaxTransitionDuration(afterMediaItem));
        ((Activity)getContext()).startActivityForResult(intent,
                VideoEditorActivity.REQUEST_CODE_EDIT_TRANSITION);
    }

    /**
     * Find the media item view with the specified id
     *
     * @param mediaItemId The media item id
     * @return The media item view
     */
    private View getMediaItemView(String mediaItemId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mediaItem = (MovieMediaItem)tag;
                if (mediaItemId.equals(mediaItem.getId())) {
                    return childView;
                }
            }
        }

        return null;
    }

    /**
     * Find the media item view index with the specified id
     *
     * @param mediaItemId The media item id
     * @return The media item view index
     */
    private int getMediaItemViewIndex(String mediaItemId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                final MovieMediaItem mediaItem = (MovieMediaItem)tag;
                if (mediaItemId.equals(mediaItem.getId())) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Find the transition view with the specified id
     *
     * @param transitionId The transition id
     *
     * @return The media item view
     */
    private View getTransitionView(String transitionId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    return childView;
                }
            }
        }

        return null;
    }

    /**
     * Remove a transition
     *
     * @param transitionId The transition id
     */
    public void removeTransitionView(String transitionId) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final Object tag = getChildAt(i).getTag();
            if (tag != null && tag instanceof MovieTransition) {
                final MovieTransition transition = (MovieTransition)tag;
                if (transitionId.equals(transition.getId())) {
                    // Remove the view
                    removeViewAt(i);

                    // Adjust the size of all the views
                    requestLayout();

                    // If this transition was removed by the user invalidate the menu item
                    if (mMediaItemActionMode != null) {
                        mMediaItemActionMode.invalidate();
                    }
                    return;
                }
            }
        }
    }

    /**
     * Remove all media item and transition views (leave the beginning and end views)
     */
    private void removeViews() {
        int index = 0;
        while (index < getChildCount()) {
            final Object tag = getChildAt(index).getTag();
            if (tag != null) { // Media item or transition view
                removeViewAt(index);
            } else {
                index++;
            }
        }
        requestLayout();

        // We cannot add clips by tapping the beginning view
        final View beginView = getChildAt(0);
        beginView.findViewById(R.id.add_left_media_item_button).setVisibility(View.GONE);
    }

    /**
     * Compute the transition duration
     *
     * @param afterMediaItem The position of the transition
     *
     * @return The transition duration
     */
    private long getTransitionDuration(MovieMediaItem afterMediaItem) {
        if (afterMediaItem == null) {
            final MovieMediaItem firstMediaItem = mProject.getFirstMediaItem();
            return Math.min(MAXIMUM_TRANSITION_DURATION / 2,
                    firstMediaItem.getAppTimelineDuration() / 4);
        } else if (mProject.isLastMediaItem(afterMediaItem.getId())) {
            return Math.min(MAXIMUM_TRANSITION_DURATION / 2,
                    afterMediaItem.getAppTimelineDuration() / 4);
        } else {
            final MovieMediaItem beforeMediaItem =
                mProject.getNextMediaItem(afterMediaItem.getId());
            final long minDurationMs = Math.min(afterMediaItem.getAppTimelineDuration(),
                    beforeMediaItem.getAppTimelineDuration());
            return Math.min(MAXIMUM_TRANSITION_DURATION / 2, minDurationMs / 4);
        }
    }

    /**
     * Compute the maximum transition duration
     *
     * @param afterMediaItem The position of the transition
     *
     * @return The transition duration
     */
    private long getMaxTransitionDuration(MovieMediaItem afterMediaItem) {
        if (afterMediaItem == null) {
            final MovieMediaItem firstMediaItem = mProject.getFirstMediaItem();
            return Math.min(MAXIMUM_TRANSITION_DURATION,
                    firstMediaItem.getAppTimelineDuration() / 4);
        } else if (mProject.isLastMediaItem(afterMediaItem.getId())) {
            return Math.min(MAXIMUM_TRANSITION_DURATION,
                    afterMediaItem.getAppTimelineDuration() / 4);
        } else {
            final MovieMediaItem beforeMediaItem =
                mProject.getNextMediaItem(afterMediaItem.getId());
            final long minDurationMs = Math.min(afterMediaItem.getAppTimelineDuration(),
                    beforeMediaItem.getAppTimelineDuration());
            return Math.min(MAXIMUM_TRANSITION_DURATION, minDurationMs / 4);
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void setSelected(boolean selected) {
        // Close the contextual action bar
        if (selected == false) {
            if (mMediaItemActionMode != null) {
                mMediaItemActionMode.finish();
                mMediaItemActionMode = null;
            }

            if (mTransitionActionMode != null) {
                mTransitionActionMode.finish();
                mTransitionActionMode = null;
            }

            mLeftHandle.setVisibility(View.GONE);
            mLeftHandle.setListener(null);
            mRightHandle.setVisibility(View.GONE);
            mRightHandle.setListener(null);
            mSelectedView = null;
        }

        // Unselect all the children
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            childView.setSelected(false);
        }
    }

    /**
     * Select a view and unselect any view that is selected.
     *
     * @param selectedView The view to select
     * @param selected true if selected
     */
    private void selectView(View selectedView, boolean selected) {
        // Check if the selection has changed
        if (selectedView.isSelected() == selected) {
            return;
        }

        if (selected) {
            // Unselect all other views
            unselectAllViews();
        }

        // Select the new view
        selectedView.setSelected(selected);

        final Object tag = selectedView.getTag();
        if (selected == false) {
            mSelectedView = null;
            mLeftHandle.setVisibility(View.GONE);
            mLeftHandle.setListener(null);
            mRightHandle.setVisibility(View.GONE);
            mRightHandle.setListener(null);
            return;
        }

        if (tag instanceof MovieMediaItem) {
            mSelectedView = selectedView;

            final View scrollView = (View)getParent().getParent();
            final MediaItemView mediaItemView = (MediaItemView)selectedView;
            if (mediaItemView.isInProgress()) {
                mLeftHandle.setEnabled(false);
                mRightHandle.setEnabled(false);
            } else {
                mLeftHandle.setEnabled(true);
                mRightHandle.setEnabled(true);
            }

            final MovieMediaItem mi = (MovieMediaItem)tag;
            if (mMediaItemActionMode == null) {
                startActionMode(new MediaItemActionModeCallback(mi));
            }

            final boolean videoClip = mi.isVideoClip();
            if (videoClip) {
                mLeftHandle.setVisibility(View.VISIBLE);
                mLeftHandle.bringToFront();
                mLeftHandle.setLimitReached(mi.getAppBoundaryBeginTime() <= 0,
                        mi.getAppTimelineDuration() <=
                            MediaItemUtils.getMinimumVideoItemDuration());
                mLeftHandle.setListener(new HandleView.MoveListener() {
                    private View mTrimmedView;
                    private MovieMediaItem mMediaItem;
                    private long mTransitionsDurationMs;
                    private long mOriginalBeginMs, mOriginalEndMs;
                    private long mMinimumDurationMs;
                    private int mOriginalWidth;
                    private int mMovePosition;

                    /*
                     * {@inheritDoc}
                     */
                    public void onMoveBegin(HandleView view) {
                        mMediaItem = (MovieMediaItem)mediaItemView.getTag();
                        mTransitionsDurationMs = (mMediaItem.getBeginTransition() != null ?
                                mMediaItem.getBeginTransition().getAppDuration() : 0)
                                + (mMediaItem.getEndTransition() != null ?
                                        mMediaItem.getEndTransition().getAppDuration() : 0);
                        mOriginalBeginMs = mMediaItem.getAppBoundaryBeginTime();
                        mOriginalEndMs = mMediaItem.getAppBoundaryEndTime();
                        mOriginalWidth = mediaItemView.getWidth();
                        mMinimumDurationMs = MediaItemUtils.getMinimumVideoItemDuration();
                        setTrimState(mediaItemView, true);
                        mTrimmedView = mediaItemView;

                        mListener.onTrimMediaItemBegin(mMediaItem);
                        if (videoClip) { // Video clip
                            mListener.onTrimMediaItem(mMediaItem,
                                    mMediaItem.getAppBoundaryBeginTime());
                        } else {
                            mListener.onTrimMediaItem(mMediaItem, 0);
                        }
                        // Move the playhead
                        scrollView.setTag(R.id.playhead_offset, view.getRight());
                        scrollView.invalidate();
                    }

                    /*
                     * {@inheritDoc}
                     */
                    public boolean onMove(HandleView view, int left, int delta) {
                        if (mMoveLayoutPending) {
                            return false;
                        }

                        int position = left + delta;
                        mMovePosition = position;
                        // Compute what will become the width of the view
                        int newWidth = mTrimmedView.getRight() - position;
                        if (newWidth == mTrimmedView.getWidth()) {
                            return false;
                        }

                        // Compute the new duration
                        long newDurationMs = mTransitionsDurationMs +
                                (newWidth * mProject.computeDuration()) /
                                (getWidth() - (2 * mHalfParentWidth));
                        if (Math.abs(mMediaItem.getAppTimelineDuration() - newDurationMs) <
                                TIME_TOLERANCE) {
                            return false;
                        } else if (newDurationMs < Math.max(2 * mTransitionsDurationMs,
                                mMinimumDurationMs)) {
                            newDurationMs = Math.max(2 * mTransitionsDurationMs,
                                    mMinimumDurationMs);
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = mTrimmedView.getRight() - newWidth;
                        } else if (mMediaItem.getAppBoundaryEndTime() - newDurationMs < 0) {
                            newDurationMs = mMediaItem.getAppBoundaryEndTime();
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = mTrimmedView.getRight() - newWidth;
                        }

                        // Check if the duration would change
                        if (newDurationMs == mMediaItem.getAppTimelineDuration()) {
                            return false;
                        }

                        mMediaItem.setAppExtractBoundaries(
                                mMediaItem.getAppBoundaryEndTime() - newDurationMs,
                                mMediaItem.getAppBoundaryEndTime());

                        mLeftHandle.setLimitReached(mMediaItem.getAppBoundaryBeginTime() <= 0,
                                mMediaItem.getAppTimelineDuration() <= mMinimumDurationMs);
                        mMoveLayoutPending = true;
                        scrollView.setTag(R.id.left_view_width,
                                mHalfParentWidth - (newWidth - mOriginalWidth));
                        scrollView.setTag(R.id.playhead_offset, position);
                        requestLayout();

                        mListener.onTrimMediaItem(mMediaItem,
                                mMediaItem.getAppBoundaryBeginTime());
                        return true;
                    }

                    /*
                     * {@inheritDoc}
                     */
                    public void onMoveEnd(final HandleView view, final int left, final int delta) {
                        final int position = left + delta;
                        if (mMoveLayoutPending || (position != mMovePosition)) {
                            mHandler.post(new Runnable() {
                                /*
                                 * {@inheritDoc}
                                 */
                                public void run() {
                                    if (mMoveLayoutPending) {
                                        mHandler.post(this);
                                    } else if (position != mMovePosition) {
                                        if (onMove(view, left, delta)) {
                                            mHandler.post(this);
                                        } else {
                                            moveDone();
                                        }
                                    } else {
                                        moveDone();
                                    }
                                }
                            });
                        } else {
                            moveDone();
                        }
                    }

                    /**
                     * The move is complete
                     */
                    private void moveDone() {
                        // Layout all the children to ensure that
                        scrollView.setTag(R.id.left_view_width, mHalfParentWidth);
                        scrollView.setTag(R.id.playhead_offset, -1);
                        scrollView.invalidate();
                        // Note: invalidate the parent does not invalidate the children
                        invalidateAllChildren();

                        mListener.onTrimMediaItemEnd(mMediaItem,
                                mMediaItem.getAppBoundaryBeginTime());

                        setTrimState(mTrimmedView, false);
                        if (Math.abs(mOriginalBeginMs - mMediaItem.getAppBoundaryBeginTime()) >
                                    TIME_TOLERANCE
                                || Math.abs(mOriginalEndMs - mMediaItem.getAppBoundaryEndTime()) >
                                    TIME_TOLERANCE) {

                            if (videoClip) { // Video clip
                                ApiService.setMediaItemBoundaries(getContext(), mProject.getPath(),
                                        mMediaItem.getId(), mMediaItem.getAppBoundaryBeginTime(),
                                        mMediaItem.getAppBoundaryEndTime());
                            } else { // Image
                                ApiService.setMediaItemDuration(getContext(), mProject.getPath(),
                                        mMediaItem.getId(), mMediaItem.getAppTimelineDuration());
                            }

                            final long durationMs = mMediaItem.getAppTimelineDuration();
                            mRightHandle.setLimitReached(durationMs <=
                                MediaItemUtils.getMinimumMediaItemDuration(mMediaItem),
                                    videoClip ? (mMediaItem.getAppBoundaryEndTime() >=
                                        mMediaItem.getDuration()) : durationMs >=
                                            MAXIMUM_IMAGE_DURATION);

                            mLeftHandle.setEnabled(false);
                            mRightHandle.setEnabled(false);
                        }
                    }
                });
            }

            mRightHandle.setVisibility(View.VISIBLE);
            mRightHandle.bringToFront();
            final long durationMs = mi.getAppTimelineDuration();
            mRightHandle.setLimitReached(
                    durationMs <= MediaItemUtils.getMinimumMediaItemDuration(mi),
                    videoClip ? (mi.getAppBoundaryEndTime() >= mi.getDuration()) :
                        durationMs >= MAXIMUM_IMAGE_DURATION);
            mRightHandle.setListener(new HandleView.MoveListener() {
                private View mTrimmedView;
                private MovieMediaItem mMediaItem;
                private long mTransitionsDurationMs;
                private long mOriginalBeginMs, mOriginalEndMs;
                private long mMinimumItemDurationMs;
                private int mMovePosition;

                /*
                 * {@inheritDoc}
                 */
                public void onMoveBegin(HandleView view) {
                    mMediaItem = (MovieMediaItem)mediaItemView.getTag();
                    mTransitionsDurationMs = (mMediaItem.getBeginTransition() != null ?
                            mMediaItem.getBeginTransition().getAppDuration() : 0)
                            + (mMediaItem.getEndTransition() != null ?
                                    mMediaItem.getEndTransition().getAppDuration() : 0);
                    mOriginalBeginMs = mMediaItem.getAppBoundaryBeginTime();
                    mOriginalEndMs = mMediaItem.getAppBoundaryEndTime();
                    mMinimumItemDurationMs = MediaItemUtils.getMinimumMediaItemDuration(mMediaItem);
                    setTrimState(mediaItemView, true);
                    mTrimmedView = mediaItemView;

                    mListener.onTrimMediaItemBegin(mMediaItem);
                    if (videoClip) { // Video clip
                        mListener.onTrimMediaItem(mMediaItem, mMediaItem.getAppBoundaryEndTime());
                    } else {
                        mListener.onTrimMediaItem(mMediaItem, 0);
                    }

                    // Move the playhead
                    scrollView.setTag(R.id.playhead_offset, view.getLeft());
                    scrollView.invalidate();
                }

                /*
                 * {@inheritDoc}
                 */
                public boolean onMove(HandleView view, int left, int delta) {
                    if (mMoveLayoutPending) {
                        return false;
                    }

                    int position = left + delta;
                    mMovePosition = position;

                    long newDurationMs;
                    // Compute what will become the width of the view
                    int newWidth = position - mTrimmedView.getLeft();
                    if (newWidth == mTrimmedView.getWidth()) {
                        return false;
                    }

                    // Compute the new duration
                    newDurationMs = mTransitionsDurationMs +
                            (newWidth * mProject.computeDuration()) /
                            (getWidth() - (2 * mHalfParentWidth));
                    if (Math.abs(mMediaItem.getAppTimelineDuration() - newDurationMs) <
                            TIME_TOLERANCE) {
                        return false;
                    }

                    if (videoClip) { // Video clip
                        if (newDurationMs < Math.max(2 * mTransitionsDurationMs,
                                mMinimumItemDurationMs)) {
                            newDurationMs = Math.max(2 * mTransitionsDurationMs,
                                    mMinimumItemDurationMs);
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        } else if (mMediaItem.getAppBoundaryBeginTime() + newDurationMs >
                                mMediaItem.getDuration()) {
                            newDurationMs = mMediaItem.getDuration() -
                                mMediaItem.getAppBoundaryBeginTime();
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        }

                        if (newDurationMs == mMediaItem.getAppTimelineDuration()) {
                            return false;
                        }

                        mMediaItem.setAppExtractBoundaries(mMediaItem.getAppBoundaryBeginTime(),
                                mMediaItem.getAppBoundaryBeginTime() + newDurationMs);
                        mListener.onTrimMediaItem(mMediaItem, mMediaItem.getAppBoundaryEndTime());
                    } else { // Image
                        if (newDurationMs < Math.max(mMinimumItemDurationMs,
                                2 * mTransitionsDurationMs)) {
                            newDurationMs = Math.max(mMinimumItemDurationMs,
                                    2 * mTransitionsDurationMs);
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        } else if (newDurationMs > MAXIMUM_IMAGE_DURATION) {
                            newDurationMs = MAXIMUM_IMAGE_DURATION;
                            newWidth = (int)(((newDurationMs - mTransitionsDurationMs) *
                                    (getWidth() - (2 * mHalfParentWidth)) /
                                    mProject.computeDuration()));
                            position = newWidth + mTrimmedView.getLeft();
                        }

                        // Check if the duration would change
                        if (newDurationMs == mMediaItem.getAppTimelineDuration()) {
                            return false;
                        }

                        mMediaItem.setAppExtractBoundaries(0, newDurationMs);
                        mListener.onTrimMediaItem(mMediaItem, 0);
                    }

                    scrollView.setTag(R.id.playhead_offset, position);
                    mRightHandle.setLimitReached(
                            newDurationMs <= mMinimumItemDurationMs,
                            videoClip ? (mMediaItem.getAppBoundaryEndTime() >=
                                mMediaItem.getDuration()) : newDurationMs >=
                                    MAXIMUM_IMAGE_DURATION);

                    mMoveLayoutPending = true;
                    requestLayout();

                    return true;
                }

                /*
                 * {@inheritDoc}
                 */
                public void onMoveEnd(final HandleView view, final int left, final int delta) {
                    final int position = left + delta;
                    if (mMoveLayoutPending || (position != mMovePosition)) {
                        mHandler.post(new Runnable() {
                            /*
                             * {@inheritDoc}
                             */
                            public void run() {
                                if (mMoveLayoutPending) {
                                    mHandler.post(this);
                                } else if (position != mMovePosition) {
                                    if (onMove(view, left, delta)) {
                                        mHandler.post(this);
                                    } else {
                                        moveDone();
                                    }
                                } else {
                                    moveDone();
                                }
                            }
                        });
                    } else {
                        moveDone();
                    }
                }

                /**
                 * The move is complete
                 */
                private void moveDone() {
                    scrollView.setTag(R.id.playhead_offset, -1);
                    scrollView.invalidate();
                    // Note: invalidate the parent does not invalidate the children
                    invalidateAllChildren();

                    mListener.onTrimMediaItemEnd(mMediaItem,
                            mMediaItem.getAppBoundaryEndTime());
                    setTrimState(mTrimmedView, false);
                    if (Math.abs(mOriginalBeginMs - mMediaItem.getAppBoundaryBeginTime()) >
                            TIME_TOLERANCE ||
                            Math.abs(mOriginalEndMs - mMediaItem.getAppBoundaryEndTime()) >
                            TIME_TOLERANCE) {
                        if (videoClip) { // Video clip
                            ApiService.setMediaItemBoundaries(getContext(), mProject.getPath(),
                                    mMediaItem.getId(), mMediaItem.getAppBoundaryBeginTime(),
                                    mMediaItem.getAppBoundaryEndTime());
                        } else { // Image
                            ApiService.setMediaItemDuration(getContext(), mProject.getPath(),
                                    mMediaItem.getId(), mMediaItem.getAppTimelineDuration());
                        }

                        if (videoClip) {
                            mLeftHandle.setLimitReached(mMediaItem.getAppBoundaryBeginTime() <= 0,
                                    mMediaItem.getAppTimelineDuration() <= mMinimumItemDurationMs);
                        }

                        mLeftHandle.setEnabled(false);
                        mRightHandle.setEnabled(false);
                    }
                }
            });
        } else if (tag instanceof MovieTransition) {
            if (mTransitionActionMode == null) {
                startActionMode(new TransitionActionModeCallback((MovieTransition)tag));
            }
        }
    }

    /**
     * Set the trimming state for all media item views
     *
     * @param trimmingView The view which enters/exists the trimming state
     * @param trimming true if trimming
     */
    private void setTrimState(View trimmingView, boolean trimming) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null && tag instanceof MovieMediaItem) {
                ((MediaItemView)childView).setTrimMode(trimmingView, trimming);
            }
        }
    }

    /**
     * Set the playback state for all media item views
     *
     * @param trimmingView The view which enters/exists the trimming state
     * @param playback true if trimming
     */
    private void setPlaybackState(boolean playback) {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            final Object tag = childView.getTag();
            if (tag != null) {
                if (tag instanceof MovieMediaItem) {
                    ((MediaItemView)childView).setPlaybackMode(playback);
                } else if (tag instanceof MovieTransition) {
                    ((TransitionView)childView).setPlaybackMode(playback);
                }
            }
        }
    }

    /**
     * Unselect all views
     */
    private void unselectAllViews() {
        ((RelativeLayout)getParent()).setSelected(false);
    }

    /**
     * Invalidate all children
     */
    private void invalidateAllChildren() {
        final int childrenCount = getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View childView = getChildAt(i);
            childView.invalidate();
        }
    }
}