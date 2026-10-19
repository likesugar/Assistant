package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** GKD 官方「常驻通知」: 显示运行状态及统计数据 */
public class GkdStatusService extends Service {
    private static final String CH = "gkd_status";
    private static final int ID = 37201;

    @Override
    public void onCreate() {
        super.onCreate();
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "GKD 状态", android.app.NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
        startForeground(ID, build());
    }

    private Notification build() {
        int[] st = GkdStore.stats(this);
        boolean a11y = GkdAccessibilityService.running;
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("GKD " + (a11y ? "运行中" : "未开启无障碍"))
                .setContentText(st[0] + "全局/" + st[1] + "应用/" + st[2] + "规则/" + st[3] + "触发")
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        return b.build();
    }

    public static void refresh(android.content.Context c) {
        if (!c.getSharedPreferences("gkd", 0).getBoolean("gkd_status_notify", false)) return;
        try { c.startForegroundService(new Intent(c, GkdStatusService.class)); } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(ID, build());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
