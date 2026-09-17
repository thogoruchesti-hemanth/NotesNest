package com.example.NotesNest.notifications.helper;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.NotesNest.R;
import com.example.NotesNest.activity.EditReminderActivity;
import com.example.NotesNest.activity.ReminderAlarmActivity;
import com.example.NotesNest.notifications.receivers.AlarmDismissReceiver;

/**
 * Handles creation and posting of all NotesNest notifications.
 */
public class NotificationHelper {

    /**
     * CHANNELS
     **/
    public static final String CHANNEL_ID_DEFAULT = "nn_default";
    public static final String CHANNEL_ID_REMINDERS = "nn_reminder_alarms_v4";
    public static final String CHANNEL_ID_HIGH = "nn_high";

    /**
     * Create notification channels for Android O+.
     */
    public static void createChannels(Context context) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createDefaultChannel(nm);
        createReminderChannel(nm);
        createImportantChannel(nm);
    }

    private static void createDefaultChannel(NotificationManager nm) {
        NotificationChannel defaultChannel = new NotificationChannel(
                CHANNEL_ID_DEFAULT,
                "NotesNest Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        defaultChannel.setDescription("General notifications");
        defaultChannel.enableLights(true);
        defaultChannel.setLightColor(Color.BLUE);
        nm.createNotificationChannel(defaultChannel);
    }

    private static void createReminderChannel(NotificationManager nm) {
        NotificationChannel reminderChannel = new NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Reminder Alarms",
                NotificationManager.IMPORTANCE_HIGH
        );
        reminderChannel.setDescription("High-priority alarms for reminders");
        reminderChannel.enableVibration(false);
        reminderChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        reminderChannel.enableLights(true);
        reminderChannel.setLightColor(Color.YELLOW);
        reminderChannel.setBypassDnd(true); // Attempt to bypass DND for alarms
        reminderChannel.setSound(null, null); // Explicitly remove channel sound
        nm.createNotificationChannel(reminderChannel);
    }

    private static void createImportantChannel(NotificationManager nm) {
        NotificationChannel importantChannel = new NotificationChannel(
                CHANNEL_ID_HIGH,
                "Important",
                NotificationManager.IMPORTANCE_HIGH
        );
        importantChannel.setDescription("High importance notifications");
        importantChannel.enableVibration(true);
        importantChannel.enableLights(true);
        importantChannel.setLightColor(Color.RED);
        nm.createNotificationChannel(importantChannel);
    }

    /**
     * Generic Notification Sender
     */
    @SuppressLint({"MissingPermission", "NotificationFullIntentPermission"})
    public static void postNotification(
            Context context,
            int notificationId,
            String channelId,
            String title,
            String content,
            Integer colorRes,
            Integer smallIconRes,
            int requestCode,
            String reminderId,
            android.os.Bundle extras
    ) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        createChannels(context);

        // Intent → opens EditReminderActivity
        Intent intent = new Intent(context, EditReminderActivity.class);
        intent.putExtra("reminder_id", reminderId);

        // Full Screen Intent → opens ReminderAlarmActivity
        Intent fullScreenIntent = new Intent(context, ReminderAlarmActivity.class);
        fullScreenIntent.putExtra("title", title);
        fullScreenIntent.putExtra("message", content);
        fullScreenIntent.putExtra("notificationId", notificationId);
        if (extras != null) {
            fullScreenIntent.putExtras(extras);
        }
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Dismiss Intent → specifically for the action button (Broadcast)
        Intent dismissIntent = new Intent(context, AlarmDismissReceiver.class);
        dismissIntent.putExtra("notificationId", notificationId);

        PendingIntent pendingIntent = createContentPendingIntent(context, intent, requestCode);
        PendingIntent fullScreenPendingIntent = createFullScreenPendingIntent(context, fullScreenIntent, requestCode + 1);
        PendingIntent dismissPendingIntent = createDismissPendingIntent(context, dismissIntent, requestCode + 2);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setContentIntent(pendingIntent)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setWhen(System.currentTimeMillis())
                        .setOnlyAlertOnce(false)
                        .addAction(R.drawable.ic_close, "Turn Off Alarm", dismissPendingIntent)
                        .setDefaults(NotificationCompat.DEFAULT_ALL);

        // Full Screen Intent (Android 14+ check)
        final boolean canShowFullScreen;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            canShowFullScreen = nm.canUseFullScreenIntent();
        } else {
            canShowFullScreen = true;
        }

        if (canShowFullScreen) {
            //noinspection MissingPermission,NotificationFullIntentPermission
            builder.setFullScreenIntent(fullScreenPendingIntent, true);
        } else {
            // Fallback: If full screen is not allowed, make sure the notification is prominent
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        // Title & message
        String finalTitle = (title == null || title.isEmpty()) ? "NotesNest Reminder" : title;
        builder.setContentTitle(finalTitle);
        builder.setContentText(java.util.Objects.requireNonNullElse(content, ""));
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(content));

        // Small icon
        builder.setSmallIcon(
                smallIconRes != null && smallIconRes != 0
                        ? smallIconRes
                        : R.drawable.ic_splash_logo_black
        );

        // Accent color
        if (colorRes != null && colorRes != 0) {
            builder.setColor(ContextCompat.getColor(context, colorRes));
        }

        // Notify
        nm.notify(notificationId, builder.build());
    }

    // --- Private Helpers for PendingIntents ---

    private static PendingIntent createContentPendingIntent(Context context, Intent intent, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static PendingIntent createFullScreenPendingIntent(Context context, Intent intent, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static PendingIntent createDismissPendingIntent(Context context, Intent intent, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
