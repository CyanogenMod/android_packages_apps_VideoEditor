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

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.MediaProperties;
import android.media.videoeditor.VideoEditor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.MovieMediaItem;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.util.FileUtils;
import com.android.videoeditor.util.MediaItemUtils;
import com.android.videoeditor.util.StringUtils;
import com.android.videoeditor.widgets.AudioTrackLinearLayout;
import com.android.videoeditor.widgets.MediaLinearLayout;
import com.android.videoeditor.widgets.OverlayLinearLayout;
import com.android.videoeditor.widgets.PlayheadView;
import com.android.videoeditor.widgets.PreviewSurfaceView;
import com.android.videoeditor.widgets.ScrollViewListener;
import com.android.videoeditor.widgets.TimelineHorizontalScrollView;
import com.android.videoeditor.widgets.TimelineRelativeLayout;
import com.android.videoeditor.widgets.ZoomControl;

/**
 * This is the main activity of the video editor. It handles video editing of
 * a project.
 */
public class VideoEditorActivity extends VideoEditorBaseActivity
        implements SurfaceHolder.Callback {
    // Logging
    private static final String TAG = "VideoEditorActivity";

    // State keys
    private static final String STATE_INSERT_AFTER_MEDIA_ITEM_ID = "insert_after_media_item_id";
    private static final String STATE_PLAYING = "playing";

    // Menu ids
    private static final int MENU_IMPORT_IMAGE_ID = 2;
    private static final int MENU_IMPORT_VIDEO_ID = 3;
    private static final int MENU_IMPORT_AUDIO_ID = 4;
    private static final int MENU_CHANGE_ASPECT_RATIO_ID = 5;
    private static final int MENU_EDIT_PROJECT_NAME_ID = 6;
    private static final int MENU_DELETE_PROJECT_ID = 7;
    private static final int MENU_EXPORT_MOVIE_ID = 8;
    private static final int MENU_PLAY_EXPORTED_MOVIE = 9;
    private static final int MENU_SHARE_VIDEO = 10;
    private static final int MENU_CAPTURE_VIDEO_ID = 11;
    private static final int MENU_CAPTURE_IMAGE_ID = 12;

    // Dialog ids
    private static final int DIALOG_DELETE_PROJECT_ID = 1;
    private static final int DIALOG_EDIT_PROJECT_NAME_ID = 2;
    private static final int DIALOG_CHOOSE_ASPECT_RATIO_ID = 3;
    private static final int DIALOG_EXPORT_OPTIONS_ID = 4;

    public static final int DIALOG_REMOVE_MEDIA_ITEM_ID = 10;
    public static final int DIALOG_REMOVE_TRANSITION_ID = 11;
    public static final int DIALOG_CHANGE_RENDERING_MODE_ID = 12;
    public static final int DIALOG_REMOVE_OVERLAY_ID = 13;
    public static final int DIALOG_REMOVE_EFFECT_ID = 14;
    public static final int DIALOG_REMOVE_AUDIO_TRACK_ID = 15;

    // Dialog parameters
    private static final String PARAM_ASPECT_RATIOS_LIST = "aspect_ratios";
    private static final String PARAM_CURRENT_ASPECT_RATIO_INDEX = "current_aspect_ratio";

    // Request codes
    private static final int REQUEST_CODE_IMPORT_VIDEO = 1;
    private static final int REQUEST_CODE_IMPORT_IMAGE = 2;
    private static final int REQUEST_CODE_IMPORT_MUSIC = 3;
    private static final int REQUEST_CODE_CAPTURE_VIDEO = 4;
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 5;

    public static final int REQUEST_CODE_EDIT_TRANSITION = 10;
    public static final int REQUEST_CODE_PICK_TRANSITION = 11;
    public static final int REQUEST_CODE_PICK_OVERLAY = 12;
    public static final int REQUEST_CODE_EDIT_OVERLAY = 13;
    public static final int REQUEST_CODE_PICK_EFFECT = 14;
    public static final int REQUEST_CODE_EDIT_EFFECT = 15;

    // The maximum zoom level
    private static final int MAX_ZOOM_LEVEL = 120;
    private static final int ZOOM_STEP = 2;

    private final TimelineRelativeLayout.LayoutCallback mLayoutCallback =
        new TimelineRelativeLayout.LayoutCallback() {
        /*
         * {@inheritDoc}
         */
        public void onLayoutComplete() {
            // Scroll the timeline such that the specified position
            // is in the center of the screen
            mTimelineScroller.appScrollTo(timeToDimension(mProject.getPlayheadPos()), true);
        }
    };

    // Instance variables
    private PreviewSurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private ImageView mOverlayView;
    private PreviewThread mPreviewThread;
    private View mEditorProjectView;
    private View mEditorEmptyView;
    private TimelineHorizontalScrollView mTimelineScroller;
    private TimelineRelativeLayout mTimelineLayout;
    private OverlayLinearLayout mOverlayLayout;
    private AudioTrackLinearLayout mAudioTrackLayout;
    private MediaLinearLayout mMediaLayout;
    private PlayheadView mPlayheadView;
    private TextView mTimeView;
    private ImageButton mPreviewPlayButton;
    private ImageButton mPreviewRewindButton, mPreviewNextButton, mPreviewPrevButton;
    private int mActivityWidth;
    private String mInsertMediaItemAfterMediaItemId;
    private long mCurrentPlayheadPosMs;
    private ProgressDialog mExportProgressDialog;
    private ZoomControl mZoomControl;

    // Variables used in onActivityResult
    private Uri mAddMediaItemVideoUri;
    private Uri mAddMediaItemImageUri;
    private Uri mAddAudioTrackUri;
    private String mAddTransitionAfterMediaId;
    private int mAddTransitionType;
    private long mAddTransitionDurationMs;
    private String mEditTransitionAfterMediaId, mEditTransitionId;
    private int mEditTransitionType;
    private long mEditTransitionDurationMs;
    private String mAddOverlayMediaItemId;
    private Bundle mAddOverlayUserAttributes;
    private String mEditOverlayMediaItemId;
    private String mEditOverlayId;
    private Bundle mEditOverlayUserAttributes;
    private String mAddEffectMediaItemId;
    private int mAddEffectType;
    private Rect mAddKenBurnsStartRect;
    private Rect mAddKenBurnsEndRect;
    private String mEditEffectMediaItemId;
    private int mEditEffectType;
    private Rect mEditKenBurnsStartRect;
    private Rect mEditKenBurnsEndRect;
    private boolean mRestartPreview;
    private Uri mCaptureMediaUri;

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prepare the surface holder
        mSurfaceView = (PreviewSurfaceView)findViewById(R.id.video_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mOverlayView = (ImageView)findViewById(R.id.overlay_layer);

        mEditorProjectView = findViewById(R.id.editor_project_view);
        mEditorEmptyView = findViewById(R.id.empty_project_view);

        mTimelineScroller = (TimelineHorizontalScrollView)findViewById(R.id.timeline_scroller);
        mTimelineLayout = (TimelineRelativeLayout)findViewById(R.id.timeline);
        mMediaLayout = (MediaLinearLayout)findViewById(R.id.timeline_media);
        mOverlayLayout = (OverlayLinearLayout)findViewById(R.id.timeline_overlays);
        mAudioTrackLayout = (AudioTrackLinearLayout)findViewById(R.id.timeline_audio_tracks);
        mPlayheadView = (PlayheadView)findViewById(R.id.timeline_playhead);
        mPreviewPlayButton = (ImageButton)findViewById(R.id.editor_play);
        mPreviewRewindButton = (ImageButton)findViewById(R.id.editor_rewind);
        mPreviewNextButton = (ImageButton)findViewById(R.id.editor_next);
        mPreviewPrevButton = (ImageButton)findViewById(R.id.editor_prev);

        mTimeView = (TextView)findViewById(R.id.editor_time);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        mMediaLayout.setListener(new MediaLinearLayout.MediaLayoutListener() {
            /*
             * {@inheritDoc}
             */
            public void onRequestScrollBy(int scrollBy, boolean smooth) {
                mTimelineScroller.appScrollBy(scrollBy, smooth);
            }

            /*
             * {@inheritDoc}
             */
            public void onRequestMovePlayhead(long scrollToTime, boolean smooth) {
                movePlayhead(scrollToTime);
            }

            /*
             * {@inheritDoc}
             */
            public void onAddMediaItem(String afterMediaItemId) {
                mInsertMediaItemAfterMediaItemId = afterMediaItemId;

                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                intent.setType("video/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_VIDEO);
            }

            /*
             * {@inheritDoc}
             */
            public void onTrimMediaItemBegin(MovieMediaItem mediaItem) {
                onProjectEditStateChange(true);
            }

            /*
             * {@inheritDoc}
             */
            public void onTrimMediaItem(MovieMediaItem mediaItem, long timeMs) {
                updateTimelineDuration();
                if (mProject != null && mPreviewThread != null && !mPreviewThread.isPlaying()) {
                    if (mediaItem.isVideoClip()) {
                        if (timeMs >= 0) {
                            mPreviewThread.renderMediaItemFrame(mediaItem, timeMs);
                        }
                    } else {
                        mPreviewThread.previewFrame(mProject,
                                mProject.getMediaItemBeginTime(mediaItem.getId()) + timeMs,
                                mProject.getMediaItemCount() == 0);
                    }
                }
            }

            /*
             * {@inheritDoc}
             */
            public void onTrimMediaItemEnd(MovieMediaItem mediaItem, long timeMs) {
                onProjectEditStateChange(false);
                // We need to repaint the timeline layout to clear the old
                // playhead position (the one drawn during trimming)
                mTimelineLayout.invalidate();
                showPreviewFrame();
            }
        });

        mAudioTrackLayout.setListener(new AudioTrackLinearLayout.AudioTracksLayoutListener() {
            /*
             * {@inheritDoc}
             */
            public void onAddAudioTrack() {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_MUSIC);
            }
        });

        mTimelineScroller.addScrollListener(new ScrollViewListener() {
            // Instance variables
            private int mActiveWidth;
            private long mDurationMs;
            private int mLastScrollX;

            /*
             * {@inheritDoc}
             */
            public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll) {
                if (!appScroll && mProject != null) {
                    mActiveWidth = mMediaLayout.getWidth() - mActivityWidth;
                    mDurationMs = mProject.computeDuration();
                } else {
                    mActiveWidth = 0;
                }

                mLastScrollX = scrollX;
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll) {
                // We check if the project is valid since the project may
                // close while scrolling
                if (!appScroll && mActiveWidth > 0 && mProject != null) {
                    final int deltaScrollX = Math.abs(mLastScrollX - scrollX);

                    if (deltaScrollX < 100) {
                        mLastScrollX = scrollX;
                        // When scrolling at high speed do not display the
                        // preview frame
                        final long timeMs = (scrollX * mDurationMs) / mActiveWidth;
                        if (setPlayhead(timeMs < 0 ? 0 : timeMs)) {
                            showPreviewFrame();
                        }
                    }
                }
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                // We check if the project is valid since the project may
                // close while scrolling
                if (!appScroll && mActiveWidth > 0 && mProject != null && scrollX != mLastScrollX) {
                    final long timeMs = (scrollX * mDurationMs) / mActiveWidth;
                    if (setPlayhead(timeMs < 0 ? 0 : timeMs)) {
                        showPreviewFrame();
                    }
                }
            }
        });

        mTimelineScroller.setScaleListener(new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            // Guard against this many scale events in the opposite direction
            private static final int SCALE_TOLERANCE = 3;

            private int mLastScaleFactorSign;
            private float mLastScaleFactor;

            /*
             * {@inheritDoc}
             */
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mLastScaleFactorSign = 0;
                return true;
            }

            /*
             * {@inheritDoc}
             */
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mProject == null) {
                    return false;
                }

                final float scaleFactor = detector.getScaleFactor();
                final float deltaScaleFactor = scaleFactor - mLastScaleFactor;
                if (deltaScaleFactor > 0.01f || deltaScaleFactor < -0.01f) {
                    if (scaleFactor < 1.0f) {
                        if (mLastScaleFactorSign <= 0) {
                            zoomTimeline(mProject.getZoomLevel() - ZOOM_STEP, true);
                        }

                        if (mLastScaleFactorSign > -SCALE_TOLERANCE) {
                            mLastScaleFactorSign--;
                        }
                    } else if (scaleFactor > 1.0f) {
                        if (mLastScaleFactorSign >= 0) {
                            zoomTimeline(mProject.getZoomLevel() + ZOOM_STEP, true);
                        }

                        if (mLastScaleFactorSign < SCALE_TOLERANCE) {
                            mLastScaleFactorSign++;
                        }
                    }
                }

                mLastScaleFactor = scaleFactor;
                return true;
            }

            /*
             * {@inheritDoc}
             */
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
        });

        if (savedInstanceState != null) {
            mInsertMediaItemAfterMediaItemId = savedInstanceState.getString(
                    STATE_INSERT_AFTER_MEDIA_ITEM_ID);
            mRestartPreview = savedInstanceState.getBoolean(STATE_PLAYING);
        } else {
            mRestartPreview = false;
        }

        // Compute the activity width
        final Display display = getWindowManager().getDefaultDisplay();
        mActivityWidth = display.getWidth();

        mSurfaceView.setGestureListener(new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                            float velocityY) {
                        if (mPreviewThread != null && mPreviewThread.isPlaying()) {
                            return false;
                        }

                        mTimelineScroller.fling(-(int)velocityX);
                        return true;
                    }

                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
                        if (mPreviewThread != null && mPreviewThread.isPlaying()) {
                            return false;
                        }

                        mTimelineScroller.scrollBy((int)distanceX, 0);
                        return true;
                    }
                }));

        mZoomControl = ((ZoomControl)findViewById(R.id.editor_zoom));
        mZoomControl.setMax(MAX_ZOOM_LEVEL);
        mZoomControl.setOnZoomChangeListener(new ZoomControl.OnZoomChangeListener() {
            /*
             * {@inheritDoc}
             */
            public void onProgressChanged(int progress, boolean fromUser) {
                if (mProject != null) {
                    zoomTimeline(progress, false);
                }
            }
        });
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        // Dismiss the export progress dialog. If the export will still be pending
        // when we return to this activity, we will display this dialog again.
        if (mExportProgressDialog != null) {
            mExportProgressDialog.dismiss();
            mExportProgressDialog = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

        if (mProject != null) {
            mMediaLayout.onResume();
            mAudioTrackLayout.onResume();
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(STATE_INSERT_AFTER_MEDIA_ITEM_ID, mInsertMediaItemAfterMediaItemId);
        outState.putBoolean(STATE_PLAYING,
                mPreviewThread != null ? mPreviewThread.isPlaying() : false);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_CAPTURE_VIDEO_ID, Menu.NONE,
                R.string.editor_capture_video).setIcon(
                        R.drawable.ic_menu_video_camera).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_CAPTURE_IMAGE_ID, Menu.NONE,
                R.string.editor_capture_image).setIcon(
                        R.drawable.ic_menu_camera).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_IMPORT_VIDEO_ID, Menu.NONE,
                R.string.editor_import_video).setIcon(
                        R.drawable.ic_menu_add_video).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_IMPORT_IMAGE_ID, Menu.NONE,
                R.string.editor_import_image).setIcon(
                        R.drawable.ic_menu_add_image).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_IMPORT_AUDIO_ID, Menu.NONE, R.string.editor_import_audio).setIcon(
                R.drawable.ic_menu_add_audio_clip);
        menu.add(Menu.NONE, MENU_CHANGE_ASPECT_RATIO_ID, Menu.NONE,
                R.string.editor_change_aspect_ratio);
        menu.add(Menu.NONE, MENU_EDIT_PROJECT_NAME_ID, Menu.NONE,
                R.string.editor_edit_project_name);
        menu.add(Menu.NONE, MENU_EXPORT_MOVIE_ID, Menu.NONE, R.string.editor_export_movie);
        menu.add(Menu.NONE, MENU_PLAY_EXPORTED_MOVIE, Menu.NONE,
                R.string.editor_play_exported_movie);
        menu.add(Menu.NONE, MENU_SHARE_VIDEO, Menu.NONE, R.string.editor_share_movie);
        menu.add(Menu.NONE, MENU_DELETE_PROJECT_ID, Menu.NONE, R.string.editor_delete_project);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean haveProject = mProject != null;
        final boolean haveMediaItems = haveProject && mProject.getMediaItemCount() > 0;
        menu.findItem(MENU_CAPTURE_VIDEO_ID).setVisible(haveProject);
        menu.findItem(MENU_CAPTURE_IMAGE_ID).setVisible(haveProject);
        menu.findItem(MENU_IMPORT_VIDEO_ID).setVisible(haveProject);
        menu.findItem(MENU_IMPORT_IMAGE_ID).setVisible(haveProject);
        menu.findItem(MENU_IMPORT_AUDIO_ID).setVisible(haveProject &&
                mProject.getAudioTracks().size() == 0 && haveMediaItems);
        menu.findItem(MENU_CHANGE_ASPECT_RATIO_ID).setVisible(haveProject &&
                mProject.hasMultipleAspectRatios());
        menu.findItem(MENU_EDIT_PROJECT_NAME_ID).setVisible(haveProject);

        // Check if there is an operation pending or preview is on
        boolean enableMenu = haveProject;
        if (enableMenu && mPreviewThread != null) {
            // Preview is in progress
            enableMenu = mPreviewThread.isStopped();
            if (enableMenu && mProjectPath != null) {
                enableMenu = !ApiService.isProjectEdited(mProjectPath);
            }
        }

        menu.findItem(MENU_EXPORT_MOVIE_ID).setVisible(enableMenu && haveMediaItems);
        menu.findItem(MENU_PLAY_EXPORTED_MOVIE).setVisible(enableMenu &&
                mProject.getExportedMovieUri() != null);
        menu.findItem(MENU_SHARE_VIDEO).setVisible(enableMenu &&
                mProject.getExportedMovieUri() != null);
        menu.findItem(MENU_DELETE_PROJECT_ID).setVisible(enableMenu);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                final Intent intent = new Intent(this, ProjectsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

                finish();
                return true;
            }

            case MENU_CAPTURE_VIDEO_ID: {
                final MovieMediaItem mediaItem = mProject.getInsertAfterMediaItem(
                        mProject.getPlayheadPos());
                if (mediaItem != null) {
                    mInsertMediaItemAfterMediaItemId = mediaItem.getId();
                } else {
                    mInsertMediaItemAfterMediaItemId = null;
                }

                // Create parameters for Intent with filename
                final ContentValues values = new ContentValues();
                mCaptureMediaUri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                final Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureMediaUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                startActivityForResult(intent, REQUEST_CODE_CAPTURE_VIDEO);
                return true;
            }

            case MENU_CAPTURE_IMAGE_ID: {
                final MovieMediaItem mediaItem = mProject.getInsertAfterMediaItem(
                        mProject.getPlayheadPos());
                if (mediaItem != null) {
                    mInsertMediaItemAfterMediaItemId = mediaItem.getId();
                } else {
                    mInsertMediaItemAfterMediaItemId = null;
                }

                // Create parameters for Intent with filename
                final ContentValues values = new ContentValues();
                mCaptureMediaUri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mCaptureMediaUri);
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                startActivityForResult(intent, REQUEST_CODE_CAPTURE_IMAGE);
                return true;
            }

            case MENU_IMPORT_VIDEO_ID: {
                final MovieMediaItem mediaItem = mProject.getInsertAfterMediaItem(
                        mProject.getPlayheadPos());
                if (mediaItem != null) {
                    mInsertMediaItemAfterMediaItemId = mediaItem.getId();
                } else {
                    mInsertMediaItemAfterMediaItemId = null;
                }

                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_VIDEO);
                return true;
            }

            case MENU_IMPORT_IMAGE_ID: {
                final MovieMediaItem mediaItem = mProject.getInsertAfterMediaItem(
                        mProject.getPlayheadPos());
                if (mediaItem != null) {
                    mInsertMediaItemAfterMediaItemId = mediaItem.getId();
                } else {
                    mInsertMediaItemAfterMediaItemId = null;
                }

                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_IMAGE);
                return true;
            }

            case MENU_IMPORT_AUDIO_ID: {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_MUSIC);
                return true;
            }

            case MENU_CHANGE_ASPECT_RATIO_ID: {
                final ArrayList<Integer> aspectRatiosList = mProject.getUniqueAspectRatiosList();
                final int size = aspectRatiosList.size();
                if (size > 1) {
                    final Bundle bundle = new Bundle();
                    bundle.putIntegerArrayList(PARAM_ASPECT_RATIOS_LIST, aspectRatiosList);

                    // Get the current aspect ratio index
                    final int currentAspectRatio = mProject.getAspectRatio();
                    int currentAspectRatioIndex = 0;
                    for (int i = 0; i < size; i++) {
                        final int aspectRatio = aspectRatiosList.get(i);
                        if (aspectRatio == currentAspectRatio) {
                            currentAspectRatioIndex = i;
                            break;
                        }
                    }
                    bundle.putInt(PARAM_CURRENT_ASPECT_RATIO_INDEX, currentAspectRatioIndex);
                    showDialog(DIALOG_CHOOSE_ASPECT_RATIO_ID, bundle);
                }
                return true;
            }

            case MENU_EDIT_PROJECT_NAME_ID: {
                showDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                return true;
            }

            case MENU_DELETE_PROJECT_ID: {
                // Confirm project delete
                showDialog(DIALOG_DELETE_PROJECT_ID);
                return true;
            }

            case MENU_EXPORT_MOVIE_ID: {
                // Present the user with a dialog to choose export options
                showDialog(DIALOG_EXPORT_OPTIONS_ID);
                return true;
            }

            case MENU_PLAY_EXPORTED_MOVIE: {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(mProject.getExportedMovieUri(), "video/*");
                intent.putExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, false);
                startActivity(intent);
                return true;
            }

            case MENU_SHARE_VIDEO: {
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, mProject.getExportedMovieUri());
                intent.setType("video/*");
                startActivity(intent);
                return true;
            }

            default: {
                return false;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(int id, final Bundle bundle) {
        switch (id) {
            case DIALOG_CHOOSE_ASPECT_RATIO_ID: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.editor_change_aspect_ratio));
                final ArrayList<Integer> aspectRatios =
                    bundle.getIntegerArrayList(PARAM_ASPECT_RATIOS_LIST);
                final int count = aspectRatios.size();
                final CharSequence[] aspectRatioStrings = new CharSequence[count];
                for (int i = 0; i < count; i++) {
                    int aspectRatio = aspectRatios.get(i);
                    switch (aspectRatio) {
                        case MediaProperties.ASPECT_RATIO_11_9: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_11_9);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_16_9: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_16_9);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_3_2: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_3_2);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_4_3: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_4_3);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_5_3: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_5_3);
                            break;
                        }

                        default: {
                            break;
                        }
                    }
                }

                builder.setSingleChoiceItems(aspectRatioStrings,
                        bundle.getInt(PARAM_CURRENT_ASPECT_RATIO_INDEX),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        final int aspectRatio = aspectRatios.get(which);
                        ApiService.setAspectRatio(VideoEditorActivity.this, mProjectPath,
                                aspectRatio);

                        removeDialog(DIALOG_CHOOSE_ASPECT_RATIO_ID);
                    }
                });
                builder.setCancelable(true);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_CHOOSE_ASPECT_RATIO_ID);
                    }
                });
                return builder.create();
            }

            case DIALOG_DELETE_PROJECT_ID: {
                return AlertDialogs.createAlert(this, getString(R.string.editor_delete_project), 0,
                                getString(R.string.editor_delete_project_question),
                                    getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        ApiService.deleteProject(VideoEditorActivity.this, mProjectPath);
                        mProjectPath = null;
                        mProject = null;
                        enterDisabledState(R.string.editor_no_project);

                        removeDialog(DIALOG_DELETE_PROJECT_ID);
                        finish();
                    }
                }, getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_DELETE_PROJECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_DELETE_PROJECT_ID);
                    }
                }, true);
            }

            case DIALOG_DELETE_BAD_PROJECT_ID: {
                return AlertDialogs.createAlert(this, getString(R.string.editor_delete_project), 0,
                                getString(R.string.editor_load_error),
                                    getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        ApiService.deleteProject(VideoEditorActivity.this,
                                bundle.getString(PARAM_PROJECT_PATH));

                        removeDialog(DIALOG_DELETE_BAD_PROJECT_ID);
                        finish();
                    }
                }, getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_DELETE_BAD_PROJECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_DELETE_BAD_PROJECT_ID);
                    }
                }, true);
            }

            case DIALOG_EDIT_PROJECT_NAME_ID: {
                if (mProject == null) {
                    return null;
                }

                return AlertDialogs.createEditDialog(this,
                    getString(R.string.editor_edit_project_name),
                    mProject.getName(), getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        /*
                         * {@inheritDoc}
                         */
                        public void onClick(DialogInterface dialog, int which) {
                            final TextView tv =
                                (TextView)((AlertDialog)dialog).findViewById(R.id.text_1);
                            mProject.setProjectName(tv.getText().toString());
                            getActionBar().setTitle(tv.getText());
                            removeDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                        }
                    }, getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        /*
                         * {@inheritDoc}
                         */
                        public void onClick(DialogInterface dialog, int which) {
                            removeDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                        }
                    }, new DialogInterface.OnCancelListener() {
                        /*
                         * {@inheritDoc}
                         */
                        public void onCancel(DialogInterface dialog) {
                            removeDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                        }
                    }, InputType.TYPE_NULL, 32);
            }

            case DIALOG_EXPORT_OPTIONS_ID: {
                if (mProject == null) {
                    return null;
                }

                return ExportOptionsDialog.create(this,
                        new ExportOptionsDialog.ExportOptionsListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onExportOptions(int movieHeight, int movieBitrate) {
                        mPendingExportFilename = FileUtils.createMovieName(
                                MediaProperties.FILE_MP4);
                        ApiService.exportVideoEditor(VideoEditorActivity.this, mProjectPath,
                                mPendingExportFilename, movieHeight, movieBitrate);

                        removeDialog(DIALOG_EXPORT_OPTIONS_ID);

                        showExportProgress();
                    }
                }, new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_EXPORT_OPTIONS_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_EXPORT_OPTIONS_ID);
                    }
                }, mProject.getAspectRatio());
            }

            case DIALOG_REMOVE_MEDIA_ITEM_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_CHANGE_RENDERING_MODE_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_TRANSITION_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_OVERLAY_ID: {
                return mOverlayLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_EFFECT_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_AUDIO_TRACK_ID: {
                return mAudioTrackLayout.onCreateDialog(id, bundle);
            }

            default: {
                return null;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    public void onClickHandler(View target) {
        final long playheadPosMs = mProject.getPlayheadPos();

        switch (target.getId()) {
            case R.id.editor_play: {
                if (mProject != null && mPreviewThread != null) {
                    if (mPreviewThread.isPlaying()) {
                        mPreviewThread.stopPreviewPlayback();
                    } else if (mProject.getMediaItemCount() > 0){
                        mPreviewThread.startPreviewPlayback(mProject, playheadPosMs);
                    }
                }
                break;
            }

            case R.id.editor_rewind: {
                if (mProject != null && mPreviewThread != null) {
                    if (mPreviewThread.isPlaying()) {
                        mPreviewThread.stopPreviewPlayback();
                        movePlayhead(0);
                        mPreviewThread.startPreviewPlayback(mProject, 0);
                    } else {
                        movePlayhead(0);
                        showPreviewFrame();
                    }
                }
                break;
            }

            case R.id.editor_next: {
                if (mProject != null && mPreviewThread != null) {
                    final boolean restartPreview;
                    if (mPreviewThread.isPlaying()) {
                        mPreviewThread.stopPreviewPlayback();
                        restartPreview = true;
                    } else {
                        restartPreview = false;
                    }

                    final MovieMediaItem mediaItem = mProject.getNextMediaItem(playheadPosMs);
                    if (mediaItem != null) {
                        movePlayhead(mProject.getMediaItemBeginTime(mediaItem.getId()));
                        if (restartPreview) {
                            mPreviewThread.startPreviewPlayback(mProject,
                                    mProject.getPlayheadPos());
                        } else {
                            showPreviewFrame();
                        }
                    } else { // Move to the end of the timeline
                        movePlayhead(mProject.computeDuration());
                        showPreviewFrame();
                    }
                }
                break;
            }

            case R.id.editor_prev: {
                if (mProject != null && mPreviewThread != null) {
                    final boolean restartPreview;
                    if (mPreviewThread.isPlaying()) {
                        mPreviewThread.stopPreviewPlayback();
                        restartPreview = true;
                    } else {
                        restartPreview = false;
                    }

                    final MovieMediaItem mediaItem = mProject.getPreviousMediaItem(playheadPosMs);
                    if (mediaItem != null) {
                        movePlayhead(mProject.getMediaItemBeginTime(mediaItem.getId()));
                    } else { // Move to the beginning of the timeline
                        movePlayhead(0);
                    }

                    if (restartPreview) {
                        mPreviewThread.startPreviewPlayback(mProject, mProject.getPlayheadPos());
                    } else {
                        showPreviewFrame();
                    }
                }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent extras) {
        super.onActivityResult(requestCode, resultCode, extras);
        if (resultCode == RESULT_CANCELED) {
            switch (requestCode) {
                case REQUEST_CODE_CAPTURE_VIDEO:
                case REQUEST_CODE_CAPTURE_IMAGE: {
                    if (mCaptureMediaUri != null) {
                        getContentResolver().delete(mCaptureMediaUri, null, null);
                        mCaptureMediaUri = null;
                    }
                    break;
                }

                default: {
                    break;
                }
            }
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_CAPTURE_VIDEO: {
                if (mProject != null) {
                    ApiService.addMediaItemVideoUri(this, mProjectPath,
                            ApiService.generateId(), mInsertMediaItemAfterMediaItemId,
                            mCaptureMediaUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                            mProject.getTheme());
                    mInsertMediaItemAfterMediaItemId = null;
                    mCaptureMediaUri = null;
                } else {
                    // Add this video after the project loads
                    mAddMediaItemVideoUri = mCaptureMediaUri;
                }
                break;
            }

            case REQUEST_CODE_CAPTURE_IMAGE: {
                if (mProject != null) {
                    ApiService.addMediaItemImageUri(this, mProjectPath,
                            ApiService.generateId(), mInsertMediaItemAfterMediaItemId,
                            mCaptureMediaUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                            MediaItemUtils.getDefaultImageDuration(),
                            mProject.getTheme());
                    mInsertMediaItemAfterMediaItemId = null;
                    mCaptureMediaUri = null;
                } else {
                    // Add this image after the project loads
                    mAddMediaItemImageUri = mCaptureMediaUri;
                }
                break;
            }

            case REQUEST_CODE_IMPORT_VIDEO: {
                final Uri mediaUri = extras.getData();
                if (mProject != null) {
                    if ("media".equals(mediaUri.getAuthority())) {
                        ApiService.addMediaItemVideoUri(this, mProjectPath,
                                ApiService.generateId(), mInsertMediaItemAfterMediaItemId,
                                mediaUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                                mProject.getTheme());
                    } else {
                        // Notify the user that this item needs to be downloaded.
                        Toast.makeText(this, getString(R.string.editor_video_load),
                                Toast.LENGTH_LONG).show();
                        // When the download is complete insert it into the project.
                        ApiService.loadMediaItem(this, mProjectPath, mediaUri, "video/*");
                    }
                    mInsertMediaItemAfterMediaItemId = null;
                } else {
                    // Add this video after the project loads
                    mAddMediaItemVideoUri = mediaUri;
                }
                break;
            }

            case REQUEST_CODE_IMPORT_IMAGE: {
                final Uri mediaUri = extras.getData();
                if (mProject != null) {
                    if ("media".equals(mediaUri.getAuthority())) {
                        ApiService.addMediaItemImageUri(this, mProjectPath,
                                ApiService.generateId(), mInsertMediaItemAfterMediaItemId,
                                mediaUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                                MediaItemUtils.getDefaultImageDuration(), mProject.getTheme());
                    } else {
                        // Notify the user that this item needs to be downloaded.
                        Toast.makeText(this, getString(R.string.editor_image_load),
                                Toast.LENGTH_LONG).show();
                        // When the download is complete insert it into the project.
                        ApiService.loadMediaItem(this, mProjectPath, mediaUri, "image/*");
                    }
                    mInsertMediaItemAfterMediaItemId = null;
                } else {
                    // Add this image after the project loads
                    mAddMediaItemImageUri = mediaUri;
                }
                break;
            }

            case REQUEST_CODE_IMPORT_MUSIC: {
                final Uri data = extras.getData();
                if (mProject != null) {
                    ApiService.addAudioTrack(this, mProjectPath, ApiService.generateId(), data,
                            true);
                } else {
                    mAddAudioTrackUri = data;
                }
                break;
            }

            case REQUEST_CODE_EDIT_TRANSITION: {
                final int type = extras.getIntExtra(TransitionsActivity.PARAM_TRANSITION_TYPE, -1);
                final String afterMediaId = extras.getStringExtra(
                        TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID);
                final String transitionId = extras.getStringExtra(
                        TransitionsActivity.PARAM_TRANSITION_ID);
                final long transitionDurationMs = extras.getLongExtra(
                        TransitionsActivity.PARAM_TRANSITION_DURATION, 500);
                if (mProject != null) {
                    mMediaLayout.editTransition(afterMediaId, transitionId, type,
                            transitionDurationMs);
                } else {
                    // Add this transition after you load the project
                    mEditTransitionAfterMediaId = afterMediaId;
                    mEditTransitionId = transitionId;
                    mEditTransitionType = type;
                    mEditTransitionDurationMs = transitionDurationMs;
                }
                break;
            }

            case REQUEST_CODE_PICK_TRANSITION: {
                final int type = extras.getIntExtra(TransitionsActivity.PARAM_TRANSITION_TYPE, -1);
                final String afterMediaId = extras.getStringExtra(
                        TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID);
                final long transitionDurationMs = extras.getLongExtra(
                        TransitionsActivity.PARAM_TRANSITION_DURATION, 500);
                if (mProject != null) {
                    mMediaLayout.addTransition(afterMediaId, type, transitionDurationMs);
                } else {
                    // Add this transition after you load the project
                    mAddTransitionAfterMediaId = afterMediaId;
                    mAddTransitionType = type;
                    mAddTransitionDurationMs = transitionDurationMs;
                }
                break;
            }

            case REQUEST_CODE_PICK_OVERLAY: {
                final String mediaItemId =
                    extras.getStringExtra(OverlayTitleActivity.PARAM_MEDIA_ITEM_ID);
                final Bundle bundle =
                    extras.getBundleExtra(OverlayTitleActivity.PARAM_OVERLAY_ATTRIBUTES);
                if (mProject != null) {
                    final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
                    if (mediaItem != null) {
                        ApiService.addOverlay(this, mProject.getPath(), mediaItemId,
                                ApiService.generateId(), bundle,
                                mediaItem.getAppBoundaryBeginTime(),
                                OverlayLinearLayout.DEFAULT_TITLE_DURATION);
                        mOverlayLayout.invalidateCAB();
                    }
                } else {
                    // Add this overlay after you load the project
                    mAddOverlayMediaItemId = mediaItemId;
                    mAddOverlayUserAttributes = bundle;
                }
                break;
            }

            case REQUEST_CODE_EDIT_OVERLAY: {
                final Bundle bundle =
                    extras.getBundleExtra(OverlayTitleActivity.PARAM_OVERLAY_ATTRIBUTES);
                final String mediaItemId =
                    extras.getStringExtra(OverlayTitleActivity.PARAM_MEDIA_ITEM_ID);
                final String overlayId =
                    extras.getStringExtra(OverlayTitleActivity.PARAM_OVERLAY_ID);
                if (mProject != null) {
                    ApiService.setOverlayUserAttributes(this, mProject.getPath(), mediaItemId,
                            overlayId, bundle);
                    mOverlayLayout.invalidateCAB();
                } else {
                    // Edit this overlay after you load the project
                    mEditOverlayMediaItemId = mediaItemId;
                    mEditOverlayId = overlayId;
                    mEditOverlayUserAttributes = bundle;
                }
                break;
            }

            case REQUEST_CODE_PICK_EFFECT: {
                final String mediaItemId =
                    extras.getStringExtra(EffectsActivity.PARAM_MEDIA_ITEM_ID);
                final int type = extras.getIntExtra(EffectsActivity.PARAM_EFFECT_TYPE,
                        EffectType.EFFECT_COLOR_GRADIENT);
                final Rect startRect = extras.getParcelableExtra(EffectsActivity.PARAM_START_RECT);
                final Rect endRect = extras.getParcelableExtra(EffectsActivity.PARAM_END_RECT);
                if (mProject != null) {
                    mMediaLayout.addEffect(type, mediaItemId, startRect, endRect);
                } else {
                    // Add this effect after you load the project
                    mAddEffectMediaItemId = mediaItemId;
                    mAddEffectType = type;
                    mAddKenBurnsStartRect = startRect;
                    mAddKenBurnsEndRect = endRect;
                }
                break;
            }

            case REQUEST_CODE_EDIT_EFFECT: {
                final String mediaItemId =
                    extras.getStringExtra(EffectsActivity.PARAM_MEDIA_ITEM_ID);
                final int type = extras.getIntExtra(EffectsActivity.PARAM_EFFECT_TYPE,
                        EffectType.EFFECT_COLOR_GRADIENT);
                final Rect startRect = extras.getParcelableExtra(EffectsActivity.PARAM_START_RECT);
                final Rect endRect = extras.getParcelableExtra(EffectsActivity.PARAM_END_RECT);
                if (mProject != null) {
                    mMediaLayout.editEffect(type, mediaItemId, startRect, endRect);
                } else {
                    // Add this effect after you load the project
                    mEditEffectMediaItemId = mediaItemId;
                    mEditEffectType = type;
                    mEditKenBurnsStartRect = startRect;
                    mEditKenBurnsEndRect = endRect;
                }
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
    public void surfaceCreated(SurfaceHolder holder) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "surfaceCreated");
        }

        mPreviewThread = new PreviewThread(mSurfaceHolder);

        restartPreview();
    }

    /*
     * {@inheritDoc}
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "surfaceChanged: " + width + "x" + height);
        }

        if (mPreviewThread != null) {
            mPreviewThread.onSurfaceChanged(width, height);
        }
    }

    /*
     * {@inheritDoc}
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "surfaceDestroyed");
        }

        // Stop the preview playback if pending and quit the preview thread
        if (mPreviewThread != null) {
            mPreviewThread.stopPreviewPlayback();
            mPreviewThread.quit();
            mPreviewThread = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void enterTransitionalState(int statusStringId) {
        mEditorProjectView.setVisibility(View.GONE);
        mEditorEmptyView.setVisibility(View.VISIBLE);

        ((TextView)findViewById(R.id.empty_project_text)).setText(statusStringId);
        findViewById(R.id.empty_project_progress).setVisibility(View.VISIBLE);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void enterDisabledState(int statusStringId) {
        mEditorProjectView.setVisibility(View.GONE);
        mEditorEmptyView.setVisibility(View.VISIBLE);

        getActionBar().setTitle(R.string.app_name);

        ((TextView)findViewById(R.id.empty_project_text)).setText(statusStringId);
        findViewById(R.id.empty_project_progress).setVisibility(View.GONE);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void enterReadyState() {
        mEditorProjectView.setVisibility(View.VISIBLE);
        mEditorEmptyView.setVisibility(View.GONE);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected boolean showPreviewFrame() {
        if (mPreviewThread == null) { // The surface is not ready
            return false;
        }

        // Regenerate the preview frame
        if (mProject != null && !mPreviewThread.isPlaying() && mPendingExportFilename == null) {
            // Display the preview frame
            mPreviewThread.previewFrame(mProject, mProject.getPlayheadPos(),
                    mProject.getMediaItemCount() == 0);
        }

        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void updateTimelineDuration() {
        if (mProject == null) {
            return;
        }

        final long durationMs = mProject.computeDuration();

        // Resize the timeline according to the new timeline duration
        final int zoomWidth = mActivityWidth + timeToDimension(durationMs);
        final int childrenCount = mTimelineLayout.getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View child = mTimelineLayout.getChildAt(i);
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            lp.width = zoomWidth;
            child.setLayoutParams(lp);
        }

        mTimelineLayout.requestLayout(mLayoutCallback);

        // Since the duration has changed make sure that the playhead
        // position is valid.
        if (mProject.getPlayheadPos() > durationMs) {
            movePlayhead(durationMs);
        }

        mAudioTrackLayout.updateTimelineDuration();
    }

    /**
     * Convert the time to dimension
     * At zoom level 1: one activity width = 1200 seconds
     * At zoom level 2: one activity width = 600 seconds
     * ...
     * At zoom level 100: one activity width = 12 seconds
     *
     * At zoom level 1000: one activity width = 1.2 seconds
     *
     * @param durationMs The time
     *
     * @return The dimension
     */
    private int timeToDimension(long durationMs) {
        return (int)((mProject.getZoomLevel() * mActivityWidth * durationMs) / 1200000);
    }

    /**
     * Zoom the timeline
     *
     * @param level The zoom level
     * @param updateControl true to set the control position to match the
     *      zoom level
     */
    private int zoomTimeline(int level, boolean updateControl) {
        if (level < 1 || level > MAX_ZOOM_LEVEL) {
            return mProject.getZoomLevel();
        }

        mProject.setZoomLevel(level);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "zoomTimeline level: " + level + " -> " + timeToDimension(1000) + " pix/s");
        }

        updateTimelineDuration();

        if (updateControl) {
            mZoomControl.setProgress(level);
        }
        return level;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void movePlayhead(long timeMs) {
        if (mProject == null) {
            return;
        }

        if (setPlayhead(timeMs)) {
            // Scroll the timeline such that the specified position
            // is in the center of the screen
            mTimelineScroller.appScrollTo(timeToDimension(timeMs), true);
        }
    }

    /**
     * Set the playhead at the specified time position
     *
     * @param timeMs The time position
     *
     * @return true if the playhead was set at the specified time position
     */
    private boolean setPlayhead(long timeMs) {
        // Check if the position would change
        if (mCurrentPlayheadPosMs == timeMs) {
            return false;
        }

        // Check if the time is valid. Note that invalid values are common due
        // to overscrolling the timeline
        if (timeMs < 0) {
            return false;
        } else if (timeMs > mProject.computeDuration()) {
            return false;
        }

        mCurrentPlayheadPosMs = timeMs;

        mTimeView.setText(StringUtils.getTimestampAsString(this, timeMs));
        mProject.setPlayheadPos(timeMs);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void setAspectRatio(final int aspectRatio) {
        final FrameLayout.LayoutParams lp =
            (FrameLayout.LayoutParams)mSurfaceView.getLayoutParams();

        switch (aspectRatio) {
            case MediaProperties.ASPECT_RATIO_5_3: {
                lp.width = (lp.height * 5) / 3;
                break;
            }

            case MediaProperties.ASPECT_RATIO_4_3: {
                lp.width = (lp.height * 4) / 3;
                break;
            }

            case MediaProperties.ASPECT_RATIO_3_2: {
                lp.width = (lp.height * 3) / 2;
                break;
            }

            case MediaProperties.ASPECT_RATIO_11_9: {
                lp.width = (lp.height * 11) / 9;
                break;
            }

            case MediaProperties.ASPECT_RATIO_16_9: {
                lp.width = (lp.height * 16) / 9;
                break;
            }

            default: {
                break;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setAspectRatio: " + aspectRatio + ", size: " + lp.width + "x" + lp.height);
        }
        mSurfaceView.setLayoutParams(lp);
        mOverlayView.setLayoutParams(lp);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected MediaLinearLayout getMediaLayout() {
        return mMediaLayout;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected OverlayLinearLayout getOverlayLayout() {
        return mOverlayLayout;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected AudioTrackLinearLayout getAudioTrackLayout() {
        return mAudioTrackLayout;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onExportProgress(int progress) {
        if (mExportProgressDialog != null) {
            mExportProgressDialog.setProgress(progress);
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onExportComplete() {
        if (mExportProgressDialog != null) {
            mExportProgressDialog.dismiss();
            mExportProgressDialog = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onProjectEditStateChange(boolean projectEdited) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onProjectEditStateChange: " + projectEdited);
        }

        mPreviewPlayButton.setAlpha(projectEdited ? 100 : 255);
        mPreviewPlayButton.setEnabled(!projectEdited);
        mPreviewRewindButton.setEnabled(!projectEdited);
        mPreviewNextButton.setEnabled(!projectEdited);
        mPreviewPrevButton.setEnabled(!projectEdited);

        mMediaLayout.invalidateCAB();
        mOverlayLayout.invalidateCAB();
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void initializeFromProject(boolean updateUI) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Project was clean: " + mProject.isClean());
        }

        if (updateUI || !mProject.isClean()) {
            getActionBar().setTitle(mProject.getName());

            // Clear the media related to the previous project and
            // add the media for the current project.
            mMediaLayout.setProject(mProject);
            mOverlayLayout.setProject(mProject);
            mAudioTrackLayout.setProject(mProject);
            mPlayheadView.setProject(mProject);

            // Add the media items to the media item layout
            mMediaLayout.addMediaItems(mProject.getMediaItems());

            // Add the media items to the overlay layout
            mOverlayLayout.addMediaItems(mProject.getMediaItems());

            // Add the audio tracks to the audio tracks layout
            mAudioTrackLayout.addAudioTracks(mProject.getAudioTracks());

            setAspectRatio(mProject.getAspectRatio());
        }

        updateTimelineDuration();
        zoomTimeline(mProject.getZoomLevel(), true);

        // Set the playhead position. We need to wait for the layout to
        // complete before we can scroll to the playhead position.
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            private final long DELAY = 100;
            private final int ATTEMPTS = 20;
            private int mAttempts = ATTEMPTS;

            /*
             * {@inheritDoc}
             */
            public void run() {
                if (mAttempts == ATTEMPTS) { // Only scroll once
                    movePlayhead(mProject.getPlayheadPos());
                }

                // If the surface is not yet created (showPreviewFrame()
                // returns false) wait for a while (DELAY * ATTEMPTS).
                if (showPreviewFrame() == false && mAttempts >= 0) {
                    mAttempts--;
                    if (mAttempts >= 0) {
                        handler.postDelayed(this, DELAY);
                    }
                }
            }
        });

        if (mAddMediaItemVideoUri != null) {
            ApiService.addMediaItemVideoUri(this, mProjectPath, ApiService.generateId(),
                    mInsertMediaItemAfterMediaItemId,
                    mAddMediaItemVideoUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                    mProject.getTheme());
            mAddMediaItemVideoUri = null;
            mInsertMediaItemAfterMediaItemId = null;
        }

        if (mAddMediaItemImageUri != null) {
            ApiService.addMediaItemImageUri(this, mProjectPath, ApiService.generateId(),
                    mInsertMediaItemAfterMediaItemId,
                    mAddMediaItemVideoUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                    MediaItemUtils.getDefaultImageDuration(), mProject.getTheme());
            mAddMediaItemImageUri = null;
            mInsertMediaItemAfterMediaItemId = null;
        }

        if (mAddAudioTrackUri != null) {
            ApiService.addAudioTrack(this, mProject.getPath(), ApiService.generateId(),
                    mAddAudioTrackUri, true);
            mAddAudioTrackUri = null;
        }

        if (mAddTransitionAfterMediaId != null) {
            mMediaLayout.addTransition(mAddTransitionAfterMediaId, mAddTransitionType,
                    mAddTransitionDurationMs);
            mAddTransitionAfterMediaId = null;
        }

        if (mEditTransitionId != null) {
            mMediaLayout.editTransition(mEditTransitionAfterMediaId, mEditTransitionId,
                    mEditTransitionType, mEditTransitionDurationMs);
            mEditTransitionId = null;
            mEditTransitionAfterMediaId = null;
        }

        if (mAddOverlayMediaItemId != null) {
            ApiService.addOverlay(this, mProject.getPath(), mAddOverlayMediaItemId,
                    ApiService.generateId(), mAddOverlayUserAttributes, 0,
                    OverlayLinearLayout.DEFAULT_TITLE_DURATION);
            mAddOverlayMediaItemId = null;
            mAddOverlayUserAttributes = null;
        }

        if (mEditOverlayMediaItemId != null) {
            ApiService.setOverlayUserAttributes(this, mProject.getPath(), mEditOverlayMediaItemId,
                    mEditOverlayId, mEditOverlayUserAttributes);
            mEditOverlayMediaItemId = null;
            mEditOverlayId = null;
            mEditOverlayUserAttributes = null;
        }

        if (mAddEffectMediaItemId != null) {
            mMediaLayout.addEffect(mAddEffectType, mAddEffectMediaItemId,
                        mAddKenBurnsStartRect, mAddKenBurnsEndRect);
            mAddEffectMediaItemId = null;
        }

        if (mEditEffectMediaItemId != null) {
            mMediaLayout.editEffect(mEditEffectType, mEditEffectMediaItemId,
                    mEditKenBurnsStartRect, mEditKenBurnsEndRect);
            mEditEffectMediaItemId = null;
        }

        enterReadyState();

        if (mPendingExportFilename != null) {
            if (ApiService.isVideoEditorExportPending(mProjectPath, mPendingExportFilename)) {
                // The export is still pending
                // Display the export project dialog
                showExportProgress();
            } else {
                // The export completed while the Activity was paused
                mPendingExportFilename = null;
            }
        }

        invalidateOptionsMenu();

        restartPreview();
    }

    /**
     * Restart preview
     */
    private void restartPreview() {
        if (mRestartPreview == false) {
            return;
        }

        if (mProject == null) {
            return;
        }

        if (mPreviewThread != null) {
            mRestartPreview = false;
            mPreviewThread.startPreviewPlayback(mProject, mProject.getPlayheadPos());
        }
    }

    /**
     * Show progress during export operation
     */
    private void showExportProgress() {
        mExportProgressDialog = new ProgressDialog(this);
        mExportProgressDialog.setTitle(getString(R.string.export_dialog_export));
        mExportProgressDialog.setMessage(null);
        mExportProgressDialog.setIndeterminate(false);
        mExportProgressDialog.setCancelable(true);
        mExportProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mExportProgressDialog.setMax(100);
        mExportProgressDialog.setCanceledOnTouchOutside(false);
        mExportProgressDialog.setButton(getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                /*
                 * {@inheritDoc}
                 */
                public void onClick(DialogInterface dialog, int which) {
                    ApiService.cancelExportVideoEditor(VideoEditorActivity.this,
                            mProjectPath, mPendingExportFilename);
                    mPendingExportFilename = null;
                    mExportProgressDialog = null;
                }
            });
        mExportProgressDialog.setCanceledOnTouchOutside(true);
        mExportProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            /*
             * {@inheritDoc}
             */
            public void onCancel(DialogInterface dialog) {
                ApiService.cancelExportVideoEditor(VideoEditorActivity.this,
                        mProjectPath, mPendingExportFilename);
                mPendingExportFilename = null;
                mExportProgressDialog = null;
            }
        });
        mExportProgressDialog.show();
        mExportProgressDialog.setProgressNumberFormat("");
    }

    /**
     * The preview thread
     */
    private class PreviewThread extends Thread {
        // Preview states
        private final int PREVIEW_STATE_STOPPED = 0;
        private final int PREVIEW_STATE_STARTING = 1;
        private final int PREVIEW_STATE_STARTED = 2;
        private final int PREVIEW_STATE_STOPPING = 3;

        private final int OVERLAY_DATA_COUNT = 16;

        private final Handler mMainHandler;
        private final Queue<Runnable> mQueue;
        private final SurfaceHolder mSurfaceHolder;
        private final Queue<VideoEditor.OverlayData> mOverlayDataQueue;
        private Handler mThreadHandler;
        private int mPreviewState;
        private Bitmap mOverlayBitmap;

        private final Runnable mProcessQueueRunnable = new Runnable() {
            /*
             * {@inheritDoc}
             */
            public void run() {
                // Process whatever accumulated in the queue
                Runnable runnable;
                while ((runnable = mQueue.poll()) != null) {
                    runnable.run();
                }
            }
        };

        /**
         * Constructor
         *
         * @param surfaceHolder The surface holder
         */
        public PreviewThread(SurfaceHolder surfaceHolder) {
            mMainHandler = new Handler(Looper.getMainLooper());
            mQueue = new LinkedBlockingQueue<Runnable>();
            mSurfaceHolder = surfaceHolder;
            mPreviewState = PREVIEW_STATE_STOPPED;

            mOverlayDataQueue = new LinkedBlockingQueue<VideoEditor.OverlayData>();
            for (int i = 0; i < OVERLAY_DATA_COUNT; i++) {
                mOverlayDataQueue.add(new VideoEditor.OverlayData());
            }

            start();
        }

        /**
         * Preview the specified frame
         *
         * @param project The video editor project
         * @param timeMs The frame time
         * @param clear true to clear the output
         */
        public void previewFrame(final VideoEditorProject project, final long timeMs,
                final boolean clear) {
            if (mPreviewState == PREVIEW_STATE_STARTING || mPreviewState == PREVIEW_STATE_STARTED) {
                stopPreviewPlayback();
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Preview frame at: " + timeMs + " " + clear);
            }

            // We only need to see the last frame
            mQueue.clear();

            mQueue.add(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    if (clear) {
                        project.clearSurface(mSurfaceHolder);
                        mMainHandler.post(new Runnable() {
                            /*
                             * {@inheritDoc}
                             */
                            public void run() {
                                if (mOverlayBitmap != null) {
                                    mOverlayBitmap.eraseColor(Color.TRANSPARENT);
                                    mOverlayView.invalidate();
                                }
                            }
                        });
                    } else {
                        final VideoEditor.OverlayData overlayData;
                        try {
                            overlayData = mOverlayDataQueue.remove();
                        } catch (NoSuchElementException ex) {
                            Log.e(TAG, "Out of OverlayData elements");
                            return;
                        }

                        try {
                            if (project.renderPreviewFrame(mSurfaceHolder, timeMs, overlayData)
                                    < 0) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "Cannot render preview frame at: " + timeMs +
                                            " of " + mProject.computeDuration());
                                }

                                mOverlayDataQueue.add(overlayData);
                            } else {
                                if (overlayData.needsRendering()) {
                                    mMainHandler.post(new Runnable() {
                                        /*
                                         * {@inheritDoc}
                                         */
                                        public void run() {
                                            if (mOverlayBitmap != null) {
                                                overlayData.renderOverlay(mOverlayBitmap);
                                                mOverlayView.invalidate();
                                            } else {
                                                overlayData.release();
                                            }

                                            mOverlayDataQueue.add(overlayData);
                                        }
                                    });
                                } else {
                                    mOverlayDataQueue.add(overlayData);
                                }
                            }
                        } catch (Exception ex) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "renderPreviewFrame failed at timeMs: " + timeMs, ex);
                            }
                            mOverlayDataQueue.add(overlayData);
                        }
                    }
                }
            });

            if (mThreadHandler != null) {
                mThreadHandler.post(mProcessQueueRunnable);
            }
        }

        /**
         * Display the frame at the specified time position
         *
         * @param mediaItem The media item
         * @param timeMs The frame time
         */
        public void renderMediaItemFrame(final MovieMediaItem mediaItem, final long timeMs) {
            if (mPreviewState == PREVIEW_STATE_STARTING || mPreviewState == PREVIEW_STATE_STARTED) {
                stopPreviewPlayback();
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Render media item frame at: " + timeMs);
            }

            // We only need to see the last frame
            mQueue.clear();

            mQueue.add(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    try {
                        if (mProject.renderMediaItemFrame(mSurfaceHolder, mediaItem.getId(),
                                timeMs) < 0) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Cannot render media item frame at: " + timeMs +
                                        " of " + mediaItem.getDuration());
                            }
                        }
                    } catch (Exception ex) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Cannot render preview frame at: " + timeMs, ex);
                        }
                    }
                }
            });

            if (mThreadHandler != null) {
                mThreadHandler.post(mProcessQueueRunnable);
            }
        }

        /**
         * Start the preview playback
         *
         * @param project The video editor project
         * @param fromMs Start playing from the specified position
         */
        private void startPreviewPlayback(final VideoEditorProject project, final long fromMs) {
            if (mPreviewState != PREVIEW_STATE_STOPPED) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Preview did not start: " + mPreviewState);
                }
                return;
            }

            previewStarted(project);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Start preview at: " + fromMs);
            }

            // Clear any pending preview frames
            mQueue.clear();
            mQueue.add(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    try {
                        project.startPreview(mSurfaceHolder, fromMs, -1, false, 3,
                                new VideoEditor.PreviewProgressListener() {
                            /*
                             * {@inheritDoc}
                             */
                            public void onStart(VideoEditor videoEditor) {
                            }

                            /*
                             * {@inheritDoc}
                             */
                            public void onProgress(VideoEditor videoEditor, final long timeMs,
                                    final VideoEditor.OverlayData overlayData) {
                                mMainHandler.post(new Runnable() {
                                    /*
                                     * {@inheritDoc}
                                     */
                                    public void run() {
                                        if (overlayData != null && overlayData.needsRendering()) {
                                            if (mOverlayBitmap != null) {
                                                overlayData.renderOverlay(mOverlayBitmap);
                                                mOverlayView.invalidate();
                                            } else {
                                                overlayData.release();
                                            }
                                        }

                                        if (mPreviewState == PREVIEW_STATE_STARTED ||
                                                mPreviewState == PREVIEW_STATE_STOPPING) {
                                            movePlayhead(timeMs);
                                        }
                                    }
                                });
                            }

                            /*
                             * {@inheritDoc}
                             */
                            public void onStop(VideoEditor videoEditor) {
                                mMainHandler.post(new Runnable() {
                                    /*
                                     * {@inheritDoc}
                                     */
                                    public void run() {
                                        if (mPreviewState == PREVIEW_STATE_STARTED ||
                                                mPreviewState == PREVIEW_STATE_STOPPING) {
                                            previewStopped(false);
                                        }
                                    }
                                });
                            }
                        });

                        mMainHandler.post(new Runnable() {
                            /*
                             * {@inheritDoc}
                             */
                            public void run() {
                                mPreviewState = PREVIEW_STATE_STARTED;
                            }
                        });
                    } catch (Exception ex) {
                        // This exception may occur when trying to play frames
                        // at the end of the timeline
                        // (e.g. when fromMs == clip duration)
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Cannot start preview at: " + fromMs, ex);
                        }

                        mMainHandler.post(new Runnable() {
                            /*
                             * {@inheritDoc}
                             */
                            public void run() {
                                mPreviewState = PREVIEW_STATE_STARTED;
                                previewStopped(true);
                            }
                        });
                    }
                }
            });

            if (mThreadHandler != null) {
                mThreadHandler.post(mProcessQueueRunnable);
            }
        }

        /**
         * The preview started.
         * This method is always invoked from the UI thread.
         *
         * @param project The project
         */
        private void previewStarted(VideoEditorProject project) {
            // Change the button image back to a play icon
            mPreviewPlayButton.setImageResource(R.drawable.btn_playback_pause_selector);

            mTimelineScroller.enableUserScrolling(false);
            mMediaLayout.setPlaybackInProgress(true);
            mOverlayLayout.setPlaybackInProgress(true);
            mAudioTrackLayout.setPlaybackInProgress(true);

            mPreviewState = PREVIEW_STATE_STARTING;
        }

        /**
         * Stop previewing
         */
        private void stopPreviewPlayback() {
            switch (mPreviewState) {
                case PREVIEW_STATE_STOPPED: {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "stopPreviewPlayback: State was PREVIEW_STATE_STOPPED");
                    }
                    return;
                }

                case PREVIEW_STATE_STOPPING: {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "stopPreviewPlayback: State was PREVIEW_STATE_STOPPING");
                    }
                    return;
                }

                case PREVIEW_STATE_STARTING: {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "stopPreviewPlayback: State was PREVIEW_STATE_STARTING " +
                                "now PREVIEW_STATE_STOPPING");
                    }

                    mPreviewState = PREVIEW_STATE_STOPPING;

                    // We need to wait until the preview starts
                    mMainHandler.postDelayed(new Runnable() {
                        /*
                         * {@inheritDoc}
                         */
                        public void run() {
                            if (isFinishing() || isChangingConfigurations()) {
                                // The activity is shutting down. Force stopping now.
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "stopPreviewPlayback: Activity is shutting down");
                                }

                                mPreviewState = PREVIEW_STATE_STARTED;
                                previewStopped(true);
                            } else if (mPreviewState == PREVIEW_STATE_STARTED) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "stopPreviewPlayback: Now PREVIEW_STATE_STARTED");
                                }

                                previewStopped(false);
                            } else if (mPreviewState == PREVIEW_STATE_STOPPING) {
                                // Keep waiting
                                mMainHandler.postDelayed(this, 100);

                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "stopPreviewPlayback: Waiting for PREVIEW_STATE_STARTED");
                                }
                            } else {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "stopPreviewPlayback: PREVIEW_STATE_STOPPED while waiting");
                                }
                            }
                        }
                    }, 50);

                    break;
                }

                case PREVIEW_STATE_STARTED: {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "stopPreviewPlayback: State was PREVIEW_STATE_STARTED");
                    }

                    // We need to stop
                    previewStopped(false);
                    return;
                }

                default: {
                    throw new IllegalArgumentException("stopPreviewPlayback state: " +
                            mPreviewState);
                }
            }
        }

        /**
         * The surface size has changed
         *
         * @param width The new surface width
         * @param heightThe new surface height
         */
        private void onSurfaceChanged(int width, int height) {
            if (mOverlayBitmap != null) {
                if (mOverlayBitmap.getWidth() == width && mOverlayBitmap.getHeight() == height) {
                    // The size has not changed
                    return;
                }

                mOverlayView.setImageBitmap(null);
                mOverlayBitmap.recycle();
                mOverlayBitmap = null;
            }

            // Create the overlay bitmap
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Overlay size: " + width + " x " + height);
            }

            mOverlayBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            mOverlayView.setImageBitmap(mOverlayBitmap);
        }

        /**
         * Preview stopped. This method is always invoked from the UI thread.
         *
         * @param error true if the preview stopped due to an error
         *
         * @return The stop position
         */
        private void previewStopped(boolean error) {
            if (mProject == null) {
                Log.w(TAG, "previewStopped: project was deleted.");
                return;
            }

            if (mPreviewState != PREVIEW_STATE_STARTED) {
                throw new IllegalStateException("previewStopped in state: " + mPreviewState);
            }

            // Change the button image back to a play icon
            mPreviewPlayButton.setImageResource(R.drawable.btn_playback_play_selector);

            if (error == false) {
                // Set the playhead position at the position where the playback stopped
                final long stopTimeMs = mProject.stopPreview();
                movePlayhead(stopTimeMs);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "PREVIEW_STATE_STOPPED: " + stopTimeMs);
                }
            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "PREVIEW_STATE_STOPPED due to error");
                }
            }

            mPreviewState = PREVIEW_STATE_STOPPED;

            // The playback has stopped
            mTimelineScroller.enableUserScrolling(true);
            mMediaLayout.setPlaybackInProgress(false);
            mAudioTrackLayout.setPlaybackInProgress(false);
            mOverlayLayout.setPlaybackInProgress(false);
        }

        /**
         * @return true if preview playback is in progress
         */
        private boolean isPlaying() {
            return mPreviewState == PREVIEW_STATE_STARTING ||
                mPreviewState == PREVIEW_STATE_STARTED;
        }

        /**
         * @return true if the preview is stopped
         */
        private boolean isStopped() {
            return mPreviewState == PREVIEW_STATE_STOPPED;
        }

        /*
         * {@inheritDoc}
         */
        @Override
        public void run() {
            setPriority(MAX_PRIORITY);
            Looper.prepare();
            mThreadHandler = new Handler();

            // Ensure that the queued items are processed
            mMainHandler.post(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    // Start processing the queue of runnables
                    mThreadHandler.post(mProcessQueueRunnable);
                }
            });

            // Run the loop
            Looper.loop();
        }

        /**
         * Quit the thread
         */
        public void quit() {
            // Release the overlay bitmap
            if (mOverlayBitmap != null) {
                mOverlayView.setImageBitmap(null);
                mOverlayBitmap.recycle();
                mOverlayBitmap = null;
            }

            if (mThreadHandler != null) {
                mThreadHandler.getLooper().quit();
                try {
                    // Wait for the thread to quit. An ANR waiting to happen.
                    mThreadHandler.getLooper().getThread().join();
                } catch (InterruptedException ex) {
                }
            }

            mQueue.clear();
        }
    }
}
