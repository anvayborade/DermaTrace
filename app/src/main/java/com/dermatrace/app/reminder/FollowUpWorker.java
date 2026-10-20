package com.dermatrace.app.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.dermatrace.app.LesionDetailActivity;
import com.dermatrace.app.R;
import com.dermatrace.app.data.DermaTraceDbHelper;

public class FollowUpWorker extends Worker {
    public static final String CHANNEL_ID = "dermatrace_followups";

    public FollowUpWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long lesionId = getInputData().getLong("lesion_id", -1);
        String label = getInputData().getString("lesion_label");
        if (label == null || label.isEmpty()) label = "saved lesion";

        Context c = getApplicationContext();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "DermaTrace follow-ups", NotificationManager.IMPORTANCE_DEFAULT));
        }

        Intent open = new Intent(c, LesionDetailActivity.class);
        open.putExtra(LesionDetailActivity.EXTRA_LESION_ID, lesionId);
        PendingIntent pi = PendingIntent.getActivity(
                c, (int)(lesionId % Integer.MAX_VALUE), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("DermaTrace follow-up")
                .setContentText("Time to recheck " + label + " with a repeat photo.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "Time to recheck " + label + ". Open DermaTrace and use the previous-image overlay to take a comparable follow-up photo."))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        boolean delivered = false;
        if (androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()) {
            try {
                nm.notify((int)(1000 + lesionId % 100000), b.build());
                delivered = true;
            } catch (SecurityException ignored) {
                // permission revoked in the meantime: keep the follow-up pending
            }
        }
        // Only mark the follow-up as done when the user could actually be told about it.
        if (delivered) new DermaTraceDbHelper(c).markFollowUpComplete(lesionId);
        return Result.success();
    }
}
