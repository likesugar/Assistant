package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

/** 下载保活前台服务：有下载任务时全进程存活，误滑退出/完全退出不中断 */
public class DlKeepService extends Service {
    private static final String CH = "dl_keep";
    private final Handler h = new Handler();

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            android.app.NotificationChannel nc = new android.app.NotificationChannel(CH, "下载保活", android.app.NotificationManager.IMPORTANCE_MIN);
            nc.setShowBadge(false);
            ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(nc);
        } catch (Throwable ignored) {}
        startForeground(20001, buildNote("下载进行中…"));
        h.postDelayed(tick, 2000);
    }

    private final Runnable tick = new Runnable() { public void run() {
        boolean busy = false;
        try {
            for (DlManager.DlJob j : DlManager.jobs()) {
                if (j.active || j.paused) { busy = true; break; }
            }
        } catch (Throwable ignored) {}
        if (!busy) {
            stopForeground(true);
            stopSelf();
            return;
        }
        h.postDelayed(this, 2000);
    }};

    private Notification buildNote(String s) {
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH)
                : new Notification.Builder(this);
        b.setContentTitle("资源嗅探").setContentText(s).setSmallIcon(android.R.drawable.stat_sys_download)
         .setOngoing(true);
        return b.build();
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 完全退出（滑掉卡片）时进程由前台服务保活，下载线程继续跑
        super.onTaskRemoved(rootIntent);
    }

    public static void ensure(Context c) {
        try {
            android.content.Intent i = new android.content.Intent(c, DlKeepService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {}
    }
}
