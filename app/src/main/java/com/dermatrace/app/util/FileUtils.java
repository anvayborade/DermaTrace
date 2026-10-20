package com.dermatrace.app.util;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.database.Cursor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;

public final class FileUtils {
    private static final long MAX_IMPORT_BYTES = 60L * 1024 * 1024;
    private static final long MAX_IMPORT_PIXELS = 120_000_000L;
    private FileUtils() {}

    public static String copyUriToAppPictures(Context context, Uri uri) throws Exception {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DermaTrace");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create DermaTrace picture folder.");

        String ext = ".jpg";
        String name = displayName(context, uri);
        if (name != null && name.contains(".")) {
            String maybe = name.substring(name.lastIndexOf('.'));
            if (maybe.length() <= 6) ext = maybe;
        }

        File out = new File(dir, "imported_" + System.currentTimeMillis() + ext);
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) throw new IllegalArgumentException("Could not open selected image.");
                byte[] buffer = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_IMPORT_BYTES) throw new IllegalArgumentException("This file is larger than 60 MB. Choose a smaller photo.");
                    fos.write(buffer, 0, n);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(out.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                throw new IllegalArgumentException("This file is not a readable image. Choose a JPEG or PNG photo.");
            if ((long) bounds.outWidth * bounds.outHeight > MAX_IMPORT_PIXELS)
                throw new IllegalArgumentException("This image is too large (over 120 megapixels). Choose a smaller photo.");
        } catch (Exception e) {
            out.delete();               // never keep junk or oversized imports
            throw e;
        }
        return out.getAbsolutePath();
    }

    /** Deletes an image only if it lives in the app's own picture folder. */
    public static void deleteAppImage(Context context, String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File f = new File(path);
            if (root != null && f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) f.delete();
        } catch (Exception ignored) {}
    }

    public static String readTextFile(File file) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }
}
