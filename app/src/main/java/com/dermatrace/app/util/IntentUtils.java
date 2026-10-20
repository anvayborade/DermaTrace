package com.dermatrace.app.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

public final class IntentUtils {
    private IntentUtils() {}

    public static void shareFile(Context context, File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mimeType);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(i, "Share DermaTrace report"));
    }

    public static void openMapsSearch(Context context, String query) {
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        launch(context, i, "No maps app is available.");
    }

    public static void dial(Context context, String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            Toast.makeText(context, "Add a clinician phone number in Settings first.", Toast.LENGTH_SHORT).show();
            return;
        }
        launch(context, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone.trim()))), "No dialler app is available.");
    }

    public static void email(Context context, String email, String subject) {
        if (email == null || email.trim().isEmpty()) {
            Toast.makeText(context, "Add a clinician email in Settings first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(email.trim())));
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        launch(context, i, "No email app is available.");
    }

    private static void launch(Context context, Intent i, String error) {
        try {
            context.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
        }
    }
}
