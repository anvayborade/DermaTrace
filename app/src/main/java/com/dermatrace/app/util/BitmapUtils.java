package com.dermatrace.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;

public final class BitmapUtils {

    private BitmapUtils() {}

    public static Bitmap loadCorrectlyOriented(String path) throws Exception {
        // Analysis models only need a few hundred pixels; never decode a 50+ MP photo at full size.
        Bitmap bitmap = loadSampledOriented(path, 2048);
        if (bitmap == null) {
            throw new IllegalArgumentException("This image could not be read. Try another photo.");
        }
        return bitmap;
    }

    /** Decodes at most maxSide pixels on the longest edge (safe for very large files) and applies EXIF orientation. */
    public static Bitmap loadSampledOriented(String path, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / sample > maxSide || o.outHeight / sample > maxSide) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(path, o);
            if (b == null) return null;
            return applyExifOrientation(b, new ExifInterface(path));
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Small preview of a picked image (safe for huge files); applies EXIF orientation. */
    public static Bitmap loadThumbnail(Context context, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / sample > maxSide || o.outHeight / sample > maxSide) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) return null;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in != null) return applyExifOrientation(b, new ExifInterface(in));
            }
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    public static Bitmap loadCorrectlyOriented(Context context, Uri uri) throws Exception {
        ExifInterface exif;
        try (InputStream exifStream = context.getContentResolver().openInputStream(uri)) {
            if (exifStream == null) throw new IllegalArgumentException("Unable to open selected image.");
            exif = new ExifInterface(exifStream);
        }

        Bitmap bitmap;
        try (InputStream imageStream = context.getContentResolver().openInputStream(uri)) {
            if (imageStream == null) throw new IllegalArgumentException("Unable to read selected image.");
            bitmap = BitmapFactory.decodeStream(imageStream);
        }

        if (bitmap == null) throw new IllegalArgumentException("Unable to decode selected image.");
        return applyExifOrientation(bitmap, exif);
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, ExifInterface exif) {
        int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
        );

        Matrix matrix = new Matrix();

        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }

        return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
        );
    }
}
