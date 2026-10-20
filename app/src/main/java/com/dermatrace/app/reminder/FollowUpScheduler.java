package com.dermatrace.app.reminder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.dermatrace.app.data.DermaTraceDbHelper;

import java.util.concurrent.TimeUnit;

public final class FollowUpScheduler {
    private FollowUpScheduler() {}

    public static void scheduleDays(Context context, long lesionId, String label, int days) {
        schedule(context, lesionId, label, Math.max(1, days), TimeUnit.DAYS);
    }

    public static void scheduleMinutes(Context context, long lesionId, String label, int minutes) {
        schedule(context, lesionId, label, Math.max(1, minutes), TimeUnit.MINUTES);
    }

    public static void cancel(Context context, long lesionId) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(lesionId));
        new DermaTraceDbHelper(context).deleteFollowUp(lesionId);
    }

    /** Confirmation text that is honest about notification permission. */
    public static String scheduledMessage(Context c, int days) {
        boolean denied = Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        boolean off = !NotificationManagerCompat.from(c).areNotificationsEnabled();
        String base = "Follow-up reminder saved for about " + days + " day(s).";
        if (denied) return base + " Allow notifications so DermaTrace can alert you.";
        if (off) return base + " Notifications are turned off for DermaTrace, so you will not be alerted until you turn them on.";
        return base;
    }

    private static void schedule(Context context, long lesionId, String label, long delay, TimeUnit unit) {
        Data data = new Data.Builder()
                .putLong("lesion_id", lesionId)
                .putString("lesion_label", label)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FollowUpWorker.class)
                .setInputData(data)
                .setInitialDelay(delay, unit)
                .addTag("dermatrace_lesion_" + lesionId)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(lesionId),
                ExistingWorkPolicy.REPLACE,
                request
        );

        long dueAt = System.currentTimeMillis() + unit.toMillis(delay);
        new DermaTraceDbHelper(context).upsertFollowUp(lesionId, dueAt);
    }

    private static String uniqueName(long lesionId) {
        return "dermatrace_followup_" + lesionId;
    }
}
