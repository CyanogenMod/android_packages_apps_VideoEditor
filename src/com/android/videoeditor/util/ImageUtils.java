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

package com.android.videoeditor.util;

import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import com.android.videoeditor.R;
import com.android.videoeditor.service.MovieOverlay;

/**
 * Image utility methods
 */
public class ImageUtils {
    // The resize paint
    private static final Paint sResizePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    // The match aspect ratio mode for scaleImage
    public static int MATCH_SMALLER_DIMENSION = 1;
    public static int MATCH_LARGER_DIMENSION = 2;

    /**
     * It is not possible to instantiate this class
     */
    private ImageUtils() {
    }

    /**
     * Resize a bitmap to the specified width and height.
     *
     * @param filename The filename
     * @param width The thumbnail width
     * @param height The thumbnail height
     * @param match MATCH_SMALLER_DIMENSION or MATCH_LARGER_DIMMENSION
     *
     * @return The resized bitmap
     */
    public static Bitmap scaleImage(String filename, int width, int height, int match)
            throws IOException {
        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, dbo);

        final int nativeWidth = dbo.outWidth;
        final int nativeHeight = dbo.outHeight;

        final Bitmap srcBitmap;
        float bitmapWidth, bitmapHeight;
        if (nativeWidth > width || nativeHeight > height) {
            float dx = ((float)nativeWidth) / ((float)width);
            float dy = ((float)nativeHeight) / ((float)height);
            if (match == MATCH_SMALLER_DIMENSION) { // Match smaller dimension
                if (dx > dy) {
                    bitmapWidth = width;
                    bitmapHeight = nativeHeight / dx;
                } else {
                    bitmapWidth = nativeWidth / dy;
                    bitmapHeight = height;
                }
            } else { // Match larger dimension
                if (dx > dy) {
                    bitmapWidth = nativeWidth / dy;
                    bitmapHeight = height;
                } else {
                    bitmapWidth = width;
                    bitmapHeight = nativeHeight / dx;
                }
            }

            // Create the bitmap from file
            if (nativeWidth / bitmapWidth > 1) {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = nativeWidth / (int)bitmapWidth;
                srcBitmap = BitmapFactory.decodeFile(filename, options);
            } else {
                srcBitmap = BitmapFactory.decodeFile(filename);
            }
        } else {
            bitmapWidth = width;
            bitmapHeight = height;
            srcBitmap = BitmapFactory.decodeFile(filename);
        }

        if (srcBitmap == null) {
            throw new IOException("Cannot decode file: " + filename);
        }

        // Create the canvas bitmap
        final Bitmap bitmap = Bitmap.createBitmap((int)bitmapWidth, (int)bitmapHeight,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(srcBitmap, new Rect(0, 0, srcBitmap.getWidth(), srcBitmap.getHeight()),
                new Rect(0, 0, (int)bitmapWidth, (int)bitmapHeight), sResizePaint);
        // Release the source bitmap
        srcBitmap.recycle();
        return bitmap;
    }

    /**
     * Build an overlay image
     *
     * @param context The context
     * @param inputBitmap If the bitmap is provided no not create a new one
     * @param overlayType The overlay type
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     *
     * @return The bitmap
     */
    public static Bitmap buildOverlayBitmap(Context context, Bitmap inputBitmap, int overlayType,
            String title, String subTitle, int width, int height) {
        final Bitmap overlayBitmap;
        if (inputBitmap == null) {
            overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } else {
            overlayBitmap = inputBitmap;
        }

        overlayBitmap.eraseColor(Color.TRANSPARENT);
        final Canvas canvas = new Canvas(overlayBitmap);

        switch (overlayType) {
            case MovieOverlay.OVERLAY_TYPE_CENTER_1: {
                drawCenterOverlay(context, canvas, R.drawable.overlay_background_1,
                        Color.WHITE, title, subTitle, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_BOTTOM_1: {
                drawBottomOverlay(context, canvas, R.drawable.overlay_background_1,
                        Color.WHITE, title, subTitle, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_CENTER_2: {
                drawCenterOverlay(context, canvas, R.drawable.overlay_background_2,
                        Color.BLACK, title, subTitle, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_BOTTOM_2: {
                drawBottomOverlay(context, canvas, R.drawable.overlay_background_2,
                        Color.BLACK, title, subTitle, width, height);
                break;
            }

            default: {
                throw new IllegalArgumentException("Unsupported overlay type: " + overlayType);
            }
        }

        return overlayBitmap;
    }

    /**
     * Build an overlay image in the center third of the image
     *
     * @param context The context
     * @param canvas The canvas
     * @param drawableId The overlay background drawable if
     * @param textColor The text color
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     */
    private static void drawCenterOverlay(Context context, Canvas canvas, int drawableId,
            int textColor, String title, String subTitle, int width, int height) {
        final int INSET = width / 72;
        final int startHeight = (height / 3) + INSET;
        final Drawable background = context.getResources().getDrawable(drawableId);
        background.setBounds(INSET, startHeight, width - INSET,
                ((2 * height) / 3) - INSET);
        background.draw(canvas);

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(textColor);

        final int titleFontSize = height / 12;
        final int maxWidth = width - (2 * INSET) - (2 * titleFontSize);
        final int startYOffset = startHeight + (height / 6);
        if (title != null) {
            p.setTextSize(titleFontSize);
            title = StringUtils.trimText(title, p, maxWidth);
            canvas.drawText(title, (width - (2 * INSET) - p.measureText(title)) / 2,
                    startYOffset - p.descent(), p);
        }

        if (subTitle != null) {
            p.setTextSize(titleFontSize - 6);
            subTitle = StringUtils.trimText(subTitle, p, maxWidth);
            canvas.drawText(subTitle, (width - (2 * INSET) - p.measureText(subTitle)) / 2,
                    startYOffset - p.ascent(), p);
        }
    }

    /**
     * Build an overlay image in the lower third of the image
     *
     * @param context The context
     * @param canvas The canvas
     * @param drawableId The overlay background drawable if
     * @param textColor The text color
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     */
    private static void drawBottomOverlay(Context context, Canvas canvas, int drawableId,
            int textColor, String title, String subTitle, int width, int height) {
        final int INSET = width / 72;
        final int startHeight = ((2 * height) / 3) + INSET;
        final Drawable background = context.getResources().getDrawable(drawableId);
        background.setBounds(INSET, startHeight, width - INSET, height - INSET);
        background.draw(canvas);

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(textColor);

        final int titleFontSize = height / 12;
        final int maxWidth = width - (2 * INSET) - (2 * titleFontSize);
        final int startYOffset = startHeight + (height / 6);
        if (title != null) {
            p.setTextSize(titleFontSize);
            title = StringUtils.trimText(title, p, maxWidth);
            canvas.drawText(title, (width - (2 * INSET) - p.measureText(title)) / 2,
                    startYOffset - p.descent(), p);
        }

        if (subTitle != null) {
            p.setTextSize(titleFontSize - 6);
            subTitle = StringUtils.trimText(subTitle, p, maxWidth);
            canvas.drawText(subTitle, (width - (2 * INSET) - p.measureText(subTitle)) / 2,
                    startYOffset - p.ascent(), p);
        }
    }

    /**
     * Build an overlay preview image
     *
     * @param context The context
     * @param canvas The canvas
     * @param overlayType The overlay type
     * @param title The title
     * @param subTitle The subtitle
     * @param startX The start horizontal position
     * @param startY The start vertical position
     * @param width The width
     * @param height The height
     */
    public static void buildOverlayPreview(Context context, Canvas canvas, int overlayType,
            String title, String subTitle, int startX, int startY, int width, int height) {
        switch (overlayType) {
            case MovieOverlay.OVERLAY_TYPE_CENTER_1:
            case MovieOverlay.OVERLAY_TYPE_BOTTOM_1: {
                drawOverlayPreview(context, canvas, R.drawable.overlay_background_1,
                        Color.WHITE, title, subTitle, startX, startY, width, height);
                break;
            }

            case MovieOverlay.OVERLAY_TYPE_CENTER_2:
            case MovieOverlay.OVERLAY_TYPE_BOTTOM_2: {
                drawOverlayPreview(context, canvas, R.drawable.overlay_background_2,
                        Color.BLACK, title, subTitle, startX, startY, width, height);
                break;
            }

            default: {
                throw new IllegalArgumentException("Unsupported overlay type: " + overlayType);
            }
        }
    }

    /**
     * Build an overlay image in the lower third of the image
     *
     * @param context The context
     * @param canvas The canvas
     * @param drawableId The overlay background drawable if
     * @param title The title
     * @param subTitle The subtitle
     * @param width The width
     * @param height The height
     */
    private static void drawOverlayPreview(Context context, Canvas canvas, int drawableId,
            int textColor, String title, String subTitle, int startX, int startY, int width,
            int height) {
        final int INSET = 0;
        final int startHeight = startY + INSET;
        final Drawable background = context.getResources().getDrawable(drawableId);
        background.setBounds(startX + INSET, startHeight, startX + width - INSET,
                height - INSET + startY);
        background.draw(canvas);

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(textColor);

        final int titleFontSize = height / 4;
        final int maxWidth = width - (2 * INSET) - (2 * titleFontSize);
        final int startYOffset = startHeight + (height / 2);
        if (title != null) {
            p.setTextSize(titleFontSize);
            title = StringUtils.trimText(title, p, maxWidth);
            canvas.drawText(title, (width - (2 * INSET) - p.measureText(title)) / 2,
                    startYOffset - p.descent(), p);
        }

        if (subTitle != null) {
            p.setTextSize(titleFontSize - 6);
            subTitle = StringUtils.trimText(subTitle, p, maxWidth);
            canvas.drawText(subTitle, (width - (2 * INSET) - p.measureText(subTitle)) / 2,
                    startYOffset - p.ascent(), p);
        }
    }
}
