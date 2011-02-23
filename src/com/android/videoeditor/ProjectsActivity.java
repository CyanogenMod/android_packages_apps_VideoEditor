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

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.android.videoeditor.service.ApiService;
import com.android.videoeditor.service.VideoEditorProject;
import com.android.videoeditor.service.ApiService.ApiServiceListener;
import com.android.videoeditor.util.FileUtils;
import com.android.videoeditor.widgets.ProjectsCarouselView;
import com.android.videoeditor.widgets.ProjectsCarouselViewHelper;
import com.android.videoeditor.widgets.ProjectsCarouselViewHelper.CarouselItemListener;

/**
 * This activity is used to manage projects
 */
public class ProjectsActivity extends Activity implements CarouselItemListener {
    // Request codes
    private static final int REQUEST_CODE_OPEN_PROJECT = 1;
    private static final int REQUEST_CODE_CREATE_PROJECT = 2;

    // The project path returned by the picker
    public static final String PARAM_OPEN_PROJECT_PATH = "path";
    public static final String PARAM_CREATE_PROJECT_NAME = "name";

    // Menu ids
    private static final int MENU_NEW_PROJECT_ID = 1;

    // Dialog ids
    private static final int DIALOG_NEW_PROJECT_ID = 1;
    private static final int DIALOG_REMOVE_PROJECT_ID = 2;

    // Dialog parameters
    private static final String PARAM_DIALOG_PATH_ID = "path";

    // Instance variables
    private final ServiceListener mServiceListener = new ServiceListener();
    private ProjectsCarouselView mCarouselView;
    private ProjectsCarouselViewHelper mHelper;

    /**
     * The service listener
     */
    private class ServiceListener extends ApiServiceListener {
        /*
         * {@inheritDoc}
         */
        @Override
        public void onProjectsLoaded(List<VideoEditorProject> projects, Exception exception) {
            if (exception == null) {
                mHelper.setProjects(projects);
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.carousel_container);

        mCarouselView = (ProjectsCarouselView)findViewById(R.id.carousel);
        mCarouselView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        mCarouselView.setZOrderOnTop(true);

        mHelper = new ProjectsCarouselViewHelper(this, mCarouselView, this);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        ApiService.registerListener(mServiceListener);

        mHelper.onResume();

        ApiService.loadProjects(this);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        super.onPause();
        ApiService.unregisterListener(mServiceListener);

        mHelper.onPause();
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_NEW_PROJECT_ID, Menu.NONE,
                R.string.projects_new_project).setIcon(
                        R.drawable.ic_menu_add_video).setShowAsAction(
                                MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_NEW_PROJECT_ID: {
                showDialog(DIALOG_NEW_PROJECT_ID);
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
            case DIALOG_NEW_PROJECT_ID: {
                return AlertDialogs.createEditDialog(this,
                        getString(R.string.projects_project_name),
                        getString(R.string.untitled), getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            /*
                             * {@inheritDoc}
                             */
                            public void onClick(DialogInterface dialog, int which) {
                                final TextView tv =
                                    (TextView)((AlertDialog)dialog).findViewById(R.id.text_1);
                                final String projectName = tv.getText().toString();
                                removeDialog(DIALOG_NEW_PROJECT_ID);

                                createProject(projectName);
                            }
                        }, getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            /*
                             * {@inheritDoc}
                             */
                            public void onClick(DialogInterface dialog, int which) {
                                removeDialog(DIALOG_NEW_PROJECT_ID);
                            }
                        }, new DialogInterface.OnCancelListener() {
                            /*
                             * {@inheritDoc}
                             */
                            public void onCancel(DialogInterface dialog) {
                                removeDialog(DIALOG_NEW_PROJECT_ID);
                            }
                        }, InputType.TYPE_NULL, 32);
            }

            case DIALOG_REMOVE_PROJECT_ID: {
                final String projectPath = bundle.getString(PARAM_DIALOG_PATH_ID);
                return AlertDialogs.createAlert(this,
                        getString(R.string.editor_delete_project),
                        0, getString(R.string.editor_delete_project_question),
                        getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_REMOVE_PROJECT_ID);

                        mHelper.removeProject(projectPath);
                        ApiService.deleteProject(ProjectsActivity.this, projectPath);
                    }
                }, getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_REMOVE_PROJECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_REMOVE_PROJECT_ID);
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
    public boolean onSearchRequested() {
        return false;
    }

    /*
     * {@inheritDoc}
     */
    public void onCarouselItemTap(String projectPath) {
        if (projectPath != null) {
            openProject(projectPath);
        } else {
            showDialog(DIALOG_NEW_PROJECT_ID);
        }
    }

    /*
     * {@inheritDoc}
     */
    public void onCarouselItemLongPress(final String projectPath, View anchorView) {
        // Create the popup menu
        final PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenuInflater().inflate(R.menu.project_menu, popupMenu.getMenu());
        popupMenu.show();
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            /*
             * {@inheritDoc}
             */
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_remove_project: {
                        final Bundle bundle = new Bundle();
                        bundle.putString(PARAM_DIALOG_PATH_ID, projectPath);
                        showDialog(DIALOG_REMOVE_PROJECT_ID, bundle);
                        break;
                    }

                    default: {
                        break;
                    }
                }

                return false;
            }
        });
    }

    /**
     * Create a new project
     *
     * @param projectPath The project path
     */
    private void createProject(String projectName) {
        try {
            final Intent extra = new Intent(this, VideoEditorActivity.class);
            extra.setAction(Intent.ACTION_INSERT);
            extra.putExtra(PARAM_CREATE_PROJECT_NAME, projectName);
            final String projectPath = FileUtils.createNewProjectPath(this);
            extra.putExtra(PARAM_OPEN_PROJECT_PATH, projectPath);
            startActivityForResult(extra, REQUEST_CODE_CREATE_PROJECT);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this, R.string.editor_storage_not_available, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Open the specified project
     *
     * @param projectPath The project path
     */
    private void openProject(String projectPath) {
        final Intent extra = new Intent(this, VideoEditorActivity.class);
        extra.setAction(Intent.ACTION_EDIT);
        extra.putExtra(PARAM_OPEN_PROJECT_PATH, projectPath);
        startActivityForResult(extra, REQUEST_CODE_OPEN_PROJECT);
    }
}
