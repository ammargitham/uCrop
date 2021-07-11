/*
 * Copyright (C) 2007-2008 OpenIntents.org
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

package com.yalantis.ucrop.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

/**
 * @author Peli
 * @author paulburke (ipaulpro)
 * @version 2013-12-11
 */
public class FileUtils {

    /**
     * TAG for log messages.
     */
    private static final String TAG = "FileUtils";

    private FileUtils() {}

    /**
     * Copies one file into the other with the given Uris.
     * In the event that the Uris are the same, trying to copy one file to the other
     * will cause both files to become null.
     * Simply skipping this step if the paths are identical.
     *
     * @param context The context from which to require the {@link android.content.ContentResolver}
     * @param uriFrom Represents the source file
     * @param uriTo   Represents the destination file
     */
    public static void copyFile(@NonNull Context context, @NonNull Uri uriFrom, @NonNull Uri uriTo) throws IOException {
        if (uriFrom.equals(uriTo)) {
            return;
        }

        try (InputStream isFrom = context.getContentResolver().openInputStream(uriFrom);
             OutputStream osTo = context.getContentResolver().openOutputStream(uriTo)) {

            if (isFrom instanceof FileInputStream && osTo instanceof FileOutputStream) {
                FileChannel inputChannel = ((FileInputStream) isFrom).getChannel();
                FileChannel outputChannel = ((FileOutputStream) osTo).getChannel();
                inputChannel.transferTo(0, inputChannel.size(), outputChannel);
            } else {
                throw new IllegalArgumentException("The input or output URI don't represent a file. "
                                                           + "uCrop requires then to represent files in order to work properly.");
            }
        }
    }

}
