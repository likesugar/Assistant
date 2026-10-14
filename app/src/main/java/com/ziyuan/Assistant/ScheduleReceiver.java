package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 日程到点通知 */
public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try { CalendarCardView.scheduleSchedules(context); } catch (Throwable ignored) {}
            try { CalendarCardView.scheduleMedsAll(context); } catch (Throwable ignored) {}
            try { MedPlanActivity.scheduleAll(context); } catch (Throwable ignored) {}
            return;
        }
        try {
            String t = intent.getStringExtra("t");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("remind", "提醒", NotificationManager.IMPORTANCE_HIGH));
            }
            Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, "remind") : new Notification.Builder(context);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
             .setContentTitle("日程提醒")
             .setContentText(t == null ? "" : t)
             .setAutoCancel(true);
            nm.notify((int) System.currentTimeMillis(), b.build());
        } catch (Throwable ignored) {}
    }
}
