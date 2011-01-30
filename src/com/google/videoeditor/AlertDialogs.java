/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Alert dialogs
 */
public class AlertDialogs {
    /**
     * This class cannot be instantiated
     */
    private AlertDialogs() {
    }

    /**
     * Create an alert dialog box
     *
     * @param context The context
     * @param title The title string
     * @param iconId The icon id
     * @param content The content string
     * @param positive The positive button text
     * @param positiveListener The positive listener
     * @param negative The negative button text
     * @param negativeListener The negative listener
     * @param cancelListener The cancel listener
     * @param cancelable true if cancelable
     */
    public static AlertDialog createAlert(Context context, String title, int iconId,
            String content, String positive, OnClickListener positiveListener, String negative,
            OnClickListener negativeListener, OnCancelListener cancelListener,
            boolean cancelable) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        if (iconId != 0) {
            builder.setIcon(context.getResources().getDrawable(iconId));
        }
        builder.setMessage(content);
        builder.setPositiveButton(positive, positiveListener);
        builder.setNegativeButton(negative, negativeListener);
        builder.setOnCancelListener(cancelListener);
        builder.setCancelable(cancelable);

        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    /**
     * Create a dialog with one edit text
     *
     * @param context The context
     * @param title The title of the dialog
     * @param text The text (if any)
     * @param positiveButtonText The positive button text
     * @param positiveListener The positive button listener
     * @param negativeButtonText The negative button text
     * @param negativeListener The negative button listener
     * @param cancelListener The cancel listener
     * @param inputType Input type
     * @param maxChars The maximum number of characters
     *
     * @return The created dialog
     */
    public static AlertDialog createEditDialog(Context context, String title, String text,
            String positiveButtonText, DialogInterface.OnClickListener positiveListener,
            String negativeButtonText, DialogInterface.OnClickListener negativeListener,
            DialogInterface.OnCancelListener cancelListener, int inputType, int maxChars) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final LayoutInflater vi = (LayoutInflater)context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final View myView = vi.inflate(R.layout.edit_one_dialog_view, null);
        builder.setView(myView);
        builder.setTitle(title);

        // Set the text if any
        final EditText textInput = (EditText)myView.findViewById(R.id.text_1);
        if (text != null) {
            textInput.setText(text);
            textInput.setSelection(0, text.length());
        }

        if (maxChars > 0) {
            final InputFilter[] filters = new InputFilter[1];
            filters[0] = new InputFilter.LengthFilter(maxChars);
            textInput.setFilters(filters);
        }

        if (inputType != InputType.TYPE_NULL) {
            textInput.setInputType(inputType);
        }

        // Setup the positive button listener
        builder.setPositiveButton(positiveButtonText, positiveListener);
        builder.setNegativeButton(negativeButtonText, negativeListener);
        builder.setOnCancelListener(cancelListener);
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        textInput.addTextChangedListener(new TextWatcher() {
            Button mPositiveButton;
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
                if (mPositiveButton == null) {
                    mPositiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                }
                mPositiveButton.setEnabled(s.toString().trim().length() > 0);
            }
        });

        return dialog;
    }
}
